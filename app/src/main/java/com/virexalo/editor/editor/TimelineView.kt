package com.virexalo.editor.editor

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max

class TimelineView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var durationMs = 1L
    private var positionMs = 0L
    private var startMs = 0L
    private var endMs = 1L
    private var thumb: Bitmap? = null
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

    fun setMediaThumbnail(bitmap: Bitmap?) { thumb = bitmap; invalidate() }
    fun setTrim(start: Long, end: Long) {
        startMs = start.coerceIn(0L, (durationMs - 1).coerceAtLeast(0L))
        endMs = end.coerceIn(startMs + 1L, durationMs)
        positionMs = positionMs.coerceIn(startMs, endMs)
        invalidate()
    }
    fun setPosition(position: Long) { positionMs = position.coerceIn(startMs, endMs); invalidate() }
    fun trimStart() = startMs
    fun trimEnd() = endMs

    private fun xForTime(t: Long): Float =
        12f + t * (width - 24f).coerceAtLeast(1f) / durationMs.toFloat()
    private fun timeForX(x: Float): Long =
        ((x - 12f) * durationMs / (width - 24f).coerceAtLeast(1f)).toLong().coerceIn(0L, durationMs)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        canvas.drawColor(Color.rgb(9, 11, 20))
        paint.color = Color.rgb(20, 24, 38)
        canvas.drawRoundRect(8f, 24f, w - 8f, h - 8f, 12f, 12f, paint)

        val left = xForTime(startMs); val right = xForTime(endMs)
        if (thumb != null) {
            val src = Rect(0, 0, thumb!!.width, thumb!!.height)
            val dst = RectF(left, 30f, right, h - 14f)
            canvas.drawBitmap(thumb!!, src, dst, paint)
        } else {
            paint.color = Color.rgb(38, 45, 70)
            canvas.drawRoundRect(left, 30f, right, h - 14f, 8f, 8f, paint)
        }
        paint.color = Color.argb(75, 0, 0, 0)
        canvas.drawRect(left, 30f, right, h - 14f, paint)

        paint.color = Color.rgb(124, 92, 255)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawRoundRect(left, 28f, right, h - 12f, 9f, 9f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.rgb(174, 158, 255)
        canvas.drawRoundRect(left - 5f, 20f, left + 5f, h - 10f, 4f, 4f, paint)
        canvas.drawRoundRect(right - 5f, 20f, right + 5f, h - 10f, 4f, 4f, paint)

        paint.color = Color.WHITE
        val x = xForTime(positionMs)
        canvas.drawRoundRect(x - 1.5f, 10f, x + 1.5f, h - 5f, 2f, 2f, paint)

        paint.textSize = 11f
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.color = Color.rgb(174, 182, 211)
        for (i in 0..5) {
            val t = durationMs * i / 5
            canvas.drawText(format(t), xForTime(t) + 2f, 18f, paint)
        }
    }

    private fun format(ms: Long): String = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val time = timeForX(event.x)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val threshold = (durationMs * .04f).toLong().coerceAtLeast(250L)
                dragMode = when {
                    abs(time - startMs) <= threshold -> DragMode.START
                    abs(time - endMs) <= threshold -> DragMode.END
                    else -> DragMode.PLAYHEAD
                }
                update(time); return true
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> { update(time); return true }
        }
        return true
    }

    private fun update(time: Long) {
        when (dragMode) {
            DragMode.START -> {
                startMs = time.coerceIn(0L, (endMs - 1).coerceAtLeast(0L))
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
