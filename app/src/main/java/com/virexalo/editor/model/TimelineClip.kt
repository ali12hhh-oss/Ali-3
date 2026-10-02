package com.virexalo.editor.model

data class TimelineClip(
    val id: String,
    val uri: String,
    val kind: MediaKind,
    val sourceStartMs: Long = 0L,
    val sourceEndMs: Long? = null,
    val startOnTimelineMs: Long = 0L,
    val durationMs: Long
) {
    val endOnTimelineMs: Long get() = startOnTimelineMs + durationMs
}
