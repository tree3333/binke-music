package com.binke.music.player

import android.content.Context
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

/**
 * 歌曲预加载缓存：播放地址 + 封面图片 + 歌词。
 * 采用 cache-aside 模式，优先读缓存，未命中再从网络加载。
 * 切歌时只更新缓存头部一个条目，并追加预加载下一首。
 */
class SongCache(private val apiService: KuwoApiService) {

    data class Entry(
        val playUrl: String?,
        val pic: String?,
        val lyrics: List<LrcLine>?
    )

    // 缓存 key 使用 Song.id（rid.toString()）
    private val cache = mutableMapOf<String, Entry>()

    // 待展示的缓存命中消息，playSong 时打包成一条 toast 后清空
    val pendingHits = mutableListOf<String>()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun clearPendingHits() = pendingHits.clear()

    /** 同步获取，已命中缓存返回 Entry，否则返回 null */
    fun get(song: Song): Entry? = cache[song.id]

    /** 缓存是否存在且可用（playUrl 非空） */
    fun hasPlayUrl(song: Song): Boolean =
        cache[song.id]?.playUrl != null

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

        // QQ 歌词兜底
        if (lyrics.isEmpty()) {
            var qr = apiService.searchLyricsQQ(song.name, song.artist)
            for (attempt in 0..2) {
                if (qr.isSuccess) {
                    val qq = qr.getOrNull() ?: emptyList()
                    if (qq.isNotEmpty()) { lyrics = qq; break }
                }
                delay(500)
            }
        }

        // 网易云歌词兜底
        if (lyrics.isEmpty()) {
            var nr = apiService.searchLyricsNetEase(song.name, song.artist)
            for (attempt in 0..2) {
                if (nr.isSuccess) {
                    val ne = nr.getOrNull() ?: emptyList()
                    if (ne.isNotEmpty()) { lyrics = ne; break }
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
     */
    fun evictOutsideWindow(playlist: List<Song>, currentIdx: Int, windowSize: Int = 3) {
        if (playlist.isEmpty()) return
        val windowIds = (currentIdx until minOf(currentIdx + windowSize, playlist.size))
            .mapNotNull { idx -> playlist.getOrNull(idx)?.id }
            .toSet()
        cache.keys.toList().forEach { id ->
            if (id !in windowIds) {
                cache.remove(id)
            }
        }
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
     * 预加载封面图片（后台，等待图片真正缓存到内存后再返回）。
     * 使用 SongCache.getImageLoader() 获取与 AsyncImage 同一个实例，保证缓存命中。
     * 加载前检查 Coil 内存缓存是否已有，命中则记入 pendingHits。
     */
    fun preloadPics(songs: List<Song>) {
        val loader = getImageLoader() ?: return
        val ctx = getAppContext() ?: return
        val memoryCache = loader.memoryCache
        songs.forEach { song ->
            val picUrl = song.pic
            if (picUrl.isNullOrBlank()) return@forEach
            // 检查 Coil 内存缓存是否已有此封面
            val request = ImageRequest.Builder(ctx).data(picUrl).build()
            val cacheKey = request.memoryCacheKey ?: return@forEach
            val cachedBitmap = memoryCache?.get(cacheKey!!)
            if (cachedBitmap != null) {
                pendingHits.add("封面命中缓存")
                return@forEach
            }
            scope.launch {
                loader.execute(request)
            }
        }
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
