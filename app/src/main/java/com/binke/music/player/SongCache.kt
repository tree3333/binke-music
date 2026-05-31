package com.binke.music.player

import android.content.Context
import android.graphics.Bitmap
import coil.ImageLoader
import coil.request.CachePolicy
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

/**
 * 歌曲预加载缓存：播放地址 + 封面图片 + 歌词。
 * 采用 cache-aside 模式，优先读缓存，未命中再从网络加载。
 * 切歌时只更新缓存头部一个条目，并追加预加载下一首。
 */
class SongCache(private val apiService: KuwoApiService) {

    data class Entry(
        val playUrl: String?,
        val pic: String?,
        val lyrics: List<LrcLine>?,
        val coverBitmap: Bitmap? = null
    )

    // 缓存 key 使用 Song.id（rid.toString()）
    private val cache = mutableMapOf<String, Entry>()

    // 封面 Bitmap 独立缓存（与 cache.Entry.coverBitmap 同步），直接存 Bitmap 供 Compose Image() 使用
    private val cachedBitmaps = mutableMapOf<String, Bitmap>()

    // 记录已预加载封面的 URL 集合（用于统一判断命中）
    private val preloadedCoverUrls = mutableSetOf<String>()

    // 待展示的缓存命中消息，playSong 时打包成一条 toast 后清空（已禁用）
    private val pendingHits = mutableListOf<String>()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun clearPendingHits() = pendingHits.clear()

    /** 同步获取，已命中缓存返回 Entry，否则返回 null */
    fun get(song: Song): Entry? = cache[song.id]

    /** 缓存是否存在且可用（playUrl 非空） */
    fun hasPlayUrl(song: Song): Boolean =
        cache[song.id]?.playUrl != null

    /** 封面是否已预加载进内存（统一判断：URL 在 preloadedCoverUrls 中） */
    fun hasCover(song: Song): Boolean =
        song.pic?.isNotBlank() == true && song.pic in preloadedCoverUrls

    /** 获取已预加载的封面 Bitmap（供 Compose Image() 直接使用） */
    fun getCoverBitmap(songId: String): Bitmap? = cachedBitmaps[songId]

    /** 获取封面 Bitmap（按 song.id） */
    fun getCoverBitmap(song: Song): Bitmap? = cachedBitmaps[song.id]

    /** 从缓存或网络加载播放地址（同步） */
    suspend fun loadOrGetPlayUrl(song: Song): String? {
        cache[song.id]?.playUrl?.let { return it }
        val url = apiService.getPlayUrl(song.musicRid).url
        // 写回时用当前 song.pic（已增强），保持缓存图片URL与UI一致
        cache[song.id] = Entry(playUrl = url, pic = song.pic, lyrics = cache[song.id]?.lyrics)
        return url
    }

    /** 加载歌词并写缓存 */
    suspend fun loadLyrics(song: Song): List<LrcLine> {
        cache[song.id]?.lyrics?.let { return it }

        var lyrics: List<LrcLine> = emptyList()

        // 酷我歌词
        var kr = apiService.getLyrics(song.rid.toString())
        for (attempt in 0..2) {
            if (kr.isSuccess) {
                lyrics = kr.getOrNull() ?: emptyList()
                if (lyrics.isNotEmpty()) break
            }
            delay(500)
        }

        // 网易云歌词兜底
        if (lyrics.isEmpty()) {
            val cleanName = song.name.replace(Regex("（[^）]*）|\\([^)]*\\)"), "").trim()
            var nr = apiService.searchLyricsNetEase(cleanName, song.artist)
            for (attempt in 0..2) {
                if (nr.isSuccess) {
                    val ne = nr.getOrNull() ?: emptyList()
                    if (ne.isNotEmpty()) { lyrics = ne; break }
                }
                delay(500)
            }
        }

        // QQ 歌词兜底
        if (lyrics.isEmpty()) {
            val cleanName = song.name.replace(Regex("（[^）]*）|\\([^)]*\\)"), "").trim()
            var qr = apiService.searchLyricsQQ(cleanName, song.artist)
            for (attempt in 0..2) {
                if (qr.isSuccess) {
                    val qq = qr.getOrNull() ?: emptyList()
                    if (qq.isNotEmpty()) { lyrics = qq; break }
                }
                delay(500)
            }
        }

        // 写缓存
        val existing = cache[song.id]
        cache[song.id] = Entry(
            playUrl = existing?.playUrl,
            pic = existing?.pic ?: song.pic,
            lyrics = lyrics
        )
        return lyrics
    }

