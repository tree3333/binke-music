package com.binke.music.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.binke.music.data.model.Playlist
import com.binke.music.data.model.Song
import com.binke.music.player.SongCache
import com.binke.music.ui.util.BASE_HEIGHT_DP
import com.binke.music.ui.util.BASE_WIDTH_DP
import com.binke.music.ui.util.sdp
import com.binke.music.ui.util.xdp
import com.binke.music.ui.util.ydp
import kotlin.math.roundToInt

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
    onPlayAll: () -> Unit,
    onSongClick: (Int) -> Unit,
    sx: Float, sy: Float, su: Float
) {
    // Banner 放大 1.5 倍（160 → 240）
    val bannerSize = (160 * 1.5f).toInt().sdp(su)
    // 标题缩小 0.7 倍
    val titleFontSize = (52 * 0.7f * su).sp

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = playlist.img.ifEmpty { "https://via.placeholder.com/240/171717/F1F1F1?text=BinKe" },
                contentDescription = null,
                modifier = Modifier
                    .size(bannerSize)
                    .clip(RoundedCornerShape(16.sdp(su))),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(18.xdp(sx)))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    color = Color.White,
                    fontSize = titleFontSize,
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

        // 播放全部：比字多 20%，按钮拉长与歌单列表同宽
        Button(
            onClick = onPlayAll,
            contentPadding = PaddingValues(vertical = (30 * 0.1f * su).dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF6B5BFF),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape((30 * su).dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.PlayArrow, null, Modifier.size((30 * su).dp))
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
            model = song.pic.ifEmpty { "https://via.placeholder.com/120/1D1D21/8E8E93?text=♪" },
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

/**
 * 统一封面组件：优先从 SongCache 内存 Bitmap 直接渲染（命中则无网络延迟），
 * 未命中则走 AsyncImage（触发预加载）。与播放地址/歌词共用同一滑动窗口缓存体系。
 * 检测两层缓存：1) preloadedCoverUrls 集合  2) Coil memoryCache
 */
@Composable
private fun CachedCoverImage(
    song: Song,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val songCache = remember { SongCache.getInstance() }
    val ctx = songCache?.let { SongCache.getAppContext() }

    // 命中判断：URL 已在 preloadedCoverUrls 且 Bitmap 已解码，或 Coil memoryCache 已有 bitmap
    val cachedBitmap = song.id.let { songCache?.getCoverBitmap(it) }
    val isCached = song.pic.isNotBlank() && (
        (songCache?.hasCover(song) == true && cachedBitmap != null) ||
        (ctx != null && SongCache.getImageLoader()?.memoryCache?.let { mc ->
            val req = coil.request.ImageRequest.Builder(ctx).data(song.pic).build()
            req.memoryCacheKey?.let { mc.get(it) != null } ?: false
        } == true)
    )

    if (isCached) {
        if (cachedBitmap != null) {
            Image(
                bitmap = cachedBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = modifier,
                contentScale = contentScale
            )
        } else {
            // URL 在 Coil 内存缓存中，直接读取（无需网络）
            AsyncImage(
                model = song.pic,
                contentDescription = null,
                modifier = modifier,
                contentScale = contentScale
            )
        }
    } else {
        AsyncImage(
            model = song.pic.ifEmpty { "https://via.placeholder.com/100/171717/F1F1F1?text=BinKe" },
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale
        )
    }
}
