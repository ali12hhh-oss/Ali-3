package com.virexalo.editor.editor

import android.content.Context
import android.graphics.*
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
    private var lastSnappedMs = -1L
    var onTrimChanged: ((Long, Long) -> Unit)? = null
    var onPositionChanged: ((Long) -> Unit)? = null
    var onSnap: ((Long) -> Unit)? = null

    private enum class DragMode { PLAYHEAD, START, END }

    fun setTimeline(duration: Long, position: Long = 0L) {
        durationMs = max(1L, duration)
        positionMs = position.coerceIn(0L, durationMs)
        startMs = 0L
        endMs = durationMs
        invalidate()
    }

    fun setMediaThumbnail(bitmap: Bitmap?) {
        thumb = bitmap
        invalidate()
    }

    fun setTrim(start: Long, end: Long) {
        startMs = start.coerceIn(0L, (durationMs - 1).coerceAtLeast(0L))
        endMs = end.coerceIn(startMs + 1L, durationMs)
        positionMs = positionMs.coerceIn(startMs, endMs)
        invalidate()
    }

    fun setPosition(position: Long) {
        positionMs = snap(position.coerceIn(startMs, endMs))
        invalidate()
    }

    fun trimStart() = startMs
    fun trimEnd() = endMs

    private fun xForTime(t: Long): Float =
        14f + t * (width - 28f).coerceAtLeast(1f) / durationMs.toFloat()

    private fun timeForX(x: Float): Long =
        ((x - 14f) * durationMs / (width - 28f).coerceAtLeast(1f))
            .toLong().coerceIn(0L, durationMs)

    private fun snap(time: Long): Long {
        val threshold = max(250L, durationMs / 100L)
        val candidates = longArrayOf(0L, startMs, endMs, durationMs)
        val nearest = candidates.minByOrNull { abs(it - time) } ?: time
        return if (abs(nearest - time) <= threshold) {
            if (nearest != lastSnappedMs) {
                lastSnappedMs = nearest
                onSnap?.invoke(nearest)
            }
            nearest
        } else {
            lastSnappedMs = -1L
            time
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawColor(Color.rgb(9, 11, 20))

        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(18, 22, 35)
        canvas.drawRoundRect(8f, 22f, w - 8f, h - 8f, 12f, 12f, paint)

        val left = xForTime(startMs)
        val right = xForTime(endMs)
        val trackTop = 34f
        val trackBottom = h - 16f

        paint.color = Color.rgb(30, 35, 53)
        canvas.drawRoundRect(left, trackTop, right, trackBottom, 8f, 8f, paint)

        if (thumb != null) {
            val src = Rect(0, 0, thumb!!.width, thumb!!.height)
            val dst = RectF(left, trackTop, right, trackBottom)
            canvas.drawBitmap(thumb!!, src, dst, paint)
            paint.color = Color.argb(82, 0, 0, 0)
            canvas.drawRoundRect(left, trackTop, right, trackBottom, 8f, 8f, paint)
        }

        // Timeline divisions make the clip structure readable even before multiple clips are loaded.
        paint.color = Color.argb(100, 255, 255, 255)
        paint.strokeWidth = 1f
        for (i in 1 until 10) {
            val x = left + (right - left) * i / 10f
            canvas.drawLine(x, trackTop + 2f, x, trackBottom - 2f, paint)
        }

        paint.color = Color.rgb(124, 92, 255)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawRoundRect(left, trackTop - 2f, right, trackBottom + 2f, 9f, 9f, paint)
        paint.style = Paint.Style.FILL

        // Trim handles.
        paint.color = Color.rgb(174, 158, 255)
        canvas.drawRoundRect(left - 5f, 20f, left + 5f, h - 10f, 4f, 4f, paint)
        canvas.drawRoundRect(right - 5f, 20f, right + 5f, h - 10f, 4f, 4f, paint)

        // Time ruler.
        paint.color = Color.rgb(174, 182, 211)
        paint.textSize = 11f
        paint.typeface = Typeface.DEFAULT_BOLD
        for (i in 0..5) {
            val t = durationMs * i / 5
            val x = xForTime(t)
            canvas.drawText(format(t), (x + 2f).coerceAtMost(w - 42f), 16f, paint)
        }

        // Playhead.
        val playX = xForTime(positionMs)
        paint.color = Color.WHITE
        canvas.drawRoundRect(playX - 1.5f, 9f, playX + 1.5f, h - 5f, 2f, 2f, paint)
        canvas.drawCircle(playX, 9f, 4f, paint)
    }

    private fun format(ms: Long): String =
        "%d:%02d".format(ms / 60000, (ms / 1000) % 60)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val raw = timeForX(event.x)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val time = snap(raw)
                val threshold = max(250L, durationMs / 25L)
                dragMode = when {
                    abs(time - startMs) <= threshold -> DragMode.START
                    abs(time - endMs) <= threshold -> DragMode.END
                    else -> DragMode.PLAYHEAD
                }
                update(time)
                return true
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                update(snap(raw))
                if (event.actionMasked == MotionEvent.ACTION_UP) lastSnappedMs = -1L
                return true
            }
        }
        return true
    }

    private fun update(time: Long) {
        when (dragMode) {
            DragMode.START -> {
                startMs = time.coerceIn(0L, (endMs - 1L).coerceAtLeast(0L))
                positionMs = positionMs.coerceAtLeast(startMs)
                onTrimChanged?.invoke(startMs, endMs)
            }
            DragMode.END -> {
                endMs = time.coerceIn((startMs + 1L).coerceAtMost(durationMs), durationMs)
                positionMs = positionMs.coerceAtMost(endMs)
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