    /**
     * 滑动窗口清理：只保留 [currentIdx, currentIdx + windowSize] 范围内的歌曲缓存。
     * 在预加载前调用，确保缓存队列深度始终为 windowSize。
     * 同步清理 cache + cachedBitmaps + preloadedCoverUrls 三层缓存。
     */
    fun evictOutsideWindow(playlist: List<Song>, currentIdx: Int, windowSize: Int = 3) {
        if (playlist.isEmpty()) return
        val windowIds = (currentIdx until minOf(currentIdx + windowSize, playlist.size))
            .mapNotNull { idx -> playlist.getOrNull(idx)?.id }
            .toSet()
        // 清理 cache
        cache.keys.toList().forEach { id ->
            if (id !in windowIds) cache.remove(id)
        }
        // 清理 Bitmap 缓存
        cachedBitmaps.keys.toList().forEach { id ->
            if (id !in windowIds) {
                cachedBitmaps.remove(id)
            }
        }
        // 清理预加载记录（按 pic URL 清理，只删窗口内不存在的）
        val windowPics = windowIds.mapNotNull { id -> cache[id]?.pic }.toSet()
        preloadedCoverUrls.removeAll { pic -> pic !in windowPics }
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
                    // 用 song.pic（已增强），确保 Coil 预加载的 URL 与 SongCache 缓存的 pic 一致
                    cache[song.id] = Entry(playUrl = url, pic = song.pic, lyrics = cache[song.id]?.lyrics)
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

    /**
     * 预加载封面图片（后台，等待图片真正下载并解码成 Bitmap 后再返回）。
     * 1. 检查 preloadedCoverUrls 是否已有该 URL → 命中 "封面命中缓存"
     * 2. 用 withContext(Dispatchers.IO) 真正等待 execute() 完成
     * 3. 用 BitmapFactory 解码 bytes 得到 Bitmap，存入 cachedBitmaps
     * 4. URL 加入 preloadedCoverUrls（与 playUrl/lyrics 共用同一滑动窗口）
     *
     * 注意：pendingHits.add("封面命中缓存") 移到 Bitmap 真正就绪之后，
     * 确保 toast 触发时图片已可直接渲染，不会回到 AsyncImage 重新 decode。
     */
    fun preloadPics(songs: List<Song>) {
        songs.forEach { song ->
            val picUrl = song.pic
            if (picUrl.isNullOrBlank()) return@forEach
            // 命中判断：URL 已在预加载集合中
            if (picUrl in preloadedCoverUrls) {
                pendingHits.add("封面命中缓存")
                return@forEach
            }
            // 未命中：启动协程真正下载并解码
            scope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        val loader = getImageLoader() ?: return@withContext
                        val ctx = getAppContext() ?: return@withContext
                        val request = ImageRequest.Builder(ctx)
                            .data(picUrl)
                            .build()
                        val result = loader.execute(request)
                        val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)
                            ?.bitmap
                            ?.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                        if (bitmap != null) {
                            cachedBitmaps[song.id] = bitmap
                            preloadedCoverUrls.add(picUrl)
                            // Bitmap 就绪后才标记命中，确保 toast 触发时图片已可直接渲染
                            pendingHits.add("封面命中缓存")
                        }
                    } catch (_: Exception) {
                        // 下载失败，静默忽略，不影响播放
                    }
                }
            }
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
        songs.forEach { song ->
            val picUrl = song.pic
            if (picUrl.isNullOrBlank()) return@forEach
            if (picUrl in preloadedCoverUrls) {
                cachedBitmaps[song.id]?.let { results[song.id] = it }
                return@forEach
            }
            try {
                val loader = getImageLoader() ?: return@forEach
                val request = ImageRequest.Builder(ctx).data(picUrl).build()
                val result = loader.execute(request)
                val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)
                    ?.bitmap
                    ?.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                if (bitmap != null) {
                    cachedBitmaps[song.id] = bitmap
                    preloadedCoverUrls.add(picUrl)
                    results[song.id] = bitmap
                    pendingHits.add("封面命中缓存")
                }
            } catch (_: Exception) {
                // 同步加载失败，静默忽略
            }
        }
        return results
    }

    companion object {
        @Volatile
        private var instance: SongCache? = null

        private var appContext: Context? = null

        /** 指向 BinkeMusicApp 单例，与 AsyncImage 共用同一 ImageLoader，保证缓存命中 */
        private var imageLoader: ImageLoader? = null

        fun getInstance(apiService: KuwoApiService): SongCache {
            return instance ?: synchronized(this) {
                instance ?: SongCache(apiService).also { instance = it }
            }
        }

        /** 无参获取单例（供 Compose UI 层调用，无需传入 apiService） */
        fun getInstance(): SongCache? = instance

        fun setAppContext(context: Context) {
            appContext = context.applicationContext
            // 直接取 BinkeMusicApp 已通过 ImageLoaderFactory 配置好的单例
            val app = context.applicationContext as? com.binke.music.BinkeMusicApp
            imageLoader = app?.let {
                coil.ImageLoader.Builder(it)
                    .memoryCache {
                        coil.memory.MemoryCache.Builder(it)
                            .maxSizePercent(0.25)
                            .build()
                    }
                    .diskCache {
                        coil.disk.DiskCache.Builder()
                            .directory(it.cacheDir.resolve("image_cache"))
                            .maxSizeBytes(0L)
                            .build()
                    }
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .crossfade(true)
                    .build()
            }
        }

        fun getAppContext(): Context? = appContext
        fun getImageLoader(): ImageLoader? = imageLoader
    }
}
