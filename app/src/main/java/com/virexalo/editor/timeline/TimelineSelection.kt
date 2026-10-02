package com.virexalo.editor.timeline

data class TimelineSelection(
    val clipId: String?,
    val startMs: Long = 0L,
    val endMs: Long = 0L
) {
    val hasClip: Boolean get() = clipId != null
}
