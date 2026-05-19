package com.binke.music.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.binke.music.data.model.Playlist

@Composable
fun HomeScreen(
    recommendPlaylists: List<Playlist>,
    bangPlaylists: List<Playlist>,
    isLoading: Boolean,
    onPlaylistClick: (Playlist) -> Unit
) {
    val allSections = buildList {
        if (recommendPlaylists.isNotEmpty()) add("每日推荐" to recommendPlaylists.take(6))
        if (bangPlaylists.isNotEmpty()) add("热门榜单" to bangPlaylists.take(8))
        if (recommendPlaylists.size > 6) add("推荐歌单" to recommendPlaylists.drop(6).take(8))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                allSections.forEach { (title, playlists) ->
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold
                            )
                            PlaylistGrid(playlists = playlists, onPlaylistClick = onPlaylistClick)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistGrid(playlists: List<Playlist>, onPlaylistClick: (Playlist) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        playlists.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                row.forEach { playlist ->
                    PlaylistCard(
                        playlist = playlist,
                        onClick = { onPlaylistClick(playlist) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(4 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF1B1B1F))
            .combinedClickable(onClick = onClick)
            .padding(10.dp)
    ) {
        AsyncImage(
            model = playlist.img.ifEmpty { "https://via.placeholder.com/300/171717/F1F1F1?text=BinKe" },
            contentDescription = playlist.name,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .height(180.dp),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = playlist.name,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = when {
                playlist.total > 0 -> "${playlist.total}首"
                playlist.listenCount.isNotBlank() -> "播放 ${playlist.listenCount}"
                else -> playlist.creator.ifBlank { "推荐歌单" }
            },
            color = Color(0xFF8E8E93),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
