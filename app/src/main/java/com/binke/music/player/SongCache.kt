package com.binke.music.player

import com.binke.music.data.api.KuwoApiService
import com.binke.music.data.model.LrcLine
import com.binke.music.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * 歌曲预加载缓存：播放地址 + 歌词。
 * 采用 cache-aside 模式，优先读缓存，未命中再从网络加载。
 * 切歌时只更新缓存头部一个条目，并追加预加载下一首。
 */
class SongCache(private val apiService: KuwoApiService) {

    data class Entry(
        val playUrl: String?,
        val lyrics: List<LrcLine>?,
        val loading: Boolean = false
    )

    // 缓存 key 使用 Song.id（rid.toString()）
    private val cache = mutableMapOf<String, Entry>()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** 同步获取，已命中缓存返回 Entry，否则返回 null */
    fun get(song: Song): Entry? = cache[song.id]

    /** 缓存是否存在且可用（playUrl 非空） */
    fun hasPlayUrl(song: Song): Boolean =
        cache[song.id]?.playUrl != null

    /** 从缓存或网络加载完整 Entry */
    suspend fun loadOrGet(song: Song): Entry {
        cache[song.id]?.let { return it }

        val url = apiService.getPlayUrl(song.musicRid).url
        val entry = Entry(playUrl = url, lyrics = null, loading = false)
        cache[song.id] = entry
        return entry
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
            lyrics = lyrics
        )
        return lyrics
    }

    /**
     * 预加载多首歌曲的播放地址（后台，不阻塞）。
     * @param songs 要预加载的歌曲列表
     */
    fun preloadPlayUrls(songs: List<Song>) {
        songs.forEach { song ->
            if (cache[song.id]?.playUrl == null) {
                scope.launch {
                    val url = apiService.getPlayUrl(song.musicRid).url
                    val existing = cache[song.id]
                    cache[song.id] = Entry(
                        playUrl = url,
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

    companion object {
        @Volatile
        private var instance: SongCache? = null

        fun getInstance(apiService: KuwoApiService): SongCache {
            return instance ?: synchronized(this) {
                instance ?: SongCache(apiService).also { instance = it }
            }
        }
    }
}
