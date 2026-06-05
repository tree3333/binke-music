package com.binke.music.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.binke.music.data.model.LrcLine

/**
 * 歌词显示 - 自定义 View（替代 Compose LyricsView）
 * - 当前行: pl (highlight) 色
 * - 其他行: nl (normal) 色
 * - 点击歌词触发回调
 */
class LyricsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var lyrics: List<LrcLine> = emptyList()
    private var currentIndex: Int = -1
    private var currentPositionMs: Long = 0L
    private var plColor: Int = 0xFFFFFFFF.toInt()
    private var nlColor: Int = 0xFF8E8E93.toInt()
    private var onLineClick: ((LrcLine) -> Unit)? = null

    private val plPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 56f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val nlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 44f
        textAlign = Paint.Align.CENTER
        alpha = 180
    }

    private val lineHeight = 32f.dp
    private val centerY get() = height / 2f

    fun setLyrics(newLyrics: List<LrcLine>) {
        lyrics = newLyrics
        invalidate()
    }

    fun setCurrentPosition(positionMs: Long) {
        if (positionMs == currentPositionMs) return
        currentPositionMs = positionMs
        // 找当前应该高亮的行
        val newIndex = findCurrentIndex(positionMs)
        if (newIndex != currentIndex) {
            currentIndex = newIndex
        }
        invalidate()
    }

    fun setColors(pl: Int, nl: Int) {
        plColor = pl
        nlColor = nl
        invalidate()
    }

    fun setOnLineClickListener(listener: (LrcLine) -> Unit) {
        onLineClick = listener
    }

    private fun findCurrentIndex(positionMs: Long): Int {
        if (lyrics.isEmpty()) return -1
        for (i in lyrics.indices.reversed()) {
            if ((lyrics[i].time * 1000L) <= positionMs) return i
        }
        return -1
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (lyrics.isEmpty()) {
            nlPaint.color = nlColor
            canvas.drawText("暂无歌词", width / 2f, centerY, nlPaint)
            return
        }

        plPaint.color = plColor
        nlPaint.color = nlColor

        // 当前行画在 centerY 位置
        val centerIdx = currentIndex.coerceAtLeast(0)
        // 画当前行
        if (centerIdx < lyrics.size) {
            canvas.drawText(lyrics[centerIdx].text, width / 2f, centerY, plPaint)
        }
        // 画上面
        for (i in centerIdx - 1 downTo 0) {
            val y = centerY - (centerIdx - i) * lineHeight
            if (y < 0) break
            canvas.drawText(lyrics[i].text, width / 2f, y, nlPaint)
        }
        // 画下面
        for (i in centerIdx + 1 until lyrics.size) {
            val y = centerY + (i - centerIdx) * lineHeight
            if (y > height) break
            canvas.drawText(lyrics[i].text, width / 2f, y, nlPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP && lyrics.isNotEmpty()) {
            val y = event.y
            val lineOffset = ((y - centerY) / lineHeight).toInt()
            val target = (currentIndex.coerceAtLeast(0) + lineOffset)
                .coerceIn(0, lyrics.size - 1)
            onLineClick?.invoke(lyrics[target])
            performClick()
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()

    private val Float.dp: Float
        get() = this * resources.displayMetrics.density
}
