package com.binke.music.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.binke.music.R
import com.binke.music.data.model.Playlist
import com.binke.music.data.model.Song
import kotlinx.coroutines.delay

private const val BASE_WIDTH_DP = 1920f
private const val BASE_HEIGHT_DP = 1080f

private fun Int.xdp(sx: Float): Dp = (this * sx).dp
private fun Int.ydp(sy: Float): Dp = (this * sy).dp
private fun Int.sdp(su: Float): Dp = (this * su).dp

@Composable
fun HomeScreen(
    recommendPlaylists: List<Playlist>,
    bangPlaylists: List<Playlist>,
    isLoading: Boolean,
    onPlaylistClick: (Playlist) -> Unit,
    // 搜索相关（竖屏内联搜索时传入）
    searchQuery: String = "",
    searchResults: List<Song> = emptyList(),
    isSearching: Boolean = false,
    onSongClick: (Song) -> Unit = {}
) {
    val cfg = LocalConfiguration.current
    val sx = cfg.screenWidthDp / BASE_WIDTH_DP
    val sy = cfg.screenHeightDp / BASE_HEIGHT_DP
    val su = (sx + sy) / 2f

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
                contentPadding = PaddingValues(24.xdp(sx), 24.ydp(sy)),
                verticalArrangement = Arrangement.spacedBy(28.ydp(sy))
            ) {
                // 轮播图（最顶部）
                item {
                    BannerCarousel(sx = sx, sy = sy, su = su)
                }

                // 搜索结果区块（竖屏内联搜索时显示）
                if (searchResults.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.ydp(sy))) {
                            searchResults.forEach { song ->
                                SearchResultItemHome(song = song, onClick = { onSongClick(song) }, sx = sx, sy = sy, su = su)
                            }
                            Text(
                                text = "共 ${searchResults.size} 条结果",
                                color = Color(0xFF8E8E93),
                                fontSize = (28 * su).sp,
                                modifier = Modifier.padding(vertical = 8.ydp(sy))
                            )
                        }
                    }
                }

                allSections.forEach { (title, playlists) ->
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(16.ydp(sy))) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                fontSize = (52 * su).sp,
                                fontWeight = FontWeight.Bold
                            )
                            PlaylistGrid(
                                playlists = playlists,
                                onPlaylistClick = onPlaylistClick,
                                sx = sx,
                                sy = sy,
                                su = su
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistGrid(
    playlists: List<Playlist>,
    onPlaylistClick: (Playlist) -> Unit,
    sx: Float,
    sy: Float,
    su: Float
) {
    val cfg = LocalConfiguration.current
    val isPortrait = cfg.screenHeightDp > cfg.screenWidthDp
    val columns = if (isPortrait) 3 else 4

    Column(verticalArrangement = Arrangement.spacedBy(18.ydp(sy))) {
        playlists.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.xdp(sx))) {
                row.forEach { playlist ->
                    PlaylistCard(
                        playlist = playlist,
                        onClick = { onPlaylistClick(playlist) },
                        modifier = Modifier.weight(1f),
                        sy = sy,
                        su = su
                    )
                }
                repeat(columns - row.size) {
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
    sy: Float,
    su: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.sdp(su)))
            .background(Color(0xFF1B1B1F))
            .combinedClickable(onClick = onClick)
            .padding(10.sdp(su))
    ) {
        AsyncImage(
            model = playlist.img.ifEmpty { "https://via.placeholder.com/300/171717/F1F1F1?text=BinKe" },
            contentDescription = playlist.name,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.sdp(su)))
                .aspectRatio(1f),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(10.ydp(sy)))
        Text(
            text = playlist.name,
            color = Color.White,
            fontSize = (32 * su).sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(6.ydp(sy)))
        Text(
            text = when {
                playlist.total > 0 -> "${playlist.total}首"
                playlist.listenCount.isNotBlank() -> "播放 ${playlist.listenCount}"
                else -> playlist.creator.ifBlank { "推荐歌单" }
            },
            color = Color(0xFF8E8E93),
            fontSize = (24 * su).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SearchResultItemHome(song: Song, onClick: () -> Unit, sx: Float, sy: Float, su: Float) {
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

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = song.durationText,
                color = Color(0xFFBDBDBD),
                fontSize = (28 * su).sp
            )
        }
    }
}

@Composable
private fun BannerCarousel(sx: Float, sy: Float, su: Float) {
    val bannerResIds = listOf(R.drawable.banner_1, R.drawable.banner_2)
    var currentIndex by remember { mutableIntStateOf(0) }

    // 3秒自动切换
    LaunchedEffect(currentIndex) {
        delay(3000)
        currentIndex = (currentIndex + 1) % bannerResIds.size
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1080f / 720f)
            .clip(RoundedCornerShape(12.sdp(su))),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = bannerResIds[currentIndex]),
            contentDescription = "轮播图${currentIndex + 1}",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // 指示器（底部居中）
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.ydp(sy)),
            horizontalArrangement = Arrangement.spacedBy(8.xdp(sx))
        ) {
            bannerResIds.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .size(width = if (index == currentIndex) 20.xdp(sx) else 8.xdp(sx), height = 8.ydp(sy))
                        .clip(CircleShape)
                        .background(
                            if (index == currentIndex) Color(0xFF7B6DFF) else Color(0x80FFFFFF)
                        )
                )
            }
        }
    }
}
