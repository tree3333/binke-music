package com.binke.music.ui

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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(
    private val apiService: KuwoApiService,
    private val repository: MusicRepository,
    private val musicPlayer: MusicPlayer
) : ViewModel() {

    private val songCache = SongCache.getInstance(apiService)

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

    /** 缓存命中调试 toast */
    private val _cacheToast = MutableStateFlow<String?>(null)
    val cacheToast: StateFlow<String?> = _cacheToast.asStateFlow()
    fun clearCacheToast() { _cacheToast.value = null }

    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    private val _playbackDebugParams = MutableStateFlow<String?>(null)
    val playbackDebugParams: StateFlow<String?> = _playbackDebugParams.asStateFlow()

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
            _playbackError.value = error
            _playbackDebugParams.value = buildPlaybackParams(_currentSong.value?.playUrl)
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
                // 封面增强：iTunes → 网易云 → 酷我兜底
                val enhancedSongs = withContext(Dispatchers.IO) {
                    detail.musicList.map { song ->
                        async {
                            if (song.artist.isNotBlank() && song.name.isNotBlank()) {
                                // 1. 优先 iTunes
                                val itunesPic = apiService.getCoverFromItunes(song.artist, song.name)
                                if (itunesPic.isNotBlank()) return@async song.copy(pic = itunesPic)
                                // 2. 其次网易云
                                val neteasePic = apiService.getCoverFromNetEase(song.artist, song.name)
                                if (neteasePic.isNotBlank()) return@async song.copy(pic = neteasePic)
                            }
                            song
                        }
                    }.awaitAll()
                }
                _drawerPlaylist.value = detail.copy(musicList = enhancedSongs)
                _drawerSongs.value = enhancedSongs
                // 如果 _playlist 正在播放本歌单，同步增强后的封面（iTunes → 网易云 → 酷我）到 _playlist
                if (_playlist.value.isNotEmpty()) {
                    val enhancedMap = enhancedSongs.associateBy { it.id }
                    val synced = _playlist.value.map { song -> enhancedMap[song.id] ?: song }
                    if (synced.any { s -> s.pic != _playlist.value.find { p -> p.id == s.id }?.pic }) {
                        _playlist.value = synced
                    }
                }
            }
        }
    }

    fun playPlaylist(playlist: Playlist, startIndex: Int = 0) {
        viewModelScope.launch {
            // 优先用已增强好的 _drawerSongs，保持封面顺序：iTunes → 网易云 → 酷我
            val drawerSongs = _drawerSongs.value
            val songs = if (drawerSongs.isNotEmpty()) {
                drawerSongs
            } else {
                // 未开过抽屉，同步做封面增强（iTunes → 网易云 → 酷我兜底），确保预加载和UI用同一个URL
                val rawSongs = playlist.musicList.takeIf { it.isNotEmpty() }
                    ?: withContext(Dispatchers.IO) {
                        apiService.getPlaylistDetail(playlist.id, rn = 100)?.musicList.orEmpty()
                    }
                if (rawSongs.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        rawSongs.map { song ->
                            async {
                                if (song.artist.isNotBlank() && song.name.isNotBlank()) {
                                    val itunesPic = apiService.getCoverFromItunes(song.artist, song.name)
                                    if (itunesPic.isNotBlank()) return@async song.copy(pic = itunesPic)
                                    val neteasePic = apiService.getCoverFromNetEase(song.artist, song.name)
                                    if (neteasePic.isNotBlank()) return@async song.copy(pic = neteasePic)
                                }
                                song
                            }
                        }.awaitAll()
                    }
                } else {
                    rawSongs
                }
            }
            if (songs.isNotEmpty()) {
                _playlist.value = songs
                _currentIndex.value = startIndex.coerceIn(0, songs.lastIndex)
                _playlistSource.value = PlaylistSource.NONE
                closePlaylistDrawer()
                setTab(1)
                playSongAt(_currentIndex.value)
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
    }

    fun seekTo(position: Long) {
        musicPlayer.seekTo(position)
        _currentPosition.value = position
    }

    fun playSongAt(index: Int) {
        if (index !in _playlist.value.indices) return
        _currentIndex.value = index
        playSong(_playlist.value[index])
    }

    fun playSong(song: Song) {
        // Bug2 fix: 切歌时取消上一首的歌词加载协程，防止 race condition
        lyricsJob?.cancel()

        // 立即同步预加载当前歌曲封面（等待 Bitmap 真正解码完成后才继续）
        // 放在主协程第一步，确保 toast 触发时图片已可直接渲染，不会回到 AsyncImage 重新 decode
        runBlocking(Dispatchers.IO) {
            songCache.awaitPendingBitmaps(listOf(song))
        }

        viewModelScope.launch {
            _isLoading.value = true
            _playbackError.value = null
            _playbackDebugParams.value = null
            _currentSong.value = song
            _currentPosition.value = 0L
            _duration.value = 0L
            _lyrics.value = emptyList()

            repository.addToHistory(song)
            refreshHistory()

            try {
                // 缓存优先，并发加载播放地址
                val playUrlDeferred = viewModelScope.async(Dispatchers.IO) {
                    val cached = songCache.get(song)
                    if (cached?.playUrl != null) {
                        songCache.pendingHits.add("播放地址命中缓存")
                        cached.playUrl
                    } else {
                        val url = apiService.getPlayUrl(song.musicRid).url
                        songCache.preloadPlayUrls(listOf(song))
                        url
                    }
                }
                val result = playUrlDeferred.await()
                val playUrl = result
                if (!playUrl.isNullOrBlank()) {
                    _playbackDebugParams.value = buildPlaybackParams(playUrl)
                    song.playUrl = playUrl
                    musicPlayer.play(playUrl)
                    _isPlaying.value = true
                    // 开始播放后预加载接下来的 3 首
                    preloadUpcoming()
                    // 打包所有命中项为一条 toast（只展示命中，未命中不写）
                    val hits = songCache.pendingHits.toList()
                    songCache.clearPendingHits()
                    if (hits.isNotEmpty()) {
                        _cacheToast.value = "✅ " + hits.joinToString(" | ")
                    }
                } else {
                    _playbackDebugParams.value = buildPlaybackParams(null)
                    _playbackError.value = "未获取到播放地址\n\n--- getPlayUrl 调试信息 ---\n${apiService.getPlayUrl(song.musicRid).debugInfo}"
                }

                // Bug2 fix: 歌词独立协程，且切歌时 job 被 cancel 就不再写回
                // 歌词也从缓存读（缓存会后台更新）
                lyricsJob = viewModelScope.launch(Dispatchers.IO) {
                    val cachedLyrics = songCache.get(song)?.lyrics
                    if (cachedLyrics != null) {
                        songCache.pendingHits.add("歌词命中缓存")
                        if (isActive) _lyrics.value = cachedLyrics
                    } else {
                        val lyrics = songCache.loadLyrics(song)
                        if (isActive) _lyrics.value = lyrics
                    }
                }
            } catch (e: Exception) {
                _playbackError.value = e.message ?: "播放失败"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 预加载接下来最多 3 首的播放地址和歌词。
     * 首次播放时调用以填充缓存；切歌时只追加新增的条目。
     */
    private fun preloadUpcoming() {
        val playlist = _playlist.value
        val currentIdx = _currentIndex.value
        if (playlist.isEmpty()) return

        // 滑动窗口清理：只保留 [currentIdx, currentIdx + 3]，移除已播放的条目
        songCache.evictOutsideWindow(playlist, currentIdx)

        val upcoming = mutableListOf<Song>()
        val size = playlist.size

        when (_playMode.value) {
            PlayMode.SINGLE_LOOP -> {
                // 单曲循环：只有当前这首，无需预加载下一首
                return
            }
            PlayMode.SHUFFLE -> {
                // 随机模式不知道具体下一首，随机取未缓存的 3 首
                upcoming.addAll(playlist.filter { songCache.get(it) == null }.take(3))
            }
            PlayMode.LIST_LOOP -> {
                for (offset in 1..3) {
                    val idx = (currentIdx + offset) % size
                    val song = playlist[idx]
                    if (songCache.get(song) == null) {
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
            _currentCustomPlaylistId.value = playlist.id
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
            delay(350)
            searchInternal(query, saveHistory = false)
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
                apiService.searchSongs(query)
            }
            // 封面增强：iTunes → 网易云 → 酷我兜底
            val enhanced = withContext(Dispatchers.IO) {
                results.map { song ->
                    async {
                        if (song.artist.isNotBlank() && song.name.isNotBlank()) {
                            // 1. 优先 iTunes
                            val itunesPic = apiService.getCoverFromItunes(song.artist, song.name)
                            if (itunesPic.isNotBlank()) return@async song.copy(pic = itunesPic)
                            // 2. 其次网易云
                            val neteasePic = apiService.getCoverFromNetEase(song.artist, song.name)
                            if (neteasePic.isNotBlank()) return@async song.copy(pic = neteasePic)
                        }
                        song
                    }
                }.awaitAll()
            }
            _searchResults.value = enhanced
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
    private val apiService: KuwoApiService,
    private val repository: MusicRepository,
    private val musicPlayer: MusicPlayer
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MainViewModel(apiService, repository, musicPlayer) as T
    }
}
