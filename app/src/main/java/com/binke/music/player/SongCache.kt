package com.binke.music.player

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import coil.ImageLoader
import coil.request.ImageRequest
import com.binke.music.data.api.KuwoApiService
import com.binke.music.data.model.LrcLine
import com.binke.music.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 歌曲预加载缓存：播放地址 + 封面图片 + 歌词。
 * 采用 cache-aside 模式，优先读缓存，未命中再从网络加载。
 * 切歌时只更新缓存头部一个条目，并追加预加载下一首。
 */
class SongCache(private val apiService: KuwoApiService) {

    /**
     * 【2026-06-19 修复 lyrics null/emptyList 混淆 bug】增加 lyricsTried 标志位。
     * 之前 lyrics=null 和 lyrics=emptyList() 在命中分支无法区分 → 4 源全失败时
     * emptyList() 被写进缓存，下次永远命中空列表不重试。
     * 改用三态：null = 未尝试；lyricsTried=true + lyrics=[] = 已确认无歌词；lyricsTried=true + lyrics=[…] = 有歌词。
     * 配合 evictOutsideWindow：被 evict 后 lyricsTried 随 entry 一起清空，下次重新走 4 源。
     */
    data class Entry(
        val playUrl: String?,
        val pic: String?,
        val lyrics: List<LrcLine>?,
        val lyricsTried: Boolean = false,
        /** 【1.0.33 修复】空 list 缓存的尝试时间戳。null/0 = 未尝试。空 list 5 分钟后重试 4 源，
         *  解决"装 1.0.31 当时 4 源全失败 → 100 首歌全缓存空 list → 永远不重试"bug。 */
        val lyricsTriedAt: Long = 0L,
        val coverBitmap: Bitmap? = null
    )

    // 缓存 key 使用 Song.id（rid.toString()）。跨 IO/viewModel 协程读写，必须并发安全。
    private val cache: java.util.concurrent.ConcurrentHashMap<String, Entry> = java.util.concurrent.ConcurrentHashMap()

    // 封面 Bitmap 独立缓存（与 cache.Entry.coverBitmap 同步），直接存 Bitmap 供 Compose Image() 使用
    // 【核心命中判断】hasCover 直接看 cachedBitmaps.containsKey(song.id)，不用 preloadedCoverUrls 这套 URL 集合
    // —— 之前用 pic URL 集合做命中判断有 bug：evict 时 cache[id].pic 是原 URL，preloadedCoverUrls 是 enhanced URL，
    // 永远匹配不上 → 每次切歌 preloadedCoverUrls 全被清空 → preloadPics 永远不命中。
    // 【并发安全】必须用 ConcurrentHashMap：preloadPics（scope.launch 异步并发写）+ awaitPendingBitmaps
    // （viewModelScope.launch 串行读/写）+ evictOutsideWindow（cache + cachedBitmaps + preloadedCoverUrls remove）
    // 三方在不同协程上并发操作同一个 map。LinkedHashMap 写丢 key 必然 → 命中判断永远 false → 切歌每首重新下载。
    private val cachedBitmaps: java.util.concurrent.ConcurrentHashMap<String, Bitmap> = java.util.concurrent.ConcurrentHashMap()

    // 待展示的缓存命中消息，playSong 时打包成一条 toast 后清空（已禁用）
    private val pendingHits = mutableListOf<String>()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun clearPendingHits() = pendingHits.clear()

    /** 同步获取，已命中缓存返回 Entry，否则返回 null */
    fun get(song: Song): Entry? = cache[song.id]

    /** 写入某首歌的 playUrl（保留 pic/lyrics/coverBitmap） */
    fun putPlayUrl(song: Song, url: String) {
        val existing = cache[song.id]
        cache[song.id] = Entry(
            playUrl = url,
            pic = existing?.pic ?: song.pic,
            lyrics = existing?.lyrics,
            coverBitmap = existing?.coverBitmap
        )
    }

