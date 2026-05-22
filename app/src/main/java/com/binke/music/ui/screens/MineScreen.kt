package com.binke.music.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.binke.music.data.model.Playlist
import com.binke.music.data.model.Song

@Composable
fun MineScreen(
    favorites: List<Song>,
    history: List<Song>,
    customPlaylists: List<Playlist>,
    onPlayFavorites: () -> Unit,
    onPlayHistory: () -> Unit,
    onPlayCustomPlaylist: (Playlist) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onRenamePlaylist: (String, String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var deleteMode by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Playlist?>(null) }
    var renameText by remember { mutableStateOf("") }

    val tiles = buildList {
        add(Playlist("favorites", "我的收藏", "", favorites.size))
        add(Playlist("history", "历史播放", "", history.size))
        addAll(customPlaylists)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(24.dp)
    ) {
        LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(tiles) { playlist ->
                    PlaylistTile(
                        playlist = playlist,
                        isSystem = playlist.id == "favorites" || playlist.id == "history",
                        deleteMode = deleteMode,
                        onClick = {
                            when (playlist.id) {
                                "favorites" -> onPlayFavorites()
                                "history" -> onPlayHistory()
                                else -> onPlayCustomPlaylist(playlist)
                            }
                        },
                        onLongPress = {
                            if (playlist.id != "favorites" && playlist.id != "history") {
                                deleteMode = true
                            }
                        },
                        onDelete = { onDeletePlaylist(playlist.id) },
                        onRename = {
                            if (playlist.id != "favorites" && playlist.id != "history") {
                                renameTarget = playlist
                                renameText = playlist.name
                            }
                        }
                    )
                }
                item {
                    AddPlaylistTile { showCreateDialog = true }
                }
            }

        if (showCreateDialog) {
            Dialog(onDismissRequest = { showCreateDialog = false }) {
                Surface(
                    modifier = Modifier.size(560.dp, 320.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF1C1C1E)
                ) {
                    Column(
                        modifier = Modifier.padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(28.dp)
                    ) {
                        Text("新建歌单", fontSize = 40.sp, color = Color.White)
                        OutlinedTextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            placeholder = { Text("歌单名称", fontSize = 32.sp) },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 32.sp),
                            modifier = Modifier.fillMaxWidth().height(80.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { showCreateDialog = false }) {
                                Text("取消", fontSize = 28.sp, color = Color(0xFF8E8E93))
                            }
                            TextButton(
                                onClick = {
                                    if (newPlaylistName.isNotBlank()) {
                                        onCreatePlaylist(newPlaylistName)
                                        newPlaylistName = ""
                                        showCreateDialog = false
                                    }
                                }
                            ) {
                                Text("创建", fontSize = 28.sp, color = Color(0xFF0A84FF))
                            }
                        }
                    }
                }
            }
        }

        if (renameTarget != null) {
            AlertDialog(
                onDismissRequest = { renameTarget = null },
                title = { Text("重命名歌单") },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val target = renameTarget
                            if (target != null && renameText.isNotBlank()) {
                                onRenamePlaylist(target.id, renameText)
                            }
                            renameTarget = null
                        }
                    ) { Text("保存") }
                },
                dismissButton = {
                    TextButton(onClick = { renameTarget = null }) { Text("取消") }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistTile(
    playlist: Playlist,
    isSystem: Boolean,
    deleteMode: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit
) {
    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF1B1B1F))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongPress,
                    onDoubleClick = {
                        if (!isSystem) onRename()
                    }
                )
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF2A2A31)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (playlist.id) {
                        "favorites" -> Icons.Filled.Favorite
                        "history" -> Icons.Filled.History
                        else -> Icons.Filled.MusicNote
                    },
                    contentDescription = null,
                    tint = Color(0xFF7B6DFF),
                    modifier = Modifier.size(144.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = playlist.name,
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${playlist.total}首",
                color = Color(0xFF8E8E93),
                fontSize = 26.sp
            )
        }

        if (deleteMode && !isSystem) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(Color.Red)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "删除", tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AddPlaylistTile(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF1B1B1F))
            .combinedClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "新建歌单",
                tint = Color(0xFF7B6DFF),
                modifier = Modifier.size(140.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("新增歌单", color = Color.White, fontSize = 32.sp)
        }
    }
}
