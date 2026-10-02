package com.virexalo.editor.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
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
    private var dragMode = DragMode.PLAYHEAD
    var onTrimChanged: ((Long, Long) -> Unit)? = null
    var onPositionChanged: ((Long) -> Unit)? = null

    private enum class DragMode { PLAYHEAD, START, END }

    fun setTimeline(duration: Long, position: Long = 0L) {
        durationMs = max(1L, duration)
        positionMs = position.coerceIn(0L, durationMs)
        startMs = 0L
        endMs = durationMs
        invalidate()
    }

    fun setTrim(start: Long, end: Long) {
        startMs = start.coerceIn(0L, durationMs - 1L)
        endMs = end.coerceIn(startMs + 1L, durationMs)
        positionMs = positionMs.coerceIn(startMs, endMs)
        invalidate()
    }

    fun setPosition(position: Long) {
        positionMs = position.coerceIn(startMs, endMs)
        invalidate()
    }

    fun trimStart(): Long = startMs
    fun trimEnd(): Long = endMs

    private fun timeForX(x: Float): Long {
        val scale = (width - 24f).coerceAtLeast(1f) / durationMs.toFloat()
        return ((x - 12f) / scale).toLong().coerceIn(0L, durationMs)
    }

    private fun xForTime(time: Long): Float =
        12f + time * (width - 24f).coerceAtLeast(1f) / durationMs.toFloat()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        paint.color = 0xFF151B32.toInt()
        canvas.drawRoundRect(10f, 18f, w - 10f, h - 18f, 14f, 14f, paint)

        paint.color = 0xFF7C5CFF.toInt()
        canvas.drawRoundRect(xForTime(startMs), 28f, xForTime(endMs), h - 28f, 8f, 8f, paint)

        paint.color = 0xFFB8A8FF.toInt()
        canvas.drawRect(xForTime(startMs) - 5f, 20f, xForTime(startMs) + 5f, h - 20f, paint)
        paint.color = 0xFFB8A8FF.toInt()
        canvas.drawRect(xForTime(endMs) - 5f, 20f, xForTime(endMs) + 5f, h - 20f, paint)

        paint.color = 0xFFFFFFFF.toInt()
        val x = xForTime(positionMs)
        canvas.drawRect(x - 1.5f, 12f, x + 1.5f, h - 12f, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val time = timeForX(event.x)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val threshold = (durationMs * 0.035f).toLong().coerceAtLeast(250L)
                dragMode = when {
                    abs(time - startMs) <= threshold -> DragMode.START
                    abs(time - endMs) <= threshold -> DragMode.END
                    else -> DragMode.PLAYHEAD
                }
                update(time)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                update(time)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                update(time)
                return true
            }
        }
        return true
    }

    private fun update(time: Long) {
        when (dragMode) {
            DragMode.START -> {
                startMs = time.coerceIn(0L, (endMs - 1L).coerceAtLeast(0L))
                if (positionMs < startMs) positionMs = startMs
                onTrimChanged?.invoke(startMs, endMs)
            }
            DragMode.END -> {
                endMs = time.coerceIn(startMs + 1L, durationMs)
                if (positionMs > endMs) positionMs = endMs
                onTrimChanged?.invoke(startMs, endMs)
            }
            DragMode.PLAYHEAD -> {
                positionMs = time.coerceIn(startMs, endMs)
                onPositionChanged?.invoke(positionMs)
            }
        }
        invalidate()
    }
}