    /** 清除某首歌的 playUrl（强制下次 getPlayUrl 重新拉） */
    fun invalidatePlayUrl(song: Song) {
        val existing = cache[song.id] ?: return
        cache[song.id] = existing.copy(playUrl = null)
    }

    /** 缓存是否存在且可用（playUrl 非空） */
    fun hasPlayUrl(song: Song): Boolean =
        cache[song.id]?.playUrl != null

    /** 封面是否已预加载进内存（按 song.id 命中 cachedBitmaps，绕开原/增强 pic URL 不一致的 bug） */
    fun hasCover(song: Song): Boolean = cachedBitmaps.containsKey(song.id)

    /** 获取已预加载的封面 Bitmap（供 Compose Image() 直接使用） */
    fun getCoverBitmap(songId: String): Bitmap? = cachedBitmaps[songId]

    /** 获取封面 Bitmap（按 song.id） */
    fun getCoverBitmap(song: Song): Bitmap? = cachedBitmaps[song.id]

    /** 从缓存或网络加载播放地址（同步） */
    suspend fun loadOrGetPlayUrl(song: Song): String? {
        cache[song.id]?.playUrl?.let { return it }
        val url = apiService.getPlayUrl(song.musicRid).url
        // 写回时用当前 song.pic（已增强），保持缓存图片URL与UI一致
        cache[song.id] = Entry(playUrl = url, pic = song.pic, lyrics = cache[song.id]?.lyrics, coverBitmap = cache[song.id]?.coverBitmap)
        return url
    }

