package com.virexalo.editor.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class TrimRangeTest {
    @Test
    fun clampKeepsRangeInsideDuration() {
        val range = TrimRange(500L, 9000L).clamp(6000L)
        assertEquals(500L, range.startMs)
        assertEquals(6000L, range.endMs)
    }
}
