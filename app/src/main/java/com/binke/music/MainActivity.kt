package com.binke.music

import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import com.binke.music.data.model.Page
import com.binke.music.data.model.Playlist
import com.binke.music.data.model.Song
import com.binke.music.ui.MainViewModel
import com.binke.music.ui.MainViewModelFactory
import com.binke.music.ui.PlaylistDrawer
import com.binke.music.ui.components.TopBar
import com.binke.music.ui.screens.HomeScreen
import com.binke.music.ui.screens.MineScreen
import com.binke.music.ui.screens.MusicScreen
import com.binke.music.ui.screens.SearchScreen

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            enableEdgeToEdge()
            WindowCompat.setDecorFitsSystemWindows(window, false)
            hideSystemUI()
        } catch (e: Throwable) {
            // Android 8.1 不兼容 API，忽略
        }

        val app = application as BinkeMusicApp
        val factory = MainViewModelFactory(app.apiService, app.musicRepository, app.musicPlayer)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        setContent {
            MainScreen(viewModel = viewModel)
        }

        viewModel.loadHomeData()
    }

    private fun hideSystemUI() {
        window.insetsController?.let { controller ->
            controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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

    val isFavorite = currentSong?.let { viewModel.isFavorite(it.id) } ?: false

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                currentTab = currentTab,
                onTabSelected = { viewModel.setTab(it) },
                onSearchClick = { viewModel.navigateTo(Page.SEARCH) }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(currentTab) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                // 手势结束时不处理，在 onHorizontalDrag 中实时判断
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            // 右滑（dragAmount > 0）→ 切换到左边标签
                            // 左滑（dragAmount < 0）→ 切换到右边标签
                            val threshold = 80f
                            when {
                                dragAmount > threshold && currentTab > 0 -> {
                                    viewModel.setTab(currentTab - 1)
                                }
                                dragAmount < -threshold && currentTab < 2 -> {
                                    viewModel.setTab(currentTab + 1)
                                }
                            }
                        }
                    }
            ) {
                when (currentPage) {
                    Page.HOME -> HomeScreen(
                        recommendPlaylists = recommendPlaylists,
                        bangPlaylists = bangPlaylists,
                        isLoading = isLoadingHome,
                        onPlaylistClick = { viewModel.openPlaylistDetail(it) }
                    )

                    Page.MUSIC -> MusicScreen(
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

                    Page.MINE -> MineScreen(
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

                    Page.SEARCH -> SearchScreen(
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

                    Page.PLAYLIST_DETAIL -> Unit
                }
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
                playlists = customPlaylists,
                onDismiss = { viewModel.closePlaylistPicker() },
                onSelect = { viewModel.addCurrentSongToPlaylist(it) }
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
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(Color(0xFF171717))
                    .padding(20.dp)
                    .clickable(enabled = false) {}
            ) {
                Text("当前歌曲列表", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(songs) { index, song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (index == currentIndex) Color(0xFF27273B) else Color(0xFF1F1F23))
                                .clickable { onPlaySong(index) }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = if (index == currentIndex) Color(0xFF8B7DFF) else Color(0xFF8E8E93),
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(song.name, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(song.artist, color = Color(0xFF8E8E93), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(song.durationText, color = Color(0xFF8E8E93), fontSize = 13.sp)
                            IconButton(onClick = { onRemoveSong(index) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "删除", tint = Color(0xFF8E8E93))
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
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("加入歌曲列表") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("将《$songName》加入以下歌单")
                playlists.forEach { playlist ->
                    Button(
                        onClick = { onSelect(playlist.id) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A31))
                    ) {
                        Text(playlist.name)
                    }
                }
                if (playlists.isEmpty()) {
                    Text("暂无自定义歌单，请先到“我的”中新建。", color = Color(0xFF8E8E93))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
