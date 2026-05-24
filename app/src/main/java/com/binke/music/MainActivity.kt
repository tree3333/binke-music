package com.binke.music

import android.os.Bundle
import android.content.Intent
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import com.binke.music.data.model.Page
import com.binke.music.data.model.Playlist
import com.binke.music.data.model.Song
import com.binke.music.player.BinkeMediaCallbacks
import com.binke.music.player.MediaControllerCallback
import com.binke.music.player.MusicPlayer
import com.binke.music.player.PlaybackService
import com.binke.music.player.SongCache
import com.binke.music.ui.MainViewModel
import com.binke.music.ui.MainViewModelFactory
import com.binke.music.ui.PlaylistDrawer
import com.binke.music.ui.components.BottomNav
import com.binke.music.ui.components.TopBar
import com.binke.music.ui.screens.HomeScreen
import com.binke.music.ui.screens.MineScreen
import com.binke.music.ui.screens.MusicScreen
import com.binke.music.ui.screens.SearchScreen

private const val BASE_WIDTH_DP = 1920f
private const val BASE_HEIGHT_DP = 1080f

private fun Int.xdp(sx: Float): Dp = (this * sx).dp
private fun Int.ydp(sy: Float): Dp = (this * sy).dp
private fun Int.sdp(su: Float): Dp = (this * su).dp

@Composable
private fun CompactDialog(
    title: String,
    onDismissRequest: () -> Unit,
    sx: Float,
    sy: Float,
    su: Float,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String = "取消",
    onDismissClick: () -> Unit = onDismissRequest,
    content: @Composable ColumnScope.() -> Unit
) {
    val cfg = LocalConfiguration.current
    val isPortrait = cfg.screenHeightDp > cfg.screenWidthDp
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier.width(if (isPortrait) cfg.screenWidthDp.dp * 0.85f else 620.xdp(sx)),
            shape = RoundedCornerShape(24.sdp(su)),
            color = Color(0xFF1C1C1E)
        ) {
            Column(
                modifier = Modifier.padding(36.sdp(su)),
                verticalArrangement = Arrangement.spacedBy(20.ydp(sy))
            ) {
                Text(title, fontSize = (36 * su).sp, color = Color.White)
                content()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismissClick) {
                        Text(dismissText, fontSize = (28 * su).sp, color = Color(0xFF8E8E93))
                    }
                    TextButton(onClick = onConfirm) {
                        Text(confirmText, fontSize = (28 * su).sp, color = Color(0xFF0A84FF))
                    }
                }
            }
        }
    }
}

class MainActivity : ComponentActivity(), MediaControllerCallback {

    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val app = application as BinkeMusicApp
        SongCache.setAppContext(app)
        val factory = MainViewModelFactory(app.apiService, app.musicRepository, app.musicPlayer)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        startPlaybackService()
        BinkeMediaCallbacks.callback = this

        setContent {
            MainScreen(viewModel = viewModel)
        }

