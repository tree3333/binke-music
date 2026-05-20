package com.binke.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.binke.music.data.model.Song

@Composable
fun SearchScreen(
    query: String,
    searchResults: List<Song>,
    suggestions: List<String>,
    searchHistory: List<String>,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onSongClick: (Song) -> Unit,
    onBack: () -> Unit
) {
    var inputText by remember { mutableStateOf(query) }

    LaunchedEffect(query) {
        inputText = query
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, "返回", modifier = Modifier.size(24.dp), tint = Color.White)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .background(Color(0xFF2A2A2A), RoundedCornerShape(28.dp))
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Search, "搜索", modifier = Modifier.size(24.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.width(12.dp))
                        BasicTextField(
                            value = inputText,
                            onValueChange = {
                                inputText = it
                                onQueryChange(it)
                            },
                            textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                if (inputText.isEmpty()) {
                                    Text("搜索歌手、歌曲名称、专辑", color = Color(0xFF77777C), fontSize = 18.sp)
                                }
                                innerTextField()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = { onSearch(inputText) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B5BFF)),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.width(180.dp)   // ← 原约60dp x3
                ) {
                    Text("搜      索", fontSize = 27.sp)   // ← 18x1.5，中间6空格
                }
            }

            when {
                isSearching -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }

                searchResults.isNotEmpty() -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(searchResults) { song ->
                            SearchResultItem(song = song, onClick = { onSongClick(song) })
                        }
                        item {
                            Text(
                                text = "共 ${searchResults.size} 条结果",
                                color = Color(0xFF8E8E93),
                                fontSize = 28.sp,           // ← 14x2
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }

                suggestions.isNotEmpty() && inputText.isNotEmpty() -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        items(suggestions) { suggestion ->
                            ListItem(
                                headlineContent = { Text(suggestion, color = Color.White, fontSize = 32.sp) },   // ← x2
                                leadingContent = {
                                    Icon(Icons.Filled.Search, null, tint = Color.Gray, modifier = Modifier.size(48.dp))   // ← x2
                                },
                                modifier = Modifier.clickable { onSearch(suggestion) }
                            )
                        }
                    }
                }

                searchHistory.isNotEmpty() -> {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("搜索历史", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)   // ← 18x2
                        Spacer(modifier = Modifier.height(16.dp))
                        searchHistory.filter { it.isNotBlank() }.forEach { history ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1D1D21))
                                    .clickable { onSearch(history) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.History, null, tint = Color.Gray, modifier = Modifier.size(40.dp))   // ← 20x2
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(history, color = Color.White, fontSize = 30.sp)   // ← 15x2
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                else -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("输入关键词搜索歌曲", color = Color.Gray, fontSize = 36.sp)   // ← 18x2
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultItem(song: Song, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1D1D21))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.pic.ifEmpty { "https://via.placeholder.com/100/171717/F1F1F1?text=BinKe" },
            contentDescription = null,
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.name,
                color = Color.White,
                fontSize = 36.sp,           // ← 18x2
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = song.artist,
                color = Color(0xFFBDBDBD),
                fontSize = 28.sp,           // ← 14x2
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${song.quality} · ${song.album.ifBlank { "未知专辑" }}",
                color = Color(0xFF8E8E93),
                fontSize = 26.sp,           // ← 13x2
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = song.durationText,
                color = Color(0xFFBDBDBD),
                fontSize = 28.sp            // ← 14x2
            )
        }
    }
}
