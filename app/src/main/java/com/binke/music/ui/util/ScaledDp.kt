package com.binke.music.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 屏幕尺寸自适应工具。
 * 设计基准为 1920×1080 设备（车机横屏 160dpi）。
 * sx / sy 分别为当前屏宽/高与基准的比值，su 取两者平均用于方形元素。
 *
 * 旧实现是每个 Composable 文件顶部各写一份私有 const + 扩展函数。
 * 抽到这里统一管理，避免 sx/sy/su 公式在 8 个文件里飘。
 */
internal const val BASE_WIDTH_DP = 1920f
internal const val BASE_HEIGHT_DP = 1080f

internal fun Int.xdp(sx: Float): Dp = (this * sx).dp
internal fun Int.ydp(sy: Float): Dp = (this * sy).dp
internal fun Int.sdp(su: Float): Dp = (this * su).dp

@Immutable
data class ScaledUnits(
    val sx: Float,
    val sy: Float,
    val su: Float,
    val isPortrait: Boolean
) {
    companion object {
        /** 横屏基准下 sx≈sy≈su≈1.0；竖屏 sx 会小很多、sy 接近 1.0 */
        fun of(widthDp: Int, heightDp: Int): ScaledUnits {
            val sx = widthDp / BASE_WIDTH_DP
            val sy = heightDp / BASE_HEIGHT_DP
            return ScaledUnits(
                sx = sx,
                sy = sy,
                su = (sx + sy) / 2f,
                isPortrait = heightDp > widthDp
            )
        }
    }
}

@Composable
fun rememberScaledUnits(): ScaledUnits {
    val cfg = LocalConfiguration.current
    return ScaledUnits.of(cfg.screenWidthDp, cfg.screenHeightDp)
}