        viewModel.loadHomeData()
    }

    override fun onDestroy() {
        BinkeMediaCallbacks.callback = null
        super.onDestroy()
    }

    override fun onMediaPlay() {
        if (::viewModel.isInitialized) viewModel.playPause()
    }

    override fun onMediaPause() {
        if (::viewModel.isInitialized) viewModel.playPause()
    }

    override fun onMediaNext() {
        if (::viewModel.isInitialized) viewModel.next()
    }

    override fun onMediaPrevious() {
        if (::viewModel.isInitialized) viewModel.previous()
    }

    override fun onMediaStop() {
        if (::viewModel.isInitialized) viewModel.pause()
    }

    private fun startPlaybackService() {
        val intent = Intent(this, PlaybackService::class.java)
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to start PlaybackService", e)
        }
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val currentTab by viewModel.currentTab.collectAsState()
    val currentPage by viewModel.currentPage.collectAsState()

    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val playMode by viewModel.playMode.collectAsState()
    val lyrics by viewModel.lyrics.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val recommendPlaylists by viewModel.recommendPlaylists.collectAsState()
    val bangPlaylists by viewModel.bangPlaylists.collectAsState()
    val isLoadingHome by viewModel.isLoadingHome.collectAsState()

    val favorites by viewModel.favorites.collectAsState()
    val history by viewModel.history.collectAsState()
    val customPlaylists by viewModel.customPlaylists.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val suggestions by viewModel.searchSuggestions.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    val showPlaylistDrawer by viewModel.showPlaylistDrawer.collectAsState()
    val drawerPlaylist by viewModel.drawerPlaylist.collectAsState()
    val drawerSongs by viewModel.drawerSongs.collectAsState()

    val showQueueSheet by viewModel.showQueueSheet.collectAsState()
    val queue by viewModel.playlist.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()

    val showPlaylistPicker by viewModel.showPlaylistPicker.collectAsState()
    val playlistPickerSong by viewModel.playlistPickerSong.collectAsState()

    val playbackError by viewModel.playbackError.collectAsState()
    val playbackDebugParams by viewModel.playbackDebugParams.collectAsState()

    val cfg = LocalConfiguration.current
    val sx = cfg.screenWidthDp / BASE_WIDTH_DP
    val sy = cfg.screenHeightDp / BASE_HEIGHT_DP
    val su = (sx + sy) / 2f
    val isPortrait = cfg.screenHeightDp > cfg.screenWidthDp

    val isFavorite = currentSong?.let { viewModel.isFavorite(it.id) } ?: false

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // 竖屏时搜索栏放最上面（可点击输入，不再跳转新页面）
            if (isPortrait) {
                var searchInput by remember { mutableStateOf("") }
                LaunchedEffect(searchQuery) {
                    if (searchQuery.isEmpty() && searchInput.isNotEmpty()) {
                        searchInput = ""
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(horizontal = 12.xdp(sx), vertical = 10.ydp(sy)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.sdp(su))
                            .background(Color(0xFF26262B), RoundedCornerShape(16.sdp(su)))
                            .padding(horizontal = 16.xdp(sx)),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "搜索",
                                tint = Color(0xFF8E8E93),
                                modifier = Modifier.size(44.sdp(su))
                            )
                            Spacer(modifier = Modifier.width(10.xdp(sx)))
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                BasicTextField(
                                    value = searchInput,
                                    onValueChange = {
                                        searchInput = it
                                        viewModel.updateSearchQuery(it)
                                    },
                                    textStyle = TextStyle(color = Color.White, fontSize = (29 * su).sp),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    decorationBox = { innerTextField ->
                                        if (searchInput.isEmpty()) {
                                            Text("搜索歌手、歌曲、专辑", color = Color(0xFF8E8E93), fontSize = (29 * su).sp)
                                        }
                                        innerTextField()
                                    }
                                )
                            }
                            if (searchInput.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.xdp(sx)))
                                IconButton(
                                    onClick = {
                                        searchInput = ""
                                        viewModel.updateSearchQuery("")
                                    },
                                    modifier = Modifier.size(24.sdp(su))
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "清除",
                                        tint = Color(0xFF8E8E93),
                                        modifier = Modifier.size(20.sdp(su))
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 横屏用顶部栏，竖屏不用（搜索栏已放上面）
            if (!isPortrait) {
                TopBar(
                    currentTab = currentTab,
                    onTabSelected = { viewModel.setTab(it) },
                    onSearchClick = { viewModel.navigateTo(Page.SEARCH) }
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(currentTab) {
                        detectHorizontalDragGestures(
                            onDragEnd = { }
                        ) { change, dragAmount ->
                            change.consume()
                            val threshold = 80f
                            when {
                                dragAmount > threshold && currentTab > 0 -> viewModel.setTab(currentTab - 1)
                                dragAmount < -threshold && currentTab < 2 -> viewModel.setTab(currentTab + 1)
                            }
                        }
                    }
            ) {
                when {
                    currentPage == Page.HOME -> HomeScreen(
                        recommendPlaylists = recommendPlaylists,
                        bangPlaylists = bangPlaylists,
                        isLoading = isLoadingHome,
                        onPlaylistClick = { viewModel.openPlaylistDetail(it) },
                        searchQuery = if (isPortrait) searchQuery else "",
                        searchResults = if (isPortrait) searchResults else emptyList(),
                        isSearching = if (isPortrait) isSearching else false,
                        onSongClick = { viewModel.playSong(it) }
                    )

                    currentPage == Page.MUSIC -> MusicScreen(
                        song = currentSong,
                        isPlaying = isPlaying,
                        currentPosition = currentPosition,
                        duration = duration,
                        playMode = playMode,
                        lyrics = lyrics,
                        isFavorite = isFavorite,
                        isLoading = isLoading,
                        onPlayPause = { viewModel.playPause() },
                        onPrevious = { viewModel.previous() },
                        onNext = { viewModel.next() },
                        onTogglePlayMode = { viewModel.togglePlayMode() },
                        onToggleFavorite = { currentSong?.let { viewModel.toggleFavorite(it) } },
                        onOpenQueue = { viewModel.openQueueSheet() },
                        onAddToPlaylist = { currentSong?.let { viewModel.showPlaylistPicker(it) } },
                        onSeek = { viewModel.seekTo(it) },
                        onLyricSeekToLine = { viewModel.seekToLyricIndex(it) }
                    )

                    currentPage == Page.MINE -> MineScreen(
                        favorites = favorites,
                        history = history,
                        customPlaylists = customPlaylists,
                        onPlayFavorites = { viewModel.playFavorites() },
                        onPlayHistory = { viewModel.playHistory() },
                        onPlayCustomPlaylist = { viewModel.playCustomPlaylist(it) },
                        onCreatePlaylist = { viewModel.createPlaylist(it) },
                        onDeletePlaylist = { viewModel.deletePlaylist(it) },
                        onRenamePlaylist = { id, name -> viewModel.renamePlaylist(id, name) }
                    )

                    currentPage == Page.SEARCH -> SearchScreen(
                        query = searchQuery,
                        searchResults = searchResults,
                        suggestions = suggestions,
                        searchHistory = searchHistory,
                        isSearching = isSearching,
                        onQueryChange = { viewModel.updateSearchQuery(it) },
                        onSearch = { viewModel.search(it) },
                        onSongClick = { viewModel.playSong(it) },
                        onBack = { viewModel.navigateTo(Page.HOME) }
                    )

                    currentPage == Page.PLAYLIST_DETAIL -> Unit
                }
            }

            // 竖屏底部导航
            if (isPortrait) {
                BottomNav(
                    currentTab = currentTab,
                    onTabSelected = { viewModel.setTab(it) }
                )
            }
        }

        if (showPlaylistDrawer) {
            PlaylistDrawer(
                playlist = drawerPlaylist,
                songs = drawerSongs,
                isVisible = showPlaylistDrawer,
                onDismiss = { viewModel.closePlaylistDrawer() },
                onPlayAll = { drawerPlaylist?.let { viewModel.playPlaylist(it) } },
                onSongClick = { index -> drawerPlaylist?.let { viewModel.playPlaylist(it, index) } }
            )
        }

        if (showQueueSheet) {
            QueueDialog(
                songs = queue,
                currentIndex = currentIndex,
                onDismiss = { viewModel.closeQueueSheet() },
                onPlaySong = { viewModel.playSongAt(it) },
                onRemoveSong = { viewModel.removeSongFromQueue(it) }
            )
        }

        if (showPlaylistPicker && playlistPickerSong != null) {
            AddToPlaylistDialog(
                songName = playlistPickerSong?.name.orEmpty(),
                songId = playlistPickerSong?.id.orEmpty(),
                playlists = customPlaylists,
                onDismiss = { viewModel.closePlaylistPicker() },
                onSelect = { viewModel.addCurrentSongToPlaylist(it) },
            )
        }

        if (!playbackError.isNullOrBlank()) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("播放异常") },
                text = {
                    val ctx = LocalContext.current
                    Column {
                        Text(
                            buildString {
                                append(playbackError ?: "未知错误")
                                if (!playbackDebugParams.isNullOrBlank()) {
                                    append("\n\n传给player的参数：\n")
                                    append(playbackDebugParams)
                                }
                            },
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val clip = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clip.setPrimaryClip(android.content.ClipData.newPlainText("debug", playbackError ?: ""))
                                android.widget.Toast.makeText(ctx, "已复制", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("复制日志")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearPlaybackError() }) {
                        Text("知道了")
                    }
                }
            )
        }
    }
}

