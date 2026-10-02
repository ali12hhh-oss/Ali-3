package com.virexalo.editor.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max

class TimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var durationMs = 1L
    private var positionMs = 0L
    private var startMs = 0L
    private var endMs = 1L
    var onTrimChanged: ((Long, Long) -> Unit)? = null

    fun setTimeline(duration: Long, position: Long = 0L) {
        durationMs = max(1L, duration)
        positionMs = position.coerceIn(0L, durationMs)
        endMs = durationMs
        invalidate()
    }

    fun setPosition(position: Long) {
        positionMs = position.coerceIn(0L, durationMs)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        paint.color = 0xFF151B32.toInt()
        canvas.drawRoundRect(10f, 18f, w - 10f, h - 18f, 14f, 14f, paint)
        val scale = (w - 24f) / durationMs.toFloat()
        paint.color = 0xFF7C5CFF.toInt()
        canvas.drawRoundRect(
            12f + startMs * scale,
            28f,
            12f + endMs * scale,
            h - 28f,
            8f,
            8f,
            paint
        )
        paint.color = 0xFFFFFFFF.toInt()
        val x = 12f + positionMs * scale
        canvas.drawRect(x - 1.5f, 12f, x + 1.5f, h - 12f, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
            val scale = (width - 24f) / durationMs.toFloat()
            val p = ((event.x - 12f) / scale).toLong().coerceIn(startMs, endMs)
            positionMs = p
            invalidate()
            return true
        }
        return true
    }
}