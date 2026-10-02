package com.virexalo.editor.audio

object AudioTimeline {
    fun trim(clip: AudioClip, selection: AudioSelection): AudioClip {
        val duration = (clip.sourceEndMs ?: Long.MAX_VALUE) - clip.sourceStartMs
        val start = selection.startMs.coerceIn(0L, (duration - 1L).coerceAtLeast(0L))
        val end = selection.endMs.coerceIn(start + 1L, duration)
        return clip.copy(
            sourceStartMs = clip.sourceStartMs + start,
            sourceEndMs = clip.sourceStartMs + end
        )
    }
}
