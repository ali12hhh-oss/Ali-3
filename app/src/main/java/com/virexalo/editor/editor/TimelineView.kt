package com.virexalo.editor.editor

import android.content.Context
import android.graphics.*
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.virexalo.editor.model.EditorProject
import com.virexalo.editor.model.MediaKind
import com.virexalo.editor.model.TimelineClip
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Multi-track editor timeline.
 * Video/Image/Audio/Text rows can be scrolled vertically; each row has its own
 * horizontal scroll position. The selected row expands, while the others become compact.
 */
class TimelineView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var durationMs = 1L
    private var positionMs = 0L
    private var project: EditorProject? = null
    private var thumb: Bitmap? = null
    private var activeRow = 0
    private var verticalOffset = 0f
    private val horizontalOffsets = FloatArray(4)
    private var dragging = Drag.NONE
    private var dragRow = 0
    private var dragClip: TimelineClip? = null
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f

    var onTrimChanged: ((Long, Long) -> Unit)? = null
    var onPositionChanged: ((Long) -> Unit)? = null
    var onSnap: ((Long) -> Unit)? = null
    var onClipSelected: ((String) -> Unit)? = null
    var onClipMoved: ((String, Long) -> Unit)? = null
    var onClipResized: ((String, Long, Long) -> Unit)? = null
    var onRowSelected: ((Int) -> Unit)? = null

    private enum class Drag { NONE, PLAYHEAD, MOVE, RESIZE_LEFT, RESIZE_RIGHT, SCROLL_X, SCROLL_Y }
    private val rowNames = arrayOf("VIDEO", "TEXT", "IMAGE", "AUDIO")

    fun setTimeline(duration: Long, position: Long = 0L) {
        durationMs = max(1L, duration)
        positionMs = position.coerceIn(0L, durationMs)
        invalidate()
    }

    fun setProject(value: EditorProject?) {
        project = value
        durationMs = max(1L, value?.clips?.maxOfOrNull { it.endOnTimelineMs } ?: durationMs)
        invalidate()
    }

    fun setMediaThumbnail(bitmap: Bitmap?) {
        thumb = bitmap
        invalidate()
    }

    fun setTrim(start: Long, end: Long) {
        positionMs = positionMs.coerceIn(start, end)
        invalidate()
        onTrimChanged?.invoke(start, end)
    }

    fun setPosition(position: Long) {
        positionMs = position.coerceIn(0L, durationMs)
        invalidate()
    }

    fun trimStart() = 0L
    fun trimEnd() = durationMs

    private fun rowHeight(row: Int): Float = if (row == activeRow) 82f else 38f
    private fun rowTop(row: Int): Float {
        var y = 8f - verticalOffset
        for (i in 0 until row) y += rowHeight(i)
        return y
    }

    private fun kindForRow(row: Int): MediaKind? = when (row) {
        0 -> MediaKind.VIDEO
        2 -> MediaKind.IMAGE
        3 -> MediaKind.AUDIO
        else -> null
    }

    private fun clipsForRow(row: Int): List<TimelineClip> {
        val p = project ?: return emptyList()
        return when (row) {
            0 -> p.clips.filter { it.kind == MediaKind.VIDEO }
            2 -> p.clips.filter { it.kind == MediaKind.IMAGE || it.kind == MediaKind.GIF }
            3 -> p.clips.filter { it.kind == MediaKind.AUDIO }
            else -> emptyList()
        }
    }

    private val scale: Float get() = max(0.035f, 90f / 1000f)
    private fun xForTime(time: Long, row: Int): Float =
        72f + time * scale - horizontalOffsets[row]
    private fun timeForX(x: Float, row: Int): Long =
        ((x - 72f + horizontalOffsets[row]) / scale).toLong().coerceIn(0L, durationMs)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(9, 11, 20))
        val h = height.toFloat()
        val contentBottom = h - 4f

        for (row in 0..3) {
            val top = rowTop(row)
            val bottom = top + rowHeight(row) - 4f
            if (bottom < 0 || top > contentBottom) continue

            paint.color = if (row == activeRow) Color.rgb(28, 39, 66) else Color.rgb(18, 22, 35)
            canvas.drawRoundRect(6f, top, width - 6f, bottom, 8f, 8f, paint)

            paint.color = when (row) {
                0 -> Color.rgb(96, 165, 250)
                1 -> Color.rgb(167, 139, 250)
                2 -> Color.rgb(251, 191, 36)
                else -> Color.rgb(52, 211, 153)
            }
            canvas.drawRoundRect(10f, top + 5f, 64f, bottom - 5f, 7f, 7f, paint)
            paint.color = Color.WHITE
            paint.textSize = 10f
            paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText(rowNames[row], 17f, top + (bottom - top) / 2f + 4f, paint)

            if (row == 1) {
                drawTextTrack(canvas, top, bottom)
            } else {
                drawMediaTrack(canvas, row, top, bottom)
            }
        }

        // Playhead spans every visible track.
        val playX = xForTime(positionMs, activeRow)
        paint.color = Color.rgb(248, 113, 113)
        paint.strokeWidth = 2f
        canvas.drawLine(playX, 3f, playX, h - 3f, paint)
        canvas.drawCircle(playX, 7f, 5f, paint)
    }

    private fun drawMediaTrack(canvas: Canvas, row: Int, top: Float, bottom: Float) {
        val clips = clipsForRow(row)
        for ((index, clip) in clips.withIndex()) {
            val left = xForTime(clip.startOnTimelineMs, row)
            val right = xForTime(clip.endOnTimelineMs, row)
            if (right < 68f || left > width) continue
            paint.color = if (project?.selectedClipId == clip.id)
                Color.rgb(58, 111, 190) else Color.rgb(45, 61, 91)
            canvas.drawRoundRect(left, top + 6f, right, bottom - 6f, 7f, 7f, paint)

            if (row == 0 && thumb != null) {
                val dst = RectF(left, top + 6f, right, bottom - 6f)
                canvas.drawBitmap(thumb!!, null, dst, paint)
                paint.color = Color.argb(75, 0, 0, 0)
                canvas.drawRoundRect(dst, 7f, 7f, paint)
            }

            paint.color = Color.WHITE
            paint.textSize = 10f
            canvas.drawText(if (clip.kind == MediaKind.AUDIO) "AUDIO" else "MEDIA",
                left + 8f, top + (bottom - top) / 2f + 4f, paint)

            if (index < clips.lastIndex) {
                val next = clips[index + 1]
                val transitionX = xForTime(next.startOnTimelineMs, row)
                paint.color = Color.rgb(196, 181, 253)
                canvas.drawCircle(transitionX, (top + bottom) / 2f, 10f, paint)
                paint.color = Color.rgb(35, 30, 60)
                paint.textSize = 11f
                canvas.drawText("↔", transitionX - 6f, (top + bottom) / 2f + 4f, paint)
            }

            if (project?.selectedClipId == clip.id && row == activeRow) {
                paint.color = Color.WHITE
                canvas.drawRect(left, top + 3f, left + 5f, bottom - 3f, paint)
                canvas.drawRect(right - 5f, top + 3f, right, bottom - 3f, paint)
            }
        }
    }

    private fun drawTextTrack(canvas: Canvas, top: Float, bottom: Float) {
        val text = if (activeTextLabel.isBlank()) "TEXT" else activeTextLabel
        val left = xForTime(0L, 1)
        val right = xForTime(max(durationMs, 5000L), 1)
        paint.color = Color.rgb(96, 76, 150)
        canvas.drawRoundRect(left, top + 6f, right, bottom - 6f, 7f, 7f, paint)
        paint.color = Color.WHITE
        paint.textSize = 10f
        canvas.drawText(text, max(78f, left + 8f), top + (bottom - top) / 2f + 4f, paint)
    }

    private var activeTextLabel = ""
    fun setTextLabel(value: String) {
        activeTextLabel = value.take(24)
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = x; downY = y; lastX = x; lastY = y
                val row = rowAt(y)
                activeRow = row
                onRowSelected?.invoke(row)
                val clip = findClipAt(row, x)
                dragRow = row
                dragClip = clip
                dragging = when {
                    clip != null && row != 1 && abs(x - xForTime(clip.startOnTimelineMs, row)) < 18f -> Drag.RESIZE_LEFT
                    clip != null && row != 1 && abs(x - xForTime(clip.endOnTimelineMs, row)) < 18f -> Drag.RESIZE_RIGHT
                    clip != null && row != 1 -> Drag.MOVE
                    abs(x - xForTime(positionMs, row)) < 16f -> Drag.PLAYHEAD
                    else -> Drag.SCROLL_X
                }
                clip?.let { onClipSelected?.invoke(it.id) }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = x - lastX
                val dy = y - lastY
                when (dragging) {
                    Drag.SCROLL_X -> horizontalOffsets[dragRow] = max(0f, horizontalOffsets[dragRow] - dx)
                    Drag.SCROLL_Y -> verticalOffset = max(0f, verticalOffset - dy)
                    Drag.MOVE -> dragClip?.let {
                        val delta = (dx / scale).toLong()
                        onClipMoved?.invoke(it.id, delta)
                    }
                    Drag.RESIZE_LEFT -> dragClip?.let {
                        val delta = (dx / scale).toLong()
                        onClipResized?.invoke(it.id, delta, 0L)
                    }
                    Drag.RESIZE_RIGHT -> dragClip?.let {
                        val delta = (dx / scale).toLong()
                        onClipResized?.invoke(it.id, 0L, delta)
                    }
                    Drag.PLAYHEAD -> {
                        positionMs = timeForX(x, dragRow)
                        onPositionChanged?.invoke(positionMs)
                    }
                    else -> {
                        if (abs(dy) > abs(dx)) dragging = Drag.SCROLL_Y
                    }
                }
                lastX = x; lastY = y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (dragging == Drag.SCROLL_X && abs(x - downX) < 12f && abs(y - downY) < 12f) {
                    positionMs = timeForX(x, activeRow)
                    onPositionChanged?.invoke(positionMs)
                }
                dragging = Drag.NONE
                return true
            }
        }
        return true
    }

    private fun rowAt(y: Float): Int {
        var top = 8f - verticalOffset
        for (row in 0..3) {
            val bottom = top + rowHeight(row)
            if (y in top..bottom) return row
            top = bottom
        }
        return 0
    }

    private fun findClipAt(row: Int, x: Float): TimelineClip? =
        clipsForRow(row).firstOrNull {
            x >= xForTime(it.startOnTimelineMs, row) && x <= xForTime(it.endOnTimelineMs, row)
        }

    fun scrollToClip(row: Int, clip: TimelineClip) {
        val target = xForTime(clip.startOnTimelineMs, row)
        horizontalOffsets[row] = max(0f, horizontalOffsets[row] + target - 84f)
        invalidate()
    }
}
