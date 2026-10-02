package com.virexalo.editor.editor

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.virexalo.editor.model.EditorProject
import com.virexalo.editor.model.MediaKind
import com.virexalo.editor.model.TimelineClip
import kotlin.math.abs
import kotlin.math.max

/**
 * Phone-first multi-track timeline.
 * The primary MEDIA track is intentionally tall and easy to hit.
 * Secondary tracks collapse to thin lanes and expand when selected.
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

    // MEDIA is the main track. TEXT/OVERLAY/AUDIO stay compact until tapped.
    private val rowNames = arrayOf("MEDIA", "TEXT", "OVERLAY", "AUDIO")

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
        onTrimChanged?.invoke(start, end)
        invalidate()
    }

    fun setPosition(position: Long) {
        positionMs = position.coerceIn(0L, durationMs)
        invalidate()
    }

    private fun rowHeight(row: Int): Float {
        return if (row == activeRow) 94f else 30f
    }

    private fun rowTop(row: Int): Float {
        var y = 7f - verticalOffset
        for (i in 0 until row) y += rowHeight(i)
        return y
    }

    private fun clipsForRow(row: Int): List<TimelineClip> {
        val p = project ?: return emptyList()
        return when (row) {
            // Videos + images + GIFs are one continuous visual track.
            0 -> p.clips.filter {
                it.kind == MediaKind.VIDEO || it.kind == MediaKind.IMAGE || it.kind == MediaKind.GIF
            }
            // Text/overlay lanes are reserved for their real timeline items.
            1 -> emptyList()
            2 -> emptyList()
            3 -> p.clips.filter { it.kind == MediaKind.AUDIO }
            else -> emptyList()
        }
    }

    private val scale: Float
        get() = max(0.045f, 92f / 1000f)

    private fun xForTime(time: Long, row: Int): Float =
        72f + time * scale - horizontalOffsets[row]

    private fun timeForX(x: Float, row: Int): Long =
        ((x - 72f + horizontalOffsets[row]) / scale)
            .toLong()
            .coerceIn(0L, durationMs)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(17, 24, 43))

        for (row in 0..3) {
            val top = rowTop(row)
            val bottom = top + rowHeight(row) - 4f
            if (bottom < 0f || top > height) continue

            val active = row == activeRow
            paint.color = if (active) Color.rgb(35, 52, 84) else Color.rgb(27, 37, 61)
            canvas.drawRoundRect(5f, top, width - 5f, bottom, 9f, 9f, paint)

            // Track handle/label area stays fixed while the clip area scrolls horizontally.
            paint.color = when (row) {
                0 -> Color.rgb(74, 132, 214)
                1 -> Color.rgb(145, 108, 205)
                2 -> Color.rgb(220, 158, 55)
                else -> Color.rgb(54, 177, 143)
            }
            canvas.drawRoundRect(9f, top + 4f, 64f, bottom - 4f, 7f, 7f, paint)

            paint.color = Color.WHITE
            paint.textSize = if (active) 10.5f else 9f
            paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText(rowNames[row], 16f, top + (bottom - top) / 2f + 4f, paint)

            drawTrack(canvas, row, top, bottom)
        }

        // One playhead for the whole editing stack.
        val playX = xForTime(positionMs, 0)
        paint.color = Color.rgb(245, 92, 111)
        paint.strokeWidth = 2f
        canvas.drawLine(playX, 2f, playX, height - 2f, paint)
        canvas.drawCircle(playX, 7f, 5f, paint)
    }

    private fun drawTrack(canvas: Canvas, row: Int, top: Float, bottom: Float) {
        if (row == 1) {
            drawTextTrack(canvas, top, bottom)
            return
        }

        val clips = clipsForRow(row)
        clips.forEachIndexed { index, clip ->
            val left = xForTime(clip.startOnTimelineMs, row)
            val right = xForTime(clip.endOnTimelineMs, row)
            if (right < 66f || left > width) return@forEachIndexed

            val selected = project?.selectedClipId == clip.id
            paint.color = if (selected) Color.rgb(67, 126, 212) else Color.rgb(49, 73, 112)
            canvas.drawRoundRect(left, top + 6f, right, bottom - 6f, 7f, 7f, paint)

            if (thumb != null && row == 0) {
                val dst = RectF(left, top + 6f, right, bottom - 6f)
                canvas.drawBitmap(thumb!!, null, dst, paint)
                paint.color = Color.argb(if (selected) 45 else 80, 0, 0, 0)
                canvas.drawRoundRect(dst, 7f, 7f, paint)
            }

            paint.color = Color.WHITE
            paint.textSize = if (row == 0 && selected) 10f else 8f
            val label = when (clip.kind) {
                MediaKind.IMAGE -> "IMAGE"
                MediaKind.VIDEO -> "VIDEO"
                MediaKind.GIF -> "GIF"
                MediaKind.AUDIO -> "AUDIO"
                else -> "MEDIA"
            }
            canvas.drawText(label, left + 8f, top + (bottom - top) / 2f + 4f, paint)

            // Transition handle belongs between every pair of visual clips.
            if (row == 0 && index < clips.lastIndex) {
                val next = clips[index + 1]
                val transitionX = xForTime(next.startOnTimelineMs, row)
                if (transitionX in 66f..width.toFloat()) {
                    paint.color = Color.rgb(196, 181, 253)
                    canvas.drawCircle(transitionX, (top + bottom) / 2f, 11f, paint)
                    paint.color = Color.rgb(38, 29, 63)
                    paint.textSize = 13f
                    canvas.drawText("+", transitionX - 4f, (top + bottom) / 2f + 5f, paint)
                }
            }

            if (selected && row == activeRow) {
                paint.color = Color.WHITE
                canvas.drawRoundRect(left, top + 3f, left + 5f, bottom - 3f, 2f, 2f, paint)
                canvas.drawRoundRect(right - 5f, top + 3f, right, bottom - 3f, 2f, 2f, paint)
            }
        }
    }

    private var activeTextLabel = ""

    private fun drawTextTrack(canvas: Canvas, top: Float, bottom: Float) {
        val label = if (activeTextLabel.isBlank()) "TEXT" else activeTextLabel
        val left = xForTime(0L, 1)
        val right = xForTime(max(durationMs, 5000L), 1)
        paint.color = Color.rgb(82, 62, 126)
        canvas.drawRoundRect(left, top + 5f, right, bottom - 5f, 7f, 7f, paint)
        paint.color = Color.WHITE
        paint.textSize = 8.5f
        canvas.drawText(label, max(78f, left + 8f), top + (bottom - top) / 2f + 3f, paint)
    }

    fun setTextLabel(value: String) {
        activeTextLabel = value.take(24)
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = x
                downY = y
                lastX = x
                lastY = y

                dragRow = rowAt(y)
                activeRow = dragRow
                onRowSelected?.invoke(dragRow)

                dragClip = findClipAt(dragRow, x)
                dragging = when {
                    dragClip != null && dragRow != 1 &&
                        abs(x - xForTime(dragClip!!.startOnTimelineMs, dragRow)) < 18f ->
                        Drag.RESIZE_LEFT
                    dragClip != null && dragRow != 1 &&
                        abs(x - xForTime(dragClip!!.endOnTimelineMs, dragRow)) < 18f ->
                        Drag.RESIZE_RIGHT
                    dragClip != null && dragRow != 1 -> Drag.MOVE
                    abs(x - xForTime(positionMs, 0)) < 16f -> Drag.PLAYHEAD
                    else -> Drag.SCROLL_X
                }

                dragClip?.let { onClipSelected?.invoke(it.id) }
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = x - lastX
                val dy = y - lastY

                if (dragging == Drag.SCROLL_X || dragging == Drag.SCROLL_Y) {
                    if (abs(dy) > abs(dx) + 3f) {
                        dragging = Drag.SCROLL_Y
                        verticalOffset = (verticalOffset - dy).coerceIn(0f, max(0f, contentHeight() - height))
                    } else if (dragging != Drag.SCROLL_Y) {
                        dragging = Drag.SCROLL_X
                        horizontalOffsets[dragRow] =
                            (horizontalOffsets[dragRow] - dx).coerceAtLeast(0f)
                    }
                } else {
                    when (dragging) {
                        Drag.MOVE -> dragClip?.let {
                            onClipMoved?.invoke(it.id, (dx / scale).toLong())
                        }
                        Drag.RESIZE_LEFT -> dragClip?.let {
                            onClipResized?.invoke(it.id, (dx / scale).toLong(), 0L)
                        }
                        Drag.RESIZE_RIGHT -> dragClip?.let {
                            onClipResized?.invoke(it.id, 0L, (dx / scale).toLong())
                        }
                        Drag.PLAYHEAD -> {
                            positionMs = timeForX(x, 0)
                            onPositionChanged?.invoke(positionMs)
                        }
                        else -> Unit
                    }
                }

                lastX = x
                lastY = y
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (dragging == Drag.SCROLL_X && abs(x - downX) < 12f && abs(y - downY) < 12f) {
                    positionMs = timeForX(x, 0)
                    onPositionChanged?.invoke(positionMs)
                }
                dragging = Drag.NONE
                return true
            }
        }
        return true
    }

    private fun rowAt(y: Float): Int {
        var top = 7f - verticalOffset
        for (row in 0..3) {
            val bottom = top + rowHeight(row)
            if (y in top..bottom) return row
            top = bottom
        }
        return 0
    }

    private fun contentHeight(): Float {
        var total = 7f
        for (row in 0..3) total += rowHeight(row)
        return total
    }

    private fun findClipAt(row: Int, x: Float): TimelineClip? =
        clipsForRow(row).firstOrNull {
            x >= xForTime(it.startOnTimelineMs, row) &&
                x <= xForTime(it.endOnTimelineMs, row)
        }

    fun scrollToClip(row: Int, clip: TimelineClip) {
        val target = xForTime(clip.startOnTimelineMs, row)
        horizontalOffsets[row] =
            max(0f, horizontalOffsets[row] + target - 84f)
        invalidate()
    }
}
