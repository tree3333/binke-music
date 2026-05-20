package com.binke.music.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.binke.music.data.model.LrcLine
import com.binke.music.data.model.PlayMode
import com.binke.music.data.model.Song
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MusicScreen(
    song: Song?,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    playMode: PlayMode,
    lyrics: List<LrcLine>,
    isFavorite: Boolean,
    isLoading: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTogglePlayMode: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onSeek: (Long) -> Unit,
    onLyricSeekToLine: (Int) -> Unit
) {
    var swipeOffset by remember { mutableFloatStateOf(0f) }
    var isSwiping by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .pointerInput(song?.id) {
                detectVerticalDragGestures(
                    onDragStart = { isSwiping = true },
                    onDragEnd = {
                        isSwiping = false
                        when {
                            swipeOffset < -120 -> onNext()
                            swipeOffset > 120 -> onPrevious()
                        }
                        swipeOffset = 0f
                    },
                    onDragCancel = {
                        isSwiping = false
                        swipeOffset = 0f
                    },
                    onVerticalDrag = { _, dragAmount -> swipeOffset += dragAmount }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .weight(1.2f)
                .fillMaxHeight()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF1A1A1A), Color(0xFF121212))
                    )
                )
                .padding(horizontal = 36.dp, vertical = 28.dp)
        ) {
            if (song == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无播放内容", color = Color.Gray, fontSize = 24.sp)
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model = song.pic.ifEmpty { "https://via.placeholder.com/600/171717/F1F1F1?text=BinKe" },
                        contentDescription = "专辑封面",
                        modifier = Modifier
                            .size(360.dp)
                            .clip(RoundedCornerShape(24.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.height(22.dp))

                    // 歌名品质歌手 + 右侧按钮：整体向中间对齐
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.name,
                                color = Color.White,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = song.quality,
                                    color = Color(0xFF9FA8FF),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = song.artist,
                                    color = Color(0xFFBDBDBD),
                                    fontSize = 27.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // 加入歌单（加号）+ 收藏，并排垂直居中
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onAddToPlaylist) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "加入歌单",
                                    tint = Color(0xFF9FA8FF),
                                    modifier = Modifier.size(204.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = onToggleFavorite) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = "收藏",
                                    tint = if (isFavorite) Color(0xFFFF4D67) else Color.White,
                                    modifier = Modifier.size(204.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 进度条向中间缩进，与控制按钮对齐
                    Slider(
                        value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                        onValueChange = { onSeek((it * duration).toLong()) },
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF7B6DFF),
                            activeTrackColor = Color(0xFF7B6DFF),
                            inactiveTrackColor = Color(0xFF404040)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatTime(currentPosition), color = Color(0xFF8E8E93), fontSize = 15.sp)
                        Text("剩余 ${formatRemain(duration, currentPosition)}", color = Color(0xFF8E8E93), fontSize = 15.sp)
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onTogglePlayMode) {
                            Icon(
                                imageVector = when (playMode) {
                                    PlayMode.LIST_LOOP -> Icons.Filled.Repeat
                                    PlayMode.SINGLE_LOOP -> Icons.Filled.RepeatOne
                                    PlayMode.SHUFFLE -> Icons.Filled.Shuffle
                                },
                                contentDescription = "循环模式",
                                tint = Color.White,
                                modifier = Modifier.size(136.dp)
                            )
                        }

                        IconButton(onClick = onPrevious) {
                            Icon(Icons.Filled.SkipPrevious, "上一首", modifier = Modifier.size(184.dp), tint = Color.White)
                        }

                        Box(
                            modifier = Modifier
                                .size(164.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF7B6DFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(68.dp), strokeWidth = 3.dp)
                            } else {
                                IconButton(onClick = onPlayPause) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = if (isPlaying) "暂停" else "播放",
                                        tint = Color.White,
                                        modifier = Modifier.size(126.dp)
                                    )
                                }
                            }
                        }

                        IconButton(onClick = onNext) {
                            Icon(Icons.Filled.SkipNext, "下一首", modifier = Modifier.size(184.dp), tint = Color.White)
                        }

                        IconButton(onClick = onOpenQueue) {
                            Icon(Icons.Filled.QueueMusic, "播放列表", modifier = Modifier.size(136.dp), tint = Color.White)
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color(0xFF171717))
                .padding(horizontal = 28.dp, vertical = 16.dp)
        ) {
            LyricsView(
                lyrics = lyrics,
                currentPosition = currentPosition,
                onLineClick = onLyricSeekToLine
            )

            if (isSwiping && abs(swipeOffset) > 50) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xAA000000))
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = if (swipeOffset < 0) "上滑切到下一首" else "下滑切到上一首",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LyricsView(
    lyrics: List<LrcLine>,
    currentPosition: Long,
    onLineClick: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    val currentSec = currentPosition / 1000f
    val currentLineIndex = lyrics.indexOfLast { it.time <= currentSec }.coerceAtLeast(0)

    LaunchedEffect(currentLineIndex, lyrics.size) {
        if (lyrics.isNotEmpty()) {
            listState.animateScrollToItem((currentLineIndex - 2).coerceAtLeast(0))
        }
    }

    if (lyrics.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无歌词", color = Color(0xFF6E6E73), fontSize = 44.sp)
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(36.dp)
    ) {
        itemsIndexed(lyrics) { index, line ->
            val active = index == currentLineIndex
            Text(
                text = line.text,
                color = if (active) Color.White else Color(0xFF9A9A9F),
                fontSize = if (active) 52.sp else 44.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                lineHeight = if (active) 72.sp else 60.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = { onLineClick(index) })
            )
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

private fun formatTime(millis: Long): String {
    val totalSec = (millis / 1000).coerceAtLeast(0)
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%02d:%02d", min, sec)
}

private fun formatRemain(duration: Long, currentPosition: Long): String {
    return formatTime((duration - currentPosition).coerceAtLeast(0))
}
