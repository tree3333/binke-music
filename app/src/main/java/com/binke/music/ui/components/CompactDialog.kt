package com.binke.music.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.binke.music.ui.util.sdp
import com.binke.music.ui.util.xdp
import com.binke.music.ui.util.ydp

/**
 * 通用紧凑型对话框：标题 + 自定义内容 + 取消/确认两按钮。
 * 宽度按屏幕方向自适应：竖屏 85% 屏宽，横屏固定 620.xdp(sx)。
 *
 * 旧实现：MainActivity 和 MineScreen 各写一份 private fun CompactDialog。
 * 抽到这里统一维护，行为完全一致。
 */
@Composable
internal fun CompactDialog(
    title: String,
    onDismissRequest: () -> Unit,
    sx: Float,
    sy: Float,
    su: Float,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String = "取消",
    onDismissClick: () -> Unit = onDismissRequest,
    content: @Composable ColumnScope.() -> Unit
) {
    val cfg = LocalConfiguration.current
    val isPortrait = cfg.screenHeightDp > cfg.screenWidthDp
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier.width(if (isPortrait) cfg.screenWidthDp.dp * 0.85f else 620.xdp(sx)),
            shape = RoundedCornerShape(24.sdp(su)),
            color = Color(0xFF1C1C1E)
        ) {
            Column(
                modifier = Modifier.padding(36.sdp(su)),
                verticalArrangement = Arrangement.spacedBy(20.ydp(sy))
            ) {
                Text(title, fontSize = (36 * su).sp, color = Color.White)
                content()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismissClick) {
                        Text(dismissText, fontSize = (28 * su).sp, color = Color(0xFF8E8E93))
                    }
                    TextButton(onClick = onConfirm) {
                        Text(confirmText, fontSize = (28 * su).sp, color = Color(0xFF0A84FF))
                    }
                }
            }
        }
    }
}
