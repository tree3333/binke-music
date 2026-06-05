package com.binke.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import com.binke.music.ui.theme.CoverColorPredictor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val BASE_WIDTH_DP = 1920f
private const val BASE_HEIGHT_DP = 1080f

private fun Int.xdp(sx: Float): Dp = (this * sx).dp
private fun Int.ydp(sy: Float): Dp = (this * sy).dp
private fun Int.sdp(su: Float): Dp = (this * su).dp

@Composable
fun TopBar(
    currentTab: Int,
    onTabSelected: (Int) -> Unit,
    onSearchClick: () -> Unit,
    coverColors: CoverColorPredictor.ColorTriple
) {
    val cfg = LocalConfiguration.current
    val sx = cfg.screenWidthDp / BASE_WIDTH_DP
    val sy = cfg.screenHeightDp / BASE_HEIGHT_DP
    val su = (sx + sy) / 2f

    val isMusic = currentTab == 1
    // 音乐 tab：氛围背景 + 搜索框用 nl；其他 tab 保持原色
    val barBg = if (isMusic) coverColors.bg else Color(0xFF161616)
    val searchBg = if (isMusic) coverColors.nl else Color(0xFF26262B)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.ydp(sy))
            .background(barBg)
            .padding(horizontal = 40.xdp(sx)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .offset(x = 209.xdp(sx)),
            horizontalArrangement = Arrangement.Center
        ) {
            TabItem("推荐", currentTab == 0, su = su) { onTabSelected(0) }
            Spacer(modifier = Modifier.width(42.xdp(sx)))
            TabItem("音乐", currentTab == 1, su = su) { onTabSelected(1) }
            Spacer(modifier = Modifier.width(42.xdp(sx)))
            TabItem("我的", currentTab == 2, su = su) { onTabSelected(2) }
        }

        Box(
            modifier = Modifier
                .width(420.xdp(sx))
                .height(58.ydp(sy))
                .background(searchBg, RoundedCornerShape(30.sdp(su)))
                .clickable(onClick = onSearchClick),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.xdp(sx)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "搜索",
                    tint = Color(0xFF8E8E93),
                    modifier = Modifier.size(39.sdp(su))
                )
                Spacer(modifier = Modifier.width(12.xdp(sx)))
                Text(
                    text = "搜索歌手、歌曲名称",
                    color = Color(0xFF8E8E93),
                    fontSize = (27 * su).sp
                )
            }
        }
    }
}

@Composable
private fun TabItem(text: String, selected: Boolean, su: Float, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (selected) Color.White else Color(0xFF7B7B80),
        fontSize = if (selected) (60 * su).sp else (48 * su).sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier.clickable(onClick = onClick)
    )
}
