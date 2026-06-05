package com.binke.music.ui.util

import android.content.res.Resources
import android.util.TypedValue

/**
 * 屏幕尺寸自适应工具（纯 Kotlin 版，minSdk 19 兼容）。
 *
 * 设计基准为 1920×1080 设备（车机横屏 160dpi）。
 * sx / sy 分别为当前屏宽/高与基准的比值，su 取两者平均用于方形元素。
 *
 * 用法（在 Fragment/View 里）：
 *   val sf = ScaledDpHelper.computeScaleFactors(resources)
 *   val pad = 24.sdp(sf.su)   // 缩放后转 px
 *   val w   = 480.xdp(sf.sx)
 *   val h   = 270.ydp(sf.sy)
 *
 * 旧 Compose 实现里 `ScaledUnits` 还带 `isPortrait` 字段，本版 XML + View
 * 场景下用不到（旋转由 Configuration 决定），所以只保留 sx/sy/su。
 */
internal const val BASE_WIDTH_DP = 1920f
internal const val BASE_HEIGHT_DP = 1080f

/**
 * 缩放因子 (横屏基准下 sx≈sy≈su≈1.0；竖屏 sx 会小很多、sy 接近 1.0)。
 */
data class ScaleFactors(
    val sx: Float,
    val sy: Float,
    val su: Float
)

/**
 * 计算当前 Resources 所在屏幕的缩放因子。
 *
 * 用 [Resources.getDisplayMetrics] 拿真实 px，再除以 density 转 dp，
 * 保证在 160dpi (mdpi) / 240dpi (hdpi) / 320dpi (xhdpi) 等不同密度下结果一致。
 *
 * @param resources 调用方的 Resources（Fragment/View.getResources() 或 context.resources）
 */
object ScaledDpHelper {
    fun computeScaleFactors(resources: Resources): ScaleFactors {
        val dm = resources.displayMetrics
        val widthDp = dm.widthPixels / dm.density
        val heightDp = dm.heightPixels / dm.density
        val sx = widthDp / BASE_WIDTH_DP
        val sy = heightDp / BASE_HEIGHT_DP
        return ScaleFactors(
            sx = sx,
            sy = sy,
            su = (sx + sy) / 2f
        )
    }
}

/**
 * dp 缩放后转 px。
 *
 * 用 [TypedValue.applyDimension] 处理密度：传入系统级 DisplayMetrics
 * （[Resources.getSystem] 是 framework 提供的单例，含设备真实 density）。
 * 这样既不需要 Composable LocalConfiguration，也不需要调用方多传 dm 参数，
 * 仍然能得到正确的物理像素值。
 */
fun Int.sdp(su: Float): Int =
    TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this * su,
        Resources.getSystem().displayMetrics
    ).toInt()

fun Int.xdp(sx: Float): Int =
    TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this * sx,
        Resources.getSystem().displayMetrics
    ).toInt()

fun Int.ydp(sy: Float): Int =
    TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this * sy,
        Resources.getSystem().displayMetrics
    ).toInt()
