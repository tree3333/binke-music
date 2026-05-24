package com.binke.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val BASE_WIDTH_DP = 1920f
private const val BASE_HEIGHT_DP = 1080f

private fun Int.xdp(sx: Float): Dp = (this * sx).dp
private fun Int.ydp(sy: Float): Dp = (this * sy).dp
private fun Int.sdp(su: Float): Dp = (this * su).dp

private data class NavItem(val label: String, val icon: ImageVector)

private val navItems = listOf(
    NavItem("推荐", Icons.Default.Home),
    NavItem("音乐", Icons.Default.MusicNote),
    NavItem("我的", Icons.Default.Person)
)

@Composable
fun BottomNav(
    currentTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val cfg = LocalConfiguration.current
    val sx = cfg.screenWidthDp / BASE_WIDTH_DP
    val sy = cfg.screenHeightDp / BASE_HEIGHT_DP
    val su = (sx + sy) / 2f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.ydp(sy))
            .background(Color(0xFF161616)),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        navItems.forEachIndexed { index, item ->
            val selected = currentTab == index
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onTabSelected(index) }
                    .padding(horizontal = 24.xdp(sx)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.ydp(sy))
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = if (selected) Color(0xFF7B6DFF) else Color(0xFF8E8E93),
                    modifier = Modifier.size((80 * 0.8f).toInt().sdp(su))
                )
                Text(
                    text = item.label,
                    color = if (selected) Color(0xFF7B6DFF) else Color(0xFF8E8E93),
                    fontSize = (40 * su * 0.8f).sp
                )
            }
        }
    }
}