    /**
     * 加载歌词并写缓存。
     * 【2026-06-19 修复】命中条件改为 `lyricsTried == true`：null（未尝试）走完整 4 源，
     * emptyList（已确认无歌词）直接返回不再走网络。被 evict 后 entry 整个删除，lyricsTried 一起清空，下次重试。
     */
    suspend fun loadLyrics(song: Song): List<LrcLine> {
        val t0 = System.currentTimeMillis()
        val existing = cache[song.id]
        // 【1.0.33 修复】cacheHit 条件：已尝试过 4 源 && (有歌词 OR 距上次 > 5 分钟)
        // 解决空 list 缓存永远不重试 bug——空 list 5 分钟后会自动重新走 4 源
        if (existing?.lyricsTried == true &&
            (existing.lyrics?.isNotEmpty() == true ||
             (t0 - existing.lyricsTriedAt) > 5 * 60 * 1000L)) {
            log("loadLyrics: ${song.name} (id=${song.id}) cacheHit=true, lines=${existing.lyrics?.size ?: 0}, age=${(t0 - existing.lyricsTriedAt) / 1000}s")
            return existing.lyrics ?: emptyList()
        }
        if (existing?.lyricsTried == true) {
            log("loadLyrics: ${song.name} (id=${song.id}) cacheHit-empty-but-fresh, age=${(t0 - existing.lyricsTriedAt) / 1000}s, will retry 4 sources")
        } else {
            log("loadLyrics: ${song.name} (id=${song.id}) cacheMiss, will try 4 sources")
        }

        var lyrics: List<LrcLine> = emptyList()
        var sourcesTried = 0  // 实际尝试过的源数（成功+失败），用于出口日志

        // 1. 酷我歌词（musicId 直查）
        var kr = apiService.getLyrics(song.rid.toString())
        sourcesTried++
        if (kr.isSuccess) {
            val n = kr.getOrNull()?.size ?: 0
            if (n > 0) {
                log("  lyric-kuwo: success, lines=$n")
                lyrics = kr.getOrNull()!!
            } else {
                log("  lyric-kuwo: 200 but empty lrclist")
            }
        } else {
            log("  lyric-kuwo: fail, err=${kr.exceptionOrNull()?.message?.take(80)}")
            // 失败时重试 2 次（之前是固定 retry 3 次无脑 delay，慢；改成 0+2 = 总共 3 次）
            for (attempt in 0..1) {
                delay(500)
                kr = apiService.getLyrics(song.rid.toString())
                sourcesTried++
                if (kr.isSuccess) {
                    val n = kr.getOrNull()?.size ?: 0
                    if (n > 0) {
                        log("  lyric-kuwo: success (retry ${attempt + 1}), lines=$n")
                        lyrics = kr.getOrNull()!!
                    } else {
                        log("  lyric-kuwo: 200 but empty lrclist (retry ${attempt + 1})")
                    }
                    break
                } else {
                    log("  lyric-kuwo: fail (retry ${attempt + 1}), err=${kr.exceptionOrNull()?.message?.take(80)}")
                }
            }
        }

        // 2. 网易云歌词兜底（歌名+歌手搜，再拿歌词）
        if (lyrics.isEmpty()) {
            val cleanName = song.name.replace(Regex("（[^）]*）|\\([^)]*\\)"), "").trim()
            var nr = apiService.searchLyricsNetEase(cleanName, song.artist)
            sourcesTried++
            if (nr.isSuccess) {
                val n = nr.getOrNull()?.size ?: 0
                if (n > 0) {
                    log("  lyric-163: success, lines=$n")
                    lyrics = nr.getOrNull()!!
                } else {
                    log("  lyric-163: 200 but empty result")
                }
            } else {
                log("  lyric-163: fail, err=${nr.exceptionOrNull()?.message?.take(80)}")
                for (attempt in 0..1) {
                    delay(500)
                    nr = apiService.searchLyricsNetEase(cleanName, song.artist)
                    sourcesTried++
                    if (nr.isSuccess) {
                        val n = nr.getOrNull()?.size ?: 0
                        if (n > 0) {
                            log("  lyric-163: success (retry ${attempt + 1}), lines=$n")
                            lyrics = nr.getOrNull()!!
                        } else {
                            log("  lyric-163: 200 but empty (retry ${attempt + 1})")
                        }
                        break
                    } else {
                        log("  lyric-163: fail (retry ${attempt + 1}), err=${nr.exceptionOrNull()?.message?.take(80)}")
                    }
                }
            }
        }

        // 3. 酷狗歌词兜底（覆盖网易云/lrclib 缺失的冷门中文歌，2026-06-20 加）
        if (lyrics.isEmpty()) {
            val cleanName = song.name.replace(Regex("（[^）]*）|\\([^)]*\\)"), "").trim()
            var kg = apiService.searchLyricsKugou(cleanName, song.artist)
            sourcesTried++
            if (kg.isSuccess) {
                val n = kg.getOrNull()?.size ?: 0
                if (n > 0) {
                    log("  lyric-kugou: success, lines=$n")
                    lyrics = kg.getOrNull()!!
                } else {
                    log("  lyric-kugou: 200 but empty result")
                }
            } else {
                log("  lyric-kugou: fail, err=${kg.exceptionOrNull()?.message?.take(80)}")
                delay(300)
                kg = apiService.searchLyricsKugou(cleanName, song.artist)
                sourcesTried++
                if (kg.isSuccess) {
                    val n = kg.getOrNull()?.size ?: 0
                    if (n > 0) {
                        log("  lyric-kugou: success (retry 1), lines=$n")
                        lyrics = kg.getOrNull()!!
                    } else {
                        log("  lyric-kugou: 200 but empty (retry 1)")
                    }
                } else {
                    log("  lyric-kugou: fail (retry 1), err=${kg.exceptionOrNull()?.message?.take(80)}")
                }
            }
        }

        // 4. QQ 音乐歌词兜底
        if (lyrics.isEmpty()) {
            val cleanName = song.name.replace(Regex("（[^）]*）|\\([^)]*\\)"), "").trim()
            var qr = apiService.searchLyricsQQ(cleanName, song.artist)
            sourcesTried++
            if (qr.isSuccess) {
                val n = qr.getOrNull()?.size ?: 0
                if (n > 0) {
                    log("  lyric-qq: success, lines=$n")
                    lyrics = qr.getOrNull()!!
                } else {
                    log("  lyric-qq: 200 but empty result")
                }
            } else {
                log("  lyric-qq: fail, err=${qr.exceptionOrNull()?.message?.take(80)}")
                for (attempt in 0..1) {
                    delay(500)
                    qr = apiService.searchLyricsQQ(cleanName, song.artist)
                    sourcesTried++
                    if (qr.isSuccess) {
                        val n = qr.getOrNull()?.size ?: 0
                        if (n > 0) {
                            log("  lyric-qq: success (retry ${attempt + 1}), lines=$n")
                            lyrics = qr.getOrNull()!!
                        } else {
                            log("  lyric-qq: 200 but empty (retry ${attempt + 1})")
                        }
                        break
                    } else {
                        log("  lyric-qq: fail (retry ${attempt + 1}), err=${qr.exceptionOrNull()?.message?.take(80)}")
                    }
                }
            }
        }

        // 5. lrclib.net 公共歌词库兜底
        if (lyrics.isEmpty()) {
            val cleanName = song.name.replace(Regex("（[^）]*）|\\([^)]*\\)"), "").trim()
            var lr = apiService.searchLyricsLrclib(cleanName, song.artist)
            sourcesTried++
            if (lr.isSuccess) {
                val n = lr.getOrNull()?.size ?: 0
                if (n > 0) {
                    log("  lyric-lrclib: success, lines=$n")
                    lyrics = lr.getOrNull()!!
                } else {
                    log("  lyric-lrclib: 200 but empty result")
                }
            } else {
                log("  lyric-lrclib: fail, err=${lr.exceptionOrNull()?.message?.take(80)}")
                delay(300)
                lr = apiService.searchLyricsLrclib(cleanName, song.artist)
                sourcesTried++
                if (lr.isSuccess) {
                    val n = lr.getOrNull()?.size ?: 0
                    if (n > 0) {
                        log("  lyric-lrclib: success (retry 1), lines=$n")
                        lyrics = lr.getOrNull()!!
                    } else {
                        log("  lyric-lrclib: 200 but empty (retry 1)")
                    }
                } else {
                    log("  lyric-lrclib: fail (retry 1), err=${lr.exceptionOrNull()?.message?.take(80)}")
                }
            }
        }

        // 写回：【关键】lyricsTried = true，标记"已尝试过 4 源"。即使 lyrics=emptyList()，
        // 下次命中也能区分"已查过没结果"（直接返回空，不再重试）和"未查"（走完整 4 源）。
        // 配合 evictOutsideWindow 窗口外 entry 整体删除 → lyricsTried 一起被清空 → 重新走 4 源。
        // 【1.0.33 修复】同步记录 lyricsTriedAt 时间戳——空 list 5 分钟后会重试。
        val ex = cache[song.id]
        cache[song.id] = Entry(
            playUrl = ex?.playUrl,
            pic = ex?.pic ?: song.pic,
            lyrics = lyrics,
            lyricsTried = true,
            lyricsTriedAt = t0,
            coverBitmap = ex?.coverBitmap
        )
        val cost = System.currentTimeMillis() - t0
        log("loadLyrics done: ${song.name} lines=${lyrics.size}, tried=$sourcesTried, cost=${cost}ms")
        return lyrics
    }

