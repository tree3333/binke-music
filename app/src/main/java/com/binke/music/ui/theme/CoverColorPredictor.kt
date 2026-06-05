package com.binke.music.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

/**
 * 封面颜色预测器 — 7 个 int8 TFLite ensemble
 * 输入: 96x96 RGB bitmap (0..1 fp32)
 * 输出: 9 维向量 (bg.pl.nl 各 RGB, sigmoid 后 0..1)
 * 公式: pred = (sum_6 cnn5l_se + 0.5 * cnn5l_big_se) / 6.5
 */
class CoverColorPredictor(private val context: Context) {

    data class ColorTriple(val bg: Color, val pl: Color, val nl: Color)

    companion object {
        private const val TAG = "CoverColorPredictor"
        private const val INPUT_SIZE = 96
        private const val NUM_5L = 6
        private const val BIG_WEIGHT = 0.5f
        private const val ENSEMBLE_DIV = NUM_5L + BIG_WEIGHT  // 6.5
        private val SEEDS_5L = listOf("s2", "s7", "s9", "s11", "s13", "s2024")
    }

    private var interpreters5l: Array<Interpreter?> = arrayOfNulls(NUM_5L)
    private var interpreterBig: Interpreter? = null
    private var initialized = false
    private val lock = Any()

    /** 加载 7 个 tflite (首次调用自动初始化) */
    private fun ensureInit() {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            try {
                for (i in SEEDS_5L.indices) {
                    val name = "models/cnn5l_se_${SEEDS_5L[i]}.int8.tflite"
                    val f = copyAssetIfNeeded(name)
                    interpreters5l[i] = Interpreter(f, Interpreter.Options().apply {
                        setNumThreads(2)
                    })
                }
                val bigFile = copyAssetIfNeeded("models/cnn5l_big_se_s2024.int8.tflite")
                interpreterBig = Interpreter(bigFile, Interpreter.Options().apply {
                    setNumThreads(2)
                })
                initialized = true
                Log.d(TAG, "✓ 7 个 int8 tflite 加载完成")
            } catch (e: Exception) {
                Log.e(TAG, "tflite 加载失败", e)
                throw e
            }
        }
    }

    /** asset 复制到 cacheDir (TFLite 1.x 限制) */
    private fun copyAssetIfNeeded(assetName: String): File {
        val out = File(context.cacheDir, assetName.substringAfterLast('/'))
        if (out.exists() && out.length() > 0) return out
        context.assets.open(assetName).use { input ->
            FileOutputStream(out).use { output -> input.copyTo(output) }
        }
        return out
    }

    /** bitmap (任意尺寸) → 96x96 RGB float32 ByteBuffer */
    private fun bitmapToBuffer(bmp: Bitmap): ByteBuffer {
        val scaled = Bitmap.createScaledBitmap(bmp, INPUT_SIZE, INPUT_SIZE, true)
        val buf = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4)
        buf.order(ByteOrder.nativeOrder())
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (px in pixels) {
            val r = ((px shr 16) and 0xFF) / 255.0f
            val g = ((px shr 8) and 0xFF) / 255.0f
            val b = (px and 0xFF) / 255.0f
            buf.putFloat(r)
            buf.putFloat(g)
            buf.putFloat(b)
        }
        buf.rewind()
        if (scaled !== bmp) scaled.recycle()
        return buf
    }

    /** 单张 (1,96,96,3) fp32 → (1,9) fp32 */
    private fun runOne(itp: Interpreter, input: ByteBuffer): FloatArray {
        val output = Array(1) { FloatArray(9) }
        itp.run(input, output)
        return output[0]
    }

    /** 同步推理 (必须在协程 IO 线程) */
    suspend fun predict(bitmap: Bitmap): ColorTriple = withContext(Dispatchers.IO) {
        ensureInit()
        val buf = bitmapToBuffer(bitmap)

        // 6 个 cnn5l_se
        val sum5l = FloatArray(9)
        for (i in 0 until NUM_5L) {
            val p = runOne(interpreters5l[i]!!, buf)
            for (k in 0..8) sum5l[k] += p[k]
        }
        // 1 个 cnn5l_big_se
        val big = runOne(interpreterBig!!, buf)

        // ensemble: (sum_6 + 0.5 * big) / 6.5
        val out = FloatArray(9)
        for (k in 0..8) {
            out[k] = (sum5l[k] + BIG_WEIGHT * big[k]) / ENSEMBLE_DIV
        }
        buf.clear()
        toColorTriple(out)
    }

    /** 9 维向量 → Compose ColorTriple
     *  bg = out[0..2]  氛围背景 (HSV 暗 30% 防止太亮刺眼)
     *  pl = out[3..5]  当前歌词 (高亮)
     *  nl = out[6..8]  非当前歌词
     */
    private fun toColorTriple(v: FloatArray): ColorTriple {
        val bg = darkenIfVivid(rgbToColor(v[0], v[1], v[2]), 0.7f)
        val pl = rgbToColor(v[3], v[4], v[5])
        val nl = rgbToColor(v[6], v[7], v[8])
        return ColorTriple(bg = bg, pl = pl, nl = nl)
    }

    /** RGB (0..1) → Compose Color */
    private fun rgbToColor(r: Float, g: Float, b: Float): Color {
        return Color(
            red = r.coerceIn(0f, 1f),
            green = g.coerceIn(0f, 1f),
            blue = b.coerceIn(0f, 1f),
            alpha = 1f
        )
    }

    /** 鲜艳色 (sat>0.5 且 v>0.5) 暗化到 factor (默认 0.7 = 30% 暗) */
    private fun darkenIfVivid(c: Color, factor: Float): Color {
        val r = c.red; val g = c.green; val b = c.blue
        val maxC = max(max(r, g), b)
        val minC = min(min(r, g), b)
        val v = maxC
        val s = if (maxC == 0f) 0f else (maxC - minC) / maxC
        if (s > 0.5f && v > 0.5f) {
            return Color(
                red = r * factor,
                green = g * factor,
                blue = b * factor,
                alpha = 1f
            )
        }
        return c
    }

    /** 解码 bytes → Bitmap (96x96 缩放内部处理) */
    suspend fun decodeBitmap(bytes: ByteArray): Bitmap? = withContext(Dispatchers.IO) {
        try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "decode 失败", e)
            null
        }
    }

    fun close() {
        synchronized(lock) {
            for (i in interpreters5l.indices) {
                interpreters5l[i]?.close()
                interpreters5l[i] = null
            }
            interpreterBig?.close()
            interpreterBig = null
            initialized = false
        }
    }
}
