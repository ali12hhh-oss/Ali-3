package com.virexalo.editor.audio

data class AudioClip(
    val id: String,
    val uri: String,
    val sourceStartMs: Long = 0L,
    val sourceEndMs: Long? = null,
    val startOnTimelineMs: Long = 0L,
    val volume: Float = 1f,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L
) {
    val durationMs: Long get() = ((sourceEndMs ?: Long.MAX_VALUE) - sourceStartMs).coerceAtLeast(1L)
}