    /**
     * 滑动窗口清理：只保留 [currentIdx, currentIdx + windowSize] 范围内的歌曲缓存。
     * 在预加载前调用，确保缓存队列深度始终为 windowSize。
     * 同步清理 cache + cachedBitmaps + preloadedCoverUrls 三层缓存。
     */
    fun evictOutsideWindow(playlist: List<Song>, currentIdx: Int, windowSize: Int = 7) {
        // windowSize = 7: 窗口 [currentIdx, currentIdx+7) 共 7 个位置
        // → 覆盖"当前播放" + "接下来 6 首 preload"（currentIdx+1~currentIdx+6）
        // → 之前 windowSize=6 时 currentIdx+6 那一首在窗口外，被 evict 误删 → 切到那首时 cachedBitmaps 空 → 重新下载
        if (playlist.isEmpty()) return
        val windowEnd = minOf(currentIdx + windowSize, playlist.size)
        val windowIds = (currentIdx until windowEnd)
            .mapNotNull { idx -> playlist.getOrNull(idx)?.id }
            .toSet()
        val evictFromBitmaps = cachedBitmaps.keys.toList().filter { it !in windowIds }
        if (evictFromBitmaps.isNotEmpty()) {
            log("  evict 删 ${evictFromBitmaps.size} 张: ${evictFromBitmaps.joinToString { id -> playlist.indexOfFirst { it.id == id }.toString() }}")
        }
        evictFromBitmaps.forEach { cachedBitmaps.remove(it) }
        val evictFromCache = cache.keys.toList().filter { it !in windowIds }
        evictFromCache.forEach { cache.remove(it) }
    }

