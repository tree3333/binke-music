package com.binke.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.binke.music.data.model.Playlist
import com.binke.music.data.model.Song
import kotlin.math.roundToInt

private const val BASE_WIDTH_DP = 1920f
private const val BASE_HEIGHT_DP = 1080f

private fun Int.xdp(sx: Float): Dp = (this * sx).dp
private fun Int.ydp(sy: Float): Dp = (this * sy).dp
private fun Int.sdp(su: Float): Dp = (this * su).dp

@Composable
fun PlaylistDrawer(
    playlist: Playlist?,
    songs: List<Song>,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onPlayAll: () -> Unit,
    onSongClick: (Int) -> Unit
) {
    if (!isVisible || playlist == null) return

    val cfg = LocalConfiguration.current
    val sx = cfg.screenWidthDp / BASE_WIDTH_DP
    val sy = cfg.screenHeightDp / BASE_HEIGHT_DP
    val su = (sx + sy) / 2f
    val isPortrait = cfg.screenHeightDp > cfg.screenWidthDp

    var offsetY by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        if (isPortrait) {
            // 竖屏：从下往上弹出
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clickable(onClick = onDismiss)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.75f)
                        .offset { IntOffset(0, offsetY.roundToInt()) }
                        .clip(RoundedCornerShape(topStart = 24.sdp(su), topEnd = 24.sdp(su)))
                        .background(Color(0xFF171717))
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (offsetY > 120f * sy) {
                                        onDismiss()
                                    }
                                    offsetY = 0f
                                },
                                onVerticalDrag = { _, dragAmount ->
                                    offsetY = (offsetY + dragAmount).coerceAtLeast(0f)
                                }
                            )
                        }
                        .padding(20.sdp(su))
                ) {
                    PlaylistDrawerContent(
                        playlist = playlist,
                        songs = songs,
                        onDismiss = onDismiss,
                        onPlayAll = onPlayAll,
                        onSongClick = onSongClick,
                        sx = sx, sy = sy, su = su
                    )
                }
            }
        } else {
            // 横屏：从右往左弹出（保持原样）
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(onClick = onDismiss)
                )

                Box(
                    modifier = Modifier
                        .width(778.xdp(sx))
                        .fillMaxHeight()
                        .offset { IntOffset(offsetY.roundToInt(), 0) }
                        .background(Color(0xFF171717))
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (offsetY > 120f * sx) {
                                        onDismiss()
                                    }
                                    offsetY = 0f
                                },
                                onVerticalDrag = { _, dragAmount ->
                                    offsetY = (offsetY + dragAmount).coerceAtLeast(0f)
                                }
                            )
                        }
                        .padding(20.sdp(su))
                ) {
                    PlaylistDrawerContent(
                        playlist = playlist,
                        songs = songs,
                        onDismiss = onDismiss,
                        onPlayAll = onPlayAll,
                        onSongClick = onSongClick,
                        sx = sx, sy = sy, su = su
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistDrawerContent(
    playlist: Playlist,
    songs: List<Song>,
    onDismiss: () -> Unit,
    onPlayAll: () -> Unit,
    onSongClick: (Int) -> Unit,
    sx: Float, sy: Float, su: Float
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = playlist.img.ifEmpty { "https://via.placeholder.com/240/171717/F1F1F1?text=BinKe" },
                contentDescription = null,
                modifier = Modifier
                    .size((160 * 1.5f).toInt().sdp(su))
                    .clip(RoundedCornerShape(16.sdp(su))),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(18.xdp(sx)))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    color = Color.White,
                    fontSize = (52 * su * 0.7f).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.ydp(sy)))
                Text(
                    text = if (playlist.creator.isNotBlank()) "创建者：${playlist.creator}" else "${songs.size}首歌曲",
                    color = Color(0xFFBDBDBD),
                    fontSize = (32 * su).sp
                )
                if (playlist.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.ydp(sy)))
                    Text(
                        text = playlist.description,
                        color = Color(0xFF8E8E93),
                        fontSize = (28 * su).sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.ydp(sy)))

        Button(
            onClick = onPlayAll,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF6B5BFF),
                contentColor = Color.White
            ),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 20.xdp(sx),
                vertical = 6.ydp(sy)
            ),
            shape = RoundedCornerShape(36.sdp(su)),
            modifier = Modifier
                .fillMaxWidth()
                .height(((30 * su * 1.2f).toInt()).ydp(sy))
        ) {
            Icon(Icons.Filled.PlayArrow, null, Modifier.size(36.sdp(su)))
            Spacer(modifier = Modifier.width(12.xdp(sx)))
            Text("播放全部", fontSize = (30 * su).sp, color = Color.White)
        }

        Spacer(modifier = Modifier.height(14.ydp(sy)))
        Divider(color = Color(0xFF303036))
        Spacer(modifier = Modifier.height(10.ydp(sy)))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.ydp(sy))
        ) {
            itemsIndexed(songs) { index, song ->
                SongListItem(song = song, onClick = { onSongClick(index) }, sx = sx, sy = sy, su = su)
            }
        }
    }
}

@Composable
private fun SongListItem(song: Song, onClick: () -> Unit, sx: Float, sy: Float, su: Float) {
    val coverSize = 120.sdp(su)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.sdp(su)))
            .background(Color(0xFF1D1D21))
            .clickable(onClick = onClick)
            .padding(14.sdp(su)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.pic.ifEmpty { "https://via.placeholder.com/100/171717/F1F1F1?text=BinKe" },
            contentDescription = null,
            modifier = Modifier
                .size(coverSize)
                .clip(RoundedCornerShape(10.sdp(su))),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(16.xdp(sx)))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.name,
                color = Color.White,
                fontSize = (36 * su).sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.ydp(sy)))
            Text(
                text = song.artist,
                color = Color(0xFFBDBDBD),
                fontSize = (28 * su).sp,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.ydp(sy)))
            Text(
                text = "${song.quality} · ${song.album.ifBlank { "未知专辑" }}",
                color = Color(0xFF8E8E93),
                fontSize = (26 * su).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = song.durationText,
            color = Color(0xFFBDBDBD),
            fontSize = (28 * su).sp
        )
    }
}
