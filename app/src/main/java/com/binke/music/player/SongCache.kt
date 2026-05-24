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

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** 同步获取，已命中缓存返回 Entry，否则返回 null */
    fun get(song: Song): Entry? = cache[song.id]

    /** 缓存是否存在且可用（playUrl 非空） */
    fun hasPlayUrl(song: Song): Boolean =
        cache[song.id]?.playUrl != null

    /** 从缓存或网络加载播放地址（同步） */
    suspend fun loadOrGetPlayUrl(song: Song): String? {
        cache[song.id]?.playUrl?.let { return it }
        val url = apiService.getPlayUrl(song.musicRid).url
        val existing = cache[song.id]
        cache[song.id] = Entry(playUrl = url, pic = existing?.pic ?: song.pic, lyrics = existing?.lyrics)
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
     * 预加载多首歌曲的播放地址（后台，不阻塞）。
     */
    fun preloadPlayUrls(songs: List<Song>) {
        songs.forEach { song ->
            if (cache[song.id]?.playUrl == null) {
                scope.launch {
                    val url = apiService.getPlayUrl(song.musicRid).url
                    val existing = cache[song.id]
                    cache[song.id] = Entry(
                        playUrl = url,
                        pic = existing?.pic ?: song.pic,
                        lyrics = existing?.lyrics
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

    /**
     * 预加载封面图片（后台，触发 Coil 加载到内存缓存）。
     * 使用 memoryCachePolicy = ENABLED，下次加载直接命中内存。
     */
    fun preloadPics(context: Context, songs: List<Song>) {
        val imageLoader = ImageLoader(context)
        songs.forEach { song ->
            val picUrl = song.pic
            if (picUrl.isNullOrBlank()) return@forEach
            scope.launch {
                val request = ImageRequest.Builder(context)
                    .data(picUrl)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.DISABLED)  // 不落磁盘
                    .build()
                imageLoader.execute(request)
            }
        }
    }

    companion object {
        @Volatile
        private var instance: SongCache? = null

        private var appContext: Context? = null

        fun getInstance(apiService: KuwoApiService): SongCache {
            return instance ?: synchronized(this) {
                instance ?: SongCache(apiService).also { instance = it }
            }
        }

        fun setAppContext(context: Context) {
            appContext = context.applicationContext
        }

        fun getAppContext(): Context? = appContext
    }
}
