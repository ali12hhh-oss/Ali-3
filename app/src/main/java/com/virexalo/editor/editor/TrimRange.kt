package com.virexalo.editor.editor

data class TrimRange(
    val startMs: Long,
    val endMs: Long
) {
    init { require(startMs >= 0L && endMs > startMs) }
    fun clamp(durationMs: Long): TrimRange {
        val end = endMs.coerceAtMost(durationMs)
        val start = startMs.coerceIn(0L, (end - 1L).coerceAtLeast(0L))
        return TrimRange(start, end)
    }
}
