package com.binke.music.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.binke.music.data.api.KuwoApiService
import com.binke.music.data.model.LrcLine
import com.binke.music.data.model.Page
import com.binke.music.data.model.PlayMode
import com.binke.music.data.model.Playlist
import com.binke.music.data.model.Song
import com.binke.music.data.repository.MusicRepository
import com.binke.music.player.MusicPlayer
import com.binke.music.player.SongCache
import com.binke.music.ui.theme.CoverColorPredictor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class MainViewModel(
    private val application: Application,
    private val apiService: KuwoApiService,
    private val repository: MusicRepository,
    private val musicPlayer: MusicPlayer
) : ViewModel() {

    private val songCache = SongCache.getInstance(apiService)

    // 封面颜色预测器 (7 个 int8 TFLite ensemble, 3.73 MB)
    private val colorPredictor: CoverColorPredictor by lazy { CoverColorPredictor(application) }
    private val _coverColors = MutableStateFlow(CoverColorPredictor.ColorTriple(
        bg = androidx.compose.ui.graphics.Color(0xFF121212),
        pl = androidx.compose.ui.graphics.Color.White,
        nl = androidx.compose.ui.graphics.Color(0xFF9A9A9F)
    ))
    val coverColors: StateFlow<CoverColorPredictor.ColorTriple> = _coverColors.asStateFlow()
    private val httpClient: OkHttpClient by lazy { OkHttpClient() }
    private var coverPredictionJob: Job? = null

    /**
     * 触发封面颜色预测。
     * 取消上一次未完成的预测（防 race），下载封面 → 推理 → 更新 _coverColors。
     * 失败用默认 fallback，不抛异常。
     */
    private fun triggerCoverPrediction(picUrl: String) {
        if (picUrl.isBlank()) return
        coverPredictionJob?.cancel()
        coverPredictionJob = viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    httpClient.newCall(
                        Request.Builder().url(picUrl).build()
                    ).execute().use { resp ->
                        if (!resp.isSuccessful) return@withContext null
                        resp.body?.bytes()
                    }
                } ?: return@launch
                val bmp = colorPredictor.decodeBitmap(bytes) ?: return@launch
                val triple = colorPredictor.predict(bmp)
                _coverColors.value = triple
            } catch (e: Exception) {
                // fallback: 保留当前 _coverColors (默认深灰白)
            }
        }
    }

    private val _currentPage = MutableStateFlow(Page.HOME)
    val currentPage: StateFlow<Page> = _currentPage.asStateFlow()

    private val _currentTab = MutableStateFlow(0)
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    private val _recommendPlaylists = MutableStateFlow<List<Playlist>>(emptyList())
    val recommendPlaylists: StateFlow<List<Playlist>> = _recommendPlaylists.asStateFlow()

    private val _bangPlaylists = MutableStateFlow<List<Playlist>>(emptyList())
    val bangPlaylists: StateFlow<List<Playlist>> = _bangPlaylists.asStateFlow()

    private val _isLoadingHome = MutableStateFlow(false)
    val isLoadingHome: StateFlow<Boolean> = _isLoadingHome.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _playMode = MutableStateFlow(PlayMode.LIST_LOOP)
    val playMode: StateFlow<PlayMode> = _playMode.asStateFlow()

    private val _lyrics = MutableStateFlow<List<LrcLine>>(emptyList())
    val lyrics: StateFlow<List<LrcLine>> = _lyrics.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    private val _playbackDebugParams = MutableStateFlow<String?>(null)
    val playbackDebugParams: StateFlow<String?> = _playbackDebugParams.asStateFlow()

    /**
     * 【1.0.36】记录"已对哪首 song 自动重试过"——同一首只允许自动重试 1 次，
     * 第二次 onPlayerError 直接弹窗给 user。切歌时（_currentSong 改变）允许再重试。
     */
    private var lastAutoRetriedSongId: String? = null

    private val _playlist = MutableStateFlow<List<Song>>(emptyList())
    val playlist: StateFlow<List<Song>> = _playlist.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _playlistSource = MutableStateFlow<PlaylistSource>(PlaylistSource.NONE)
    val playlistSource: StateFlow<PlaylistSource> = _playlistSource.asStateFlow()

    enum class PlaylistSource {
        NONE, FAVORITES, HISTORY, CUSTOM
    }

    private val _currentCustomPlaylistId = MutableStateFlow<String?>(null)
    val currentCustomPlaylistId: StateFlow<String?> = _currentCustomPlaylistId.asStateFlow()

    /** 跟踪当前歌词加载协程，用于切歌时取消 */
    private var lyricsJob: Job? = null
    /** 重新加载当前歌曲的歌词（供 onMediaItemTransition 和恢复界面时调用） */
    private fun reloadLyricsForCurrentSong() {
        val song = _currentSong.value ?: return
        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch(Dispatchers.IO) {
            // 【1.0.33 修复】不再走 cache[].lyrics 直接读取——空 list 会误判为"已缓存"且绕过
            // SongCache.loadLyrics 的 cacheHit 逻辑（导致 4 源全失败时 100 首歌全缓存空 list 永远不重试）。
            // 改用 loadLyrics 自己处理 cacheHit：lyricsTried=true + (有歌词 OR 距上次 > 5 分钟) 才命中，
            // 否则重新走 4 源。
            val loaded = songCache.loadLyrics(song)
            if (isActive) _lyrics.value = loaded
        }
    }
    /** 防止 MediaSession 触发的 onMediaItemTransition 与 playSong 之间循环 */
    private var pendingMediaItemTransition = false

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Song>>(emptyList())
    val searchResults: StateFlow<List<Song>> = _searchResults.asStateFlow()

    private val _searchSuggestions = MutableStateFlow<List<String>>(emptyList())
    val searchSuggestions: StateFlow<List<String>> = _searchSuggestions.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _favorites = MutableStateFlow<List<Song>>(emptyList())
    val favorites: StateFlow<List<Song>> = _favorites.asStateFlow()

    private val _history = MutableStateFlow<List<Song>>(emptyList())
    val history: StateFlow<List<Song>> = _history.asStateFlow()

    private val _customPlaylists = MutableStateFlow<List<Playlist>>(emptyList())
    val customPlaylists: StateFlow<List<Playlist>> = _customPlaylists.asStateFlow()

    private val _showPlaylistDrawer = MutableStateFlow(false)
    val showPlaylistDrawer: StateFlow<Boolean> = _showPlaylistDrawer.asStateFlow()

    private val _drawerPlaylist = MutableStateFlow<Playlist?>(null)
    val drawerPlaylist: StateFlow<Playlist?> = _drawerPlaylist.asStateFlow()

    private val _drawerSongs = MutableStateFlow<List<Song>>(emptyList())
    val drawerSongs: StateFlow<List<Song>> = _drawerSongs.asStateFlow()

    private val _showQueueSheet = MutableStateFlow(false)
    val showQueueSheet: StateFlow<Boolean> = _showQueueSheet.asStateFlow()

    private val _showPlaylistPicker = MutableStateFlow(false)
    val showPlaylistPicker: StateFlow<Boolean> = _showPlaylistPicker.asStateFlow()

    private val _playlistPickerSong = MutableStateFlow<Song?>(null)
    val playlistPickerSong: StateFlow<Song?> = _playlistPickerSong.asStateFlow()

    private val _renameTarget = MutableStateFlow<Playlist?>(null)
    val renameTarget: StateFlow<Playlist?> = _renameTarget.asStateFlow()

    private var suggestionJob: Job? = null
    private var autoSearchJob: Job? = null

    init {
        musicPlayer.onPositionChanged = { pos ->
            _currentPosition.value = pos
        }
        musicPlayer.onDurationChanged = { dur ->
            _duration.value = dur
        }
        musicPlayer.onPlaybackStateChanged = { playing ->
            _isPlaying.value = playing
        }
        musicPlayer.onPlaybackError = { error ->
            handlePlaybackError(error)
        }
        musicPlayer.onTrackEnded = {
            // SINGLE_LOOP 时让 ExoPlayer 自己处理（REPEAT_MODE_ONE），无需重新 playSong
            if (_playMode.value == PlayMode.SINGLE_LOOP) {
                musicPlayer.seekTo(0)
                musicPlayer.resume()
            } else {
                next()
            }
        }
        // MediaSession 直接切歌时（锁屏上一首/下一首），只同步 UI 状态，URL 已在缓存中由 urlProvider 提供
        musicPlayer.onMediaItemTransition = lambda@{ newIndex ->
            if (pendingMediaItemTransition) return@lambda
            pendingMediaItemTransition = true
            val playlist = _playlist.value
            if (newIndex in playlist.indices) {
                _currentIndex.value = newIndex
                _currentSong.value = playlist[newIndex]
                // 【1.0.36】MediaSession 切歌（同 next/prev / 锁屏按钮）也重置自动重试标记
                lastAutoRetriedSongId = null
                triggerCoverPrediction(playlist[newIndex].pic)
                _currentPosition.value = 1500L
                // duration 不清零：保留上一首的 duration 作为占位，避免 ExoPlayer 报新 duration
                // 之间的几秒内 Slider 看到 duration=0 而 value=0 导致进度条回 0 抖动
                _lyrics.value = emptyList()
                preloadUpcoming()
                // 重新加载当前歌曲的歌词
                reloadLyricsForCurrentSong()
            }
            pendingMediaItemTransition = false
        }
        // 注册 URL 提供器：从缓存读取 URL（由 playSong 预取时写入）。
        // 【1.0.36】走 getValidPlayUrl —— TTL 过期返回 null，ExoPlayer 继续用原 MediaItem URL，
        // 拉流失败 → onPlayerError 触发 → ViewModel 自动重试 1 次兜底。
        musicPlayer.urlProvider = lambda@{ mediaId ->
            val song = _playlist.value.find { it.id == mediaId }
            song?.let { songCache.getValidPlayUrl(it) }
        }

        // APP 被系统杀掉后恢复时，同步当前播放状态（不依赖 onMediaItemTransition）
        val currentMediaItem = musicPlayer.getCurrentMediaItem()
        if (currentMediaItem != null) {
            val mediaId = currentMediaItem.mediaId
            val idx = musicPlayer.getCurrentMediaItemIndex()
            val existingSong = _playlist.value.find { it.id == mediaId }
            if (existingSong != null) {
                _currentIndex.value = idx
                _currentSong.value = existingSong
                triggerCoverPrediction(existingSong.pic)
                reloadLyricsForCurrentSong()
            }
        }

        viewModelScope.launch {
            loadSearchHistory()
            refreshMineData()
        }
    }

    fun loadHomeData() {
        viewModelScope.launch {
            // 启动直接进音乐页，避免先闪推荐页
            setTab(1)
            _isLoadingHome.value = true
            try {
                _recommendPlaylists.value = withContext(Dispatchers.IO) {
                    apiService.getRecommendPlaylists(pn = 1, rn = 20)
                }
                _bangPlaylists.value = withContext(Dispatchers.IO) {
                    apiService.getBangMenu().take(20)
                }
                val firstPlaylist = _recommendPlaylists.value.firstOrNull() ?: _bangPlaylists.value.firstOrNull()
                if (_currentSong.value == null && firstPlaylist != null) {
                    playPlaylist(firstPlaylist)
                }
            } finally {
                _isLoadingHome.value = false
            }
        }
    }

    fun navigateTo(page: Page) {
        _currentPage.value = page
    }

    fun setTab(tab: Int) {
        _currentTab.value = tab
        when (tab) {
            0 -> navigateTo(Page.HOME)
            1 -> navigateTo(Page.MUSIC)
            2 -> navigateTo(Page.MINE)
        }
    }

    fun openPlaylistDetail(playlist: Playlist) {
        _drawerPlaylist.value = playlist
        _showPlaylistDrawer.value = true
        loadPlaylistDetail(playlist.id)
    }

    fun closePlaylistDrawer() {
        _showPlaylistDrawer.value = false
    }

    private fun loadPlaylistDetail(playlistId: String) {
        viewModelScope.launch {
            val detail = withContext(Dispatchers.IO) {
                apiService.getPlaylistDetail(playlistId, rn = 100)
            }
            if (detail != null) {
                // 推荐歌单抽屉内的歌曲列表直接用 Kuwo 原生封面，不做 iTunes/网易云增强（快）
                _drawerPlaylist.value = detail
                _drawerSongs.value = detail.musicList
            }
        }
    }

    fun playPlaylist(playlist: Playlist, startIndex: Int = 0) {
        viewModelScope.launch {
            // 列表封面直接用酷我原图，不做高清增强，保证列表加载速度
            val songs = playlist.musicList.takeIf { it.isNotEmpty() }
                ?: withContext(Dispatchers.IO) {
                    apiService.getPlaylistDetail(playlist.id, rn = 100)?.musicList.orEmpty()
                }
            if (songs.isNotEmpty()) {
                val idx = startIndex.coerceIn(0, songs.lastIndex)
                _playlist.value = songs
                _currentIndex.value = idx
                _playlistSource.value = PlaylistSource.NONE
                closePlaylistDrawer()
                setTab(1)
                SongCache.getAppContext()?.let { songCache.awaitPendingBitmaps(listOf(songs[idx])) }
                playSongAt(idx)
            }
        }
    }

    fun playPause() {
        if (musicPlayer.isPlaying()) {
            musicPlayer.pause()
        } else {
            musicPlayer.resume()
        }
        _isPlaying.value = musicPlayer.isPlaying()
    }

    fun pause() {
        musicPlayer.pause()
        _isPlaying.value = false
    }

    fun previous() {
        if (_playlist.value.isEmpty()) return
        // 标准行为：播放位置超过3秒则重唱到开头，否则切到上一首
        if (musicPlayer.currentPosition() > 3000) {
            seekTo(0)
            return
        }
        val newIndex = when (_playMode.value) {
            PlayMode.SINGLE_LOOP -> _currentIndex.value.coerceAtLeast(0)
            PlayMode.SHUFFLE -> _playlist.value.indices.random()
            PlayMode.LIST_LOOP -> {
                val idx = _currentIndex.value - 1
                if (idx < 0) _playlist.value.lastIndex else idx
            }
        }
        playSongAt(newIndex)
    }

    fun next() {
        if (_playlist.value.isEmpty()) return
        val newIndex = when (_playMode.value) {
            PlayMode.SINGLE_LOOP -> _currentIndex.value.coerceAtLeast(0)
            PlayMode.SHUFFLE -> _playlist.value.indices.random()
            PlayMode.LIST_LOOP -> {
                val idx = _currentIndex.value + 1
                if (idx >= _playlist.value.size) 0 else idx
            }
        }
        playSongAt(newIndex)
    }

    fun togglePlayMode() {
        val newMode = when (_playMode.value) {
            PlayMode.LIST_LOOP -> PlayMode.SINGLE_LOOP
            PlayMode.SINGLE_LOOP -> PlayMode.SHUFFLE
            PlayMode.SHUFFLE -> PlayMode.LIST_LOOP
        }
        _playMode.value = newMode
        // 同步 ExoPlayer repeat mode（修复单曲循环不生效）
        musicPlayer.setRepeatMode(
            when (newMode) {
                PlayMode.SINGLE_LOOP -> androidx.media3.common.Player.REPEAT_MODE_ONE
                else -> androidx.media3.common.Player.REPEAT_MODE_OFF
            }
        )
        // 同步 ExoPlayer shuffle mode（修复 AUTO 切歌不随机——手动下一首/上一首 MainViewModel 自己算随机索引
        // 是 work 的，但 AUTO 切歌走 ExoPlayer 内部 timeline，shuffleModeEnabled=false 永远是顺序）
        musicPlayer.setShuffleMode(newMode == PlayMode.SHUFFLE)
    }

    fun seekTo(position: Long) {
        musicPlayer.seekTo(position)
        _currentPosition.value = position
    }

    fun playSongAt(index: Int) {
        if (index !in _playlist.value.indices) return
        _currentIndex.value = index
        // 注意：不要在这里调 musicPlayer.setCurrentIndex(index)，
        // 那会触发 seekToDefaultPosition 立即切到新位置开始播，
        // 然后 playSong 协程内的 setPlaylist 又会把 position 重置为 0，导致"播 1s 后从头再播"。
        // setPlaylist 内部的 setMediaItems(..., snapshotIdx, 0) 已经把 ExoPlayer 切到正确位置。
        playSong(_playlist.value[index])
    }

    fun playSong(song: Song) {
        // 注意：歌词协程的 cancel 由 reloadLyricsForCurrentSong 内部处理，避免重复
        // 同步快照：playSong 启动时的完整播放上下文，防止后续调用覆盖 _playlist.value 后
        // 协程体内仍用旧快照（urlMapDeferred/currentIdx）但 setPlaylist 却用新列表
        val snapshotPlaylist: List<Song>
        val snapshotIdx: Int
        val snapshotSong: Song

        if (_playlist.value.none { it.id == song.id }) {
            // 搜索结果点击：单首歌列表
            snapshotPlaylist = listOf(song)
            snapshotIdx = 0
            snapshotSong = song
            _playlist.value = snapshotPlaylist
            _currentIndex.value = snapshotIdx
            _playlistSource.value = PlaylistSource.NONE
        } else {
            // 同列表内切歌：用当前列表快照和对应索引
            snapshotPlaylist = _playlist.value
            snapshotIdx = snapshotPlaylist.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
            snapshotSong = song
            _currentIndex.value = snapshotIdx
        }
        // 【1.0.36】切到新 song → 重置自动重试标记，新 song 允许再重试 1 次
        lastAutoRetriedSongId = null

        viewModelScope.launch {
            _isLoading.value = true
            _playbackError.value = null
            _playbackDebugParams.value = null
            _currentPosition.value = 1500L
            // duration 不清零：保留上一首的 duration 作为占位，避免 ExoPlayer 报新 duration
            // 之间的几秒内 Slider 看到 duration=0 而 value=0 导致进度条回 0 抖动
            _lyrics.value = emptyList()

            // 并发预取所有歌曲的 URL（用快照 playlist）
            val urlMapDeferred = viewModelScope.async(Dispatchers.IO) {
                snapshotPlaylist.map { s ->
                    async {
                        val url: String = songCache.get(s)?.playUrl
                            ?: apiService.getPlayUrl(s.musicRid).url.orEmpty()
                        s.id to url
                    }
                }.awaitAll().filter { (_, url) -> url.isNotBlank() }.toMap()
            }
            // 封面增强优先
            val enhancedSong = withContext(Dispatchers.IO) {
                if (snapshotSong.artist.isNotBlank() && snapshotSong.name.isNotBlank()) {
                    val itunesPic = apiService.getCoverFromItunes(snapshotSong.artist, snapshotSong.name)
                    if (itunesPic.isNotBlank()) return@withContext snapshotSong.copy(pic = itunesPic)
                    val neteasePic = apiService.getCoverFromNetEase(snapshotSong.artist, snapshotSong.name)
                    if (neteasePic.isNotBlank()) return@withContext snapshotSong.copy(pic = neteasePic)
                }
                snapshotSong
            }
            _currentSong.value = enhancedSong
            triggerCoverPrediction(enhancedSong.pic)
            // 用快照 playlist 更新封面（同步进行，不影响 urlMapDeferred）
            val updatedList = snapshotPlaylist.toMutableList()
            val idxInSnapshot = updatedList.indexOfFirst { it.id == snapshotSong.id }
            if (idxInSnapshot >= 0) {
                updatedList[idxInSnapshot] = enhancedSong
            }
            _playlist.value = updatedList
            repository.addToHistory(enhancedSong)
            refreshHistory()

            // 等所有 URL 预取完毕，再设置播放列表（所有 MediaItem 都有真实 URL）
            val urlMap = urlMapDeferred.await()
            musicPlayer.setPlaylist(updatedList, urlMap, snapshotIdx)

            val playUrl = urlMap[updatedList.getOrNull(snapshotIdx)?.id]
            if (!playUrl.isNullOrBlank()) {
                _playbackDebugParams.value = buildPlaybackParams(playUrl)
                musicPlayer.play(playUrl)
                _isPlaying.value = true
                preloadUpcoming()

                // 关键修复：异步 await 接下来 6 首的 cover 真正就绪（不阻塞当前播放）。
                // 切下一首时 cover 必然已写入 Coil memoryCache，AsyncImage 直接命中，零等待。
                val upcomingForAwait = buildList {
                    val size = updatedList.size
                    for (offset in 1..6) {
                        if (size > 1) {
                            add(updatedList[(snapshotIdx + offset) % size])
                        } else {
                            updatedList.getOrNull(snapshotIdx + offset)?.let { add(it) }
                        }
                    }
                }
                viewModelScope.launch(Dispatchers.IO) {
                    val t0 = System.currentTimeMillis()
                    if (SongCache.getAppContext() == null) return@launch
                    val results = songCache.awaitPendingBitmaps(upcomingForAwait)
                    val msg = "✓ 后续 ${upcomingForAwait.size} 首 cover await 完成: 就绪 ${results.size} 张 (${System.currentTimeMillis() - t0}ms)"
                    Log.d("MainViewModel", msg)
                    SongCache.getLogFile()?.let { f ->
                        try {
                            val ts = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.CHINA).format(java.util.Date())
                            f.appendText("[$ts] [MainViewModel] $msg\n")
                        } catch (_: Exception) {}
                    }
                }
            } else {
                _playbackDebugParams.value = buildPlaybackParams(null)
                _playbackError.value = "未获取到播放地址"
            }

            // 重新加载当前歌曲的歌词（与 reloadLyricsForCurrentSong 复用）
            // 此时 _currentSong.value 已是 enhancedSong，与 MediaSession 切歌路径行为一致
            reloadLyricsForCurrentSong()
            _isLoading.value = false
        }
    }

    /**
     * 预加载接下来最多 6 首的播放地址、歌词和封面图片。
     * 首次播放时调用以填充缓存；切歌时只追加新增的条目。
     */
    /**
     * 预加载即将播放的歌曲。
     * 【1.0.36】滑窗缩到 4 首（1 当前 + 3 即将），配合 playUrl TTL=5min 防止 sig 过期。
     *
     * 切歌时意图：
     * - 旧窗口 [N, N+4) 被 evict 滚出 1 首 N
     * - 新窗口 [N+1, N+5) 滚进 1 首 N+4
     * - 必补 1 首：N+4
     * - seek 兜底：拖动进度条后窗口内可能有几首空缺，一起补
     *
     * 随机模式：不知道具体下一首，把窗口内未缓存的全补上（最多 WINDOW_UPCOMING=3 首）
     * 单曲循环：无需预加载
     */
    private fun preloadUpcoming() {
        val playlist = _playlist.value
        val currentIdx = _currentIndex.value
        if (playlist.isEmpty()) return

        // 滑窗清理：只保留 [currentIdx, currentIdx + WINDOW_SIZE)
        songCache.evictOutsideWindow(playlist, currentIdx)

        val upcoming = mutableListOf<Song>()
        val size = playlist.size

        when (_playMode.value) {
            PlayMode.SINGLE_LOOP -> {
                // 单曲循环：只有当前这首，无需预加载下一首
                return
            }
            PlayMode.SHUFFLE -> {
                // 随机模式：取窗口内未缓存的（首次启动全空 → 补 3 首；正常随时只补缺）
                val window = (1..SongCache.WINDOW_UPCOMING).mapNotNull { off ->
                    playlist.getOrNull((currentIdx + off) % size)
                }
                upcoming.addAll(window.filter { !songCache.hasPlayUrl(it) })
            }
            PlayMode.LIST_LOOP -> {
                // 必补 1 首：滚进来那首 (currentIdx + WINDOW_SIZE)，取模处理列表末→首
                val nextIdx = (currentIdx + SongCache.WINDOW_SIZE) % size
                playlist.getOrNull(nextIdx)?.let { song ->
                    if (!songCache.hasPlayUrl(song)) upcoming.add(song)
                }
                // seek 兜底：拖动进度条后窗口内某些位置可能没缓存（比如跨过几首）
                for (off in 1 until SongCache.WINDOW_SIZE) {
                    val idx = (currentIdx + off) % size
                    val song = playlist.getOrNull(idx) ?: continue
                    if (song !in upcoming && !songCache.hasPlayUrl(song)) {
                        upcoming.add(song)
                    }
                }
            }
        }

        if (upcoming.isNotEmpty()) {
            songCache.preloadPlayUrls(upcoming)
            upcoming.forEach { songCache.preloadLyrics(it) }
            SongCache.getAppContext()?.let {
                songCache.preloadPics(upcoming)
            }
        }
    }

    fun seekByLyricOffset(offset: Float) {
        val currentSec = _currentPosition.value / 1000f
        val newSec = (currentSec + offset).coerceIn(0f, _duration.value / 1000f)
        seekTo((newSec * 1000).toLong())
    }

    fun seekToLyricIndex(index: Int) {
        val line = _lyrics.value.getOrNull(index) ?: return
        seekTo((line.time * 1000).toLong())
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            if (repository.isFavorite(song.id)) {
                repository.removeFavorite(song.id)
            } else {
                repository.addFavorite(song)
            }
            refreshFavorites()
        }
    }

    fun isFavorite(songId: String): Boolean = _favorites.value.any { it.id == songId }

    fun playFavorites() {
        val favs = _favorites.value
        if (favs.isNotEmpty()) {
            _playlist.value = favs
            _currentIndex.value = 0
            _playlistSource.value = PlaylistSource.FAVORITES
            setTab(1)
            playSongAt(0)
        }
    }

    fun playHistory() {
        val hist = _history.value
        if (hist.isNotEmpty()) {
            _playlist.value = hist
            _currentIndex.value = 0
            _playlistSource.value = PlaylistSource.HISTORY
            setTab(1)
            playSongAt(0)
        }
    }

    fun playCustomPlaylist(playlist: Playlist) {
        if (playlist.musicList.isNotEmpty()) {
            _playlist.value = playlist.musicList
            _currentIndex.value = 0
            _playlistSource.value = PlaylistSource.CUSTOM
            setTab(1)
            playSongAt(0)
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            repository.createPlaylist(name)
            refreshMineData()
        }
    }

    fun renamePlaylist(playlistId: String, newName: String) {
        viewModelScope.launch {
            repository.renamePlaylist(playlistId, newName)
            _renameTarget.value = null
            refreshMineData()
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
            refreshMineData()
        }
    }

    fun showRenameDialog(playlist: Playlist) {
        _renameTarget.value = playlist
    }

    fun closeRenameDialog() {
        _renameTarget.value = null
    }

    fun openQueueSheet() {
        _showQueueSheet.value = true
    }

    fun closeQueueSheet() {
        _showQueueSheet.value = false
    }

    fun showPlaylistPicker(song: Song) {
        _playlistPickerSong.value = song
        _showPlaylistPicker.value = true
    }

    fun closePlaylistPicker() {
        _playlistPickerSong.value = null
        _showPlaylistPicker.value = false
    }

    fun addCurrentSongToPlaylist(playlistId: String) {
        val song = _playlistPickerSong.value ?: return
        viewModelScope.launch {
            repository.addSongToPlaylist(playlistId, song)
            refreshMineData()
            closePlaylistPicker()
        }
    }

    fun removeSongFromCustomPlaylist(playlistId: String, songId: String) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, songId)
            refreshMineData()
        }
    }

    fun removeSongFromQueue(index: Int) {
        if (index !in _playlist.value.indices) return
        val song = _playlist.value[index]
        val mutable = _playlist.value.toMutableList()
        mutable.removeAt(index)
        _playlist.value = mutable

        // 只从当前来源歌单中删除
        viewModelScope.launch {
            when (_playlistSource.value) {
                PlaylistSource.FAVORITES -> {
                    repository.removeFavorite(song.id)
                    refreshFavorites()
                }
                PlaylistSource.HISTORY -> {
                    repository.removeFromHistory(song.id)
                    refreshHistory()
                }
                PlaylistSource.CUSTOM -> {
                    // 只从当前播放的自定义歌单中删除
                    _currentCustomPlaylistId.value?.let { playlistId ->
                        repository.removeSongFromPlaylist(playlistId, song.id)
                    }
                    refreshMineData()
                }
                PlaylistSource.NONE -> {
                    // 首页歌单/搜索播放，不关联"我的"标签，不删除
                }
            }
        }

        if (mutable.isEmpty()) {
            _currentIndex.value = -1
            _currentSong.value = null
            musicPlayer.pause()
            _isPlaying.value = false
            closeQueueSheet()
            return
        }
        when {
            index < _currentIndex.value -> _currentIndex.value -= 1
            index == _currentIndex.value -> {
                val nextIndex = _currentIndex.value.coerceAtMost(mutable.lastIndex)
                _currentIndex.value = nextIndex
                playSongAt(nextIndex)
            }
        }
    }

    fun clearPlaybackError() {
        _playbackError.value = null
        _playbackDebugParams.value = null
    }

    /**
     * 【1.0.36】onPlaybackError 处理函数。
     * - IO_BAD_HTTP_STATUS（URL 过期/CDN 鉴权失效）且当前 song 没自动重试过 → 静默重试 1 次
     *   重试成功不弹窗；新 URL 拉不到才弹窗给 user
     * - 其它错误（解码失败、网络断开等）直接弹窗
     *
     * lastAutoRetriedSongId 在 playSong / onMediaItemTransition 切歌时重置，
     * 保证新 song 允许再自动重试 1 次。
     */
    private fun handlePlaybackError(error: String) {
        val currentSongId = _currentSong.value?.id
        val isHttpStatusError = error.contains("ERROR_CODE_IO_BAD_HTTP_STATUS", ignoreCase = true)
        val shouldAutoRetry = isHttpStatusError && currentSongId != null && currentSongId != lastAutoRetriedSongId
        if (shouldAutoRetry) {
            val song = _currentSong.value ?: return
            lastAutoRetriedSongId = currentSongId
            viewModelScope.launch(Dispatchers.IO) {
                songCache.invalidatePlayUrl(song)
                val newUrl = songCache.loadOrGetPlayUrl(song)
                withContext(Dispatchers.Main) {
                    if (!newUrl.isNullOrBlank() && musicPlayer.retry(newUrl)) {
                        android.util.Log.i(
                            "MainViewModel",
                            "auto-retry OK: songId=$currentSongId, url=${newUrl.take(60)}"
                        )
                    } else {
                        // 新 URL 拉不到 / retry 失败 → 弹窗
                        showPlaybackError(error)
                    }
                }
            }
        } else {
            showPlaybackError(error)
        }
    }

    private fun showPlaybackError(error: String) {
        _playbackError.value = error
        _playbackDebugParams.value = buildPlaybackParams(_currentSong.value?.playUrl)
    }

    private fun buildPlaybackParams(url: String?): String {
        return buildString {
            appendLine("uri=${url ?: ""}")
            append("mimeType=audio/mpeg")
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        suggestionJob?.cancel()
        autoSearchJob?.cancel()

        if (query.isBlank()) {
            _searchSuggestions.value = emptyList()
            _searchResults.value = emptyList()
            return
        }

        suggestionJob = viewModelScope.launch {
            delay(250)
            val suggestions = withContext(Dispatchers.IO) {
                apiService.getSearchSuggestions(query)
            }
            _searchSuggestions.value = suggestions
        }

        autoSearchJob = viewModelScope.launch {
            val currentQuery = query
            delay(350)
            // delay 期间如果 query 变了，忽略此次结果
            if (currentQuery == _searchQuery.value) {
                searchInternal(currentQuery, saveHistory = false)
            }
        }
    }

    fun search(query: String) {
        if (query.isBlank()) return
        _searchQuery.value = query
        viewModelScope.launch {
            repository.addSearchHistory(query)
            loadSearchHistory()
            searchInternal(query, saveHistory = false)
        }
    }

    private suspend fun searchInternal(query: String, saveHistory: Boolean) {
        _isSearching.value = true
        try {
            if (saveHistory) {
                repository.addSearchHistory(query)
                loadSearchHistory()
            }
            val results = withContext(Dispatchers.IO) {
                val raw = apiService.searchSongs(query)
                android.util.Log.d("SearchDebug", "searchSongs($query) returned ${raw.size} results")
                raw
            }
            // 搜索结果列表直接用 Kuwo 原生封面（web_albumpic_short / web_artistpic_short），不做 iTunes/网易云增强
            _searchResults.value = results
        } finally {
            _isSearching.value = false
        }
    }

    private fun loadSearchHistory() {
        viewModelScope.launch {
            _searchHistory.value = repository.getSearchHistory()
        }
    }

    private fun refreshFavorites() {
        viewModelScope.launch {
            _favorites.value = repository.getFavorites()
        }
    }

    private fun refreshHistory() {
        viewModelScope.launch {
            _history.value = repository.getHistory()
        }
    }

    private fun refreshMineData() {
        viewModelScope.launch {
            _favorites.value = repository.getFavorites()
            _history.value = repository.getHistory()
            _customPlaylists.value = repository.getCustomPlaylists()
        }
    }
}

class MainViewModelFactory(
    private val application: android.app.Application,
    private val apiService: KuwoApiService,
    private val repository: MusicRepository,
    private val musicPlayer: MusicPlayer
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MainViewModel(application, apiService, repository, musicPlayer) as T
    }
}