@Composable
private fun QueueDialog(
    songs: List<Song>,
    currentIndex: Int,
    onDismiss: () -> Unit,
    onPlaySong: (Int) -> Unit,
    onRemoveSong: (Int) -> Unit
) {
    val cfg = LocalConfiguration.current
    val sx = cfg.screenWidthDp / BASE_WIDTH_DP
    val sy = cfg.screenHeightDp / BASE_HEIGHT_DP
    val su = (sx + sy) / 2f
    val isPortrait = cfg.screenHeightDp > cfg.screenWidthDp

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(currentIndex) {
        if (currentIndex in songs.indices) {
            listState.scrollToItem(currentIndex)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xAA000000))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(if (isPortrait) 0.95f else 0.5f)
                    .fillMaxHeight(if (isPortrait) 0.7f else 0.85f)
                    .clip(RoundedCornerShape(topStart = 24.sdp(su), topEnd = 24.sdp(su)))
                    .background(Color(0xFF171717))
                    .padding(20.sdp(su))
                    .clickable(enabled = false) {}
            ) {
                Text("当前歌曲列表", color = Color.White, fontSize = (48 * su).sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.ydp(sy)))
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(10.ydp(sy))
                ) {
                    itemsIndexed(songs) { index, song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.sdp(su)))
                                .background(if (index == currentIndex) Color(0xFF27273B) else Color(0xFF1F1F23))
                                .clickable { onPlaySong(index) }
                                .padding(14.sdp(su)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = if (index == currentIndex) Color(0xFF8B7DFF) else Color(0xFF8E8E93),
                                modifier = Modifier.size(52.sdp(su))
                            )
                            Spacer(modifier = Modifier.width(10.xdp(sx)))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(song.name, color = Color.White, fontSize = (32 * su).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(modifier = Modifier.height(2.ydp(sy)))
                                Text(song.artist, color = Color(0xFF8E8E93), fontSize = (26 * su).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(song.durationText, color = Color(0xFF8E8E93), fontSize = (26 * su).sp)
                            IconButton(
                                onClick = { onRemoveSong(index) },
                                modifier = Modifier.size(52.sdp(su))
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "删除",
                                    tint = Color(0xFF8E8E93),
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddToPlaylistDialog(
    songName: String,
    songId: String,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val cfg = LocalConfiguration.current
    val sx = cfg.screenWidthDp / BASE_WIDTH_DP
    val sy = cfg.screenHeightDp / BASE_HEIGHT_DP
    val su = (sx + sy) / 2f

    CompactDialog(
        title = "加入歌单",
        onDismissRequest = onDismiss,
        sx = sx,
        sy = sy,
        su = su,
        confirmText = "关闭",
        onConfirm = onDismiss,
        dismissText = ""
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.ydp(sy))) {
            Text("《$songName》", fontSize = (28 * su).sp, color = Color(0xFFBDBDBD))
            playlists.forEach { playlist ->
                val isInPlaylist = playlist.musicList.any { it.id == songId }
                Button(
                    onClick = { onSelect(playlist.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.ydp(sy)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2A2A31),
                        contentColor = Color.White
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 20.xdp(sx),
                        vertical = 8.ydp(sy)
                    ),
                    shape = RoundedCornerShape(16.sdp(su))
                ) {
                    Text(
                        playlist.name,
                        fontSize = (28 * su).sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        softWrap = false
                    )
                    if (isInPlaylist) {
                        Text("✓", fontSize = (34 * su).sp, color = Color(0xFF8B7DFF), modifier = Modifier.padding(start = 8.xdp(sx)))
                    }
                }
            }
            if (playlists.isEmpty()) {
                Text("暂无自定义歌单，请先到\u201c我的\u201d中新建。", color = Color(0xFF8E8E93), fontSize = (28 * su).sp)
            }
        }
    }
}