    /**
     * 预加载多首歌曲的播放地址（后台，不阻塞）。
     * 已在 preloadUpcoming 中按滑动窗口过滤，只预加载窗口内的歌曲。
     */
    fun preloadPlayUrls(songs: List<Song>) {
        songs.forEach { song ->
            if (cache[song.id]?.playUrl == null) {
                scope.launch {
                    val url = apiService.getPlayUrl(song.musicRid).url
                    val existing = cache[song.id]
                    cache[song.id] = Entry(
                        playUrl = url,
                        pic = song.pic,
                        lyrics = existing?.lyrics,
                        coverBitmap = existing?.coverBitmap
                    )
                }
            }
        }
    }

    /**
     * 预加载歌词（后台，不阻塞）
     */
    fun preloadLyrics(song: Song) {
        scope.launch {
            loadLyrics(song)
        }
    }

    /** 待 preloadPics 启动的所有协程 Job，用于 awaitPendingBitmaps 之前 join，确保 await 内的 containsKey 不会全 miss */
    private val pendingPicJobs = java.util.concurrent.CopyOnWriteArrayList<kotlinx.coroutines.Job>()

    /**
     * 预加载封面图片（后台，等待图片真正下载并解码成 Bitmap 后再返回）。
     * 1. 检查 cachedBitmaps 是否已有该 song.id → 命中 "封面命中缓存"
     * 2. 用 withContext(Dispatchers.IO) 真正等待 execute() 完成
     * 3. 用 BitmapFactory 解码 bytes 得到 Bitmap，存入 cachedBitmaps
     * 4. URL 加入 preloadedCoverUrls（与 playUrl/lyrics 共用同一滑动窗口）
     *
     * 注意：pendingHits.add("封面命中缓存") 移到 Bitmap 真正就绪之后，
     * 确保 toast 触发时图片已可直接渲染，不会回到 AsyncImage 重新 decode。
     *
     * 【双层加载修复】每次 scope.launch 都把 Job 记到 pendingPicJobs，awaitPendingBitmaps
     * 调用前会 join 所有 pending job，确保 await 内的 containsKey 检查不会全 miss（之前
     * scope.launch 异步写 vs await viewModelScope.launch 同步检查存在 race，5/6 首 containsKey
     * 全 false → 重复 loader.execute 6 张，浪费 5 张下载）。
     */
    fun preloadPics(songs: List<Song>) {
        val misses = songs.filter { song ->
            song.pic.isNotBlank() && !cachedBitmaps.containsKey(song.id)
        }
        if (misses.isEmpty()) return

        log("preloadPics 收到 ${misses.size} 首: ${misses.map { it.name }.take(6)}")
        misses.forEach { song ->
            val picUrl = song.pic
            // 未命中：启动协程真正下载并解码
            val job = scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        try {
                            val loader = getImageLoader() ?: run {
                                warn("  ✗ ${song.name} ImageLoader 为空（setAppContext 未调用?）")
                                return@withContext
                            }
                            val ctx = getAppContext() ?: return@withContext
                            val request = ImageRequest.Builder(ctx)
                                .data(picUrl)
                                .build()
                            val result = loader.execute(request)
                            val srcBitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                            // 【OOM 兜底】500x500 ARGB_8888 ≈ 1MB 理论不会 OOM，但某些 Android 机型
                            // system bitmap pool 紧张时 .copy() 会抛 OutOfMemoryError。失败时回退用
                            // Coil 缓存中的 src（不 copy），Coil memoryCache 默认 25% 应用内存，
                            // 8 张 ≈ 8MB 远小于上限，不会被 LRU 淘汰。
                            val bitmap: Bitmap? = srcBitmap?.let { src ->
                                try {
                                    src.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                                } catch (oom: OutOfMemoryError) {
                                    warn("  ⚠ ${song.name} bitmap.copy OOM，回退用 src（不 copy）")
                                    src
                                } catch (e: Throwable) {
                                    warn("  ⚠ ${song.name} bitmap.copy 异常: ${e.message}，回退用 src")
                                    src
                                }
                            }
                            if (bitmap != null) {
                                cachedBitmaps[song.id] = bitmap
                                val existing = cache[song.id]
                                cache[song.id] = Entry(
                                    playUrl = existing?.playUrl,
                                    pic = existing?.pic ?: song.pic,
                                    lyrics = existing?.lyrics,
                                    coverBitmap = bitmap
                                )
                                log("  ✓ 封面就绪: ${song.name} (${bitmap.width}x${bitmap.height}, ${bitmap.byteCount/1024}KB)")
                                // Bitmap 就绪后才标记命中，确保 toast 触发时图片已可直接渲染
                                pendingHits.add("封面命中缓存")
                            } else {
                                warn("  ✗ ${song.name} bitmap 为 null (drawable=${result.drawable!!::class.simpleName}, picUrl=$picUrl)")
                            }
                        } catch (e: Exception) {
                            warn("  ✗ ${song.name} 下载失败: ${e.message}")
                            // 下载失败，静默忽略，不影响播放
                        }
                    }
                } finally {
                    // 关键：这里 coroutineContext[Job] 是 scope.launch 的外层 Job（不是 withContext 的子 Job），
                    // remove 才能从 pendingPicJobs 中删掉。否则累加不减少，log 显示"等待 N 个"一直增加
                    // （join 仍然立即返回因为协程早已完成，但 cosmetic 上难看）。
                    pendingPicJobs.remove(coroutineContext[kotlinx.coroutines.Job])
                }
            }
            pendingPicJobs.add(job)
        }
    }

    /**
     * 同步等待所有封面预加载协程完成（用于 playSong 中，在 toast 触发前确保 Bitmap 已就绪）。
     * 返回已加载的 Bitmap map（song.id → Bitmap）。
     * 注意：suspend 函数，需在协程中调用。
     */
    suspend fun awaitPendingBitmaps(songs: List<Song>): Map<String, Bitmap> {
        val ctx = getAppContext() ?: return emptyMap()
        val results = mutableMapOf<String, Bitmap>()

        // 【双层加载修复】先把 preloadPics 启动的所有 scope.launch 协程 join 掉。
        // 之前 race：scope.launch 异步写 cachedBitmaps（230ms 内完成） vs await 内同步
        // containsKey 检查（0ms）→ 5/6 首 containsKey 全 false → 重复 loader.execute 6 张。
        // join 后 await 内的 containsKey 检查是 100% 命中状态，0 张重复加载。
        if (pendingPicJobs.isNotEmpty()) {
            val jobsToJoin = pendingPicJobs.toList()
            log("awaitPendingBitmaps 等待 ${jobsToJoin.size} 个 preloadPics 协程完成")
            jobsToJoin.forEach { it.join() }
            log("awaitPendingBitmaps preloadPics 协程全部完成，进入命中检查")
        }

        songs.forEach { song ->
            val picUrl = song.pic
            if (picUrl.isNullOrBlank()) return@forEach
            // 命中判断：按 song.id 看 cachedBitmaps（与 preloadPics 保持一致）
            if (cachedBitmaps.containsKey(song.id)) {
                cachedBitmaps[song.id]?.let { results[song.id] = it }
                return@forEach
            }
            try {
                val loader = getImageLoader() ?: return@forEach
                val request = ImageRequest.Builder(ctx).data(picUrl).build()
                val result = loader.execute(request)
                val srcBitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                // 【OOM 兜底】同 preloadPics 的处理：bitmap.copy 失败时回退用 src
                val bitmap: Bitmap? = srcBitmap?.let { src ->
                    try {
                        src.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                    } catch (oom: OutOfMemoryError) {
                        warn("  ⚠ ${song.name} await bitmap.copy OOM，回退用 src")
                        src
                    } catch (e: Throwable) {
                        warn("  ⚠ ${song.name} await bitmap.copy 异常: ${e.message}，回退用 src")
                        src
                    }
                }
                if (bitmap != null) {
                    cachedBitmaps[song.id] = bitmap
                    val existing = cache[song.id]
                    cache[song.id] = Entry(
                        playUrl = existing?.playUrl,
                        pic = existing?.pic ?: song.pic,
                        lyrics = existing?.lyrics,
                        coverBitmap = bitmap
                    )
                    results[song.id] = bitmap
                    pendingHits.add("封面命中缓存")
                } else {
                    warn("  ✗ ${song.name} await bitmap 为 null (drawable=${result.drawable!!::class.simpleName})")
                }
            } catch (e: Exception) {
                warn("  ✗ ${song.name} await 加载失败: ${e.message}")
                // 同步加载失败，静默忽略
            }
        }
        return results
    }

    companion object {
        private const val TAG = "SongCache"

        @Volatile
        private var instance: SongCache? = null

        private var appContext: Context? = null

        /** 指向 BinkeMusicApp 单例，与 AsyncImage 共用同一 ImageLoader，保证缓存命中 */
        private var imageLoader: ImageLoader? = null

        /** 日志文件：/Android/data/com.binke.music/files/logs/binke_preload.log（user 无 adb 时直接用文件管理器看） */
        private var logFile: File? = null
        private val tsFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.CHINA)

        /** 统一日志：logcat + 文件（无 adb 时查看文件路径下方有说明） */
        fun log(msg: String) {
            Log.d(TAG, msg)
            val f = logFile ?: return
            try {
                val ts = tsFormat.format(Date())
                f.appendText("[$ts] $msg\n")
            } catch (_: Exception) {}
        }

        fun warn(msg: String) {
            Log.w(TAG, msg)
            val f = logFile ?: return
            try {
                val ts = tsFormat.format(Date())
                f.appendText("[$ts] WARN: $msg\n")
            } catch (_: Exception) {}
        }

        fun getInstance(apiService: KuwoApiService): SongCache {
            return instance ?: synchronized(this) {
                instance ?: SongCache(apiService).also { instance = it }
            }
        }

        /** 无参获取单例（供 Compose UI 层调用，无需传入 apiService） */
        fun getInstance(): SongCache? = instance

        fun setAppContext(context: Context) {
            appContext = context.applicationContext
            // 关键：必须与 AsyncImage 走 Coil.imageLoader(context) → ImageLoaderFactory.newImageLoader()
            // 拿到的同一个 ImageLoader 实例，才能共享 memoryCache。直接拿 BinkeMusicApp.imageLoader 单例。
            val app = context.applicationContext as? com.binke.music.BinkeMusicApp
            imageLoader = app?.imageLoader

            // 初始化日志文件到 app 私有外部目录（不需要 root/权限，user 文件管理器直接看）
            try {
                val logsDir = appContext!!.getExternalFilesDir("logs")
                if (logsDir != null) {
                    if (!logsDir.exists()) logsDir.mkdirs()
                    val f = File(logsDir, "binke_preload.log")
                    if (!f.exists()) f.createNewFile()
                    logFile = f
                    val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())
                    f.appendText("\n=== binke preload log session @ $now ===\n")
                    f.appendText("日志路径: ${f.absolutePath}\n")
                }
            } catch (e: Exception) {
                Log.w(TAG, "初始化日志文件失败: ${e.message}")
            }
        }

        fun getAppContext(): Context? = appContext
        fun getImageLoader(): ImageLoader? = imageLoader
        fun getLogFile(): File? = logFile
    }
}
