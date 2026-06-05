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
import com.binke.music.ui.util.BASE_HEIGHT_DP
import com.binke.music.ui.util.BASE_WIDTH_DP
import com.binke.music.ui.util.sdp
import com.binke.music.ui.util.xdp
import com.binke.music.ui.util.ydp
import androidx.compose.ui.unit.sp

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
    // 音乐 tab：氛围背景 + 搜索框用氛围色叠 30% 灰半透；其他 tab 保持原色
    val barBg = if (isMusic) coverColors.bg else Color(0xFF161616)
    val searchBg = if (isMusic) coverColors.bg else Color(0xFF26262B)
    val searchOverlay = if (isMusic) Color(0x4D808080) else null
    // 音乐 tab 时：选中=pl (高亮), 未选中=nl；其他 tab 时：恢复原色
    val selectedColor = if (isMusic) coverColors.pl else Color.White
    val unselectedColor = if (isMusic) coverColors.nl else Color(0xFF7B7B80)

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
            TabItem("推荐", currentTab == 0, su = su, selectedColor = selectedColor, unselectedColor = unselectedColor) { onTabSelected(0) }
            Spacer(modifier = Modifier.width(42.xdp(sx)))
            TabItem("音乐", currentTab == 1, su = su, selectedColor = selectedColor, unselectedColor = unselectedColor) { onTabSelected(1) }
            Spacer(modifier = Modifier.width(42.xdp(sx)))
            TabItem("我的", currentTab == 2, su = su, selectedColor = selectedColor, unselectedColor = unselectedColor) { onTabSelected(2) }
        }

        Box(
            modifier = Modifier
                .width(420.xdp(sx))
                .height(58.ydp(sy))
                .background(searchBg, RoundedCornerShape(30.sdp(su)))
                .clickable(onClick = onSearchClick),
            contentAlignment = Alignment.CenterStart
        ) {
            if (searchOverlay != null) {
                // 氛围背景上叠 30% 灰度半透明
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(searchOverlay, RoundedCornerShape(30.sdp(su)))
                )
            }
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
private fun TabItem(
    text: String,
    selected: Boolean,
    su: Float,
    selectedColor: Color,
    unselectedColor: Color,
    onClick: () -> Unit
) {
    Text(
        text = text,
        color = if (selected) selectedColor else unselectedColor,
        fontSize = if (selected) (60 * su).sp else (48 * su).sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier.clickable(onClick = onClick)
    )
}
