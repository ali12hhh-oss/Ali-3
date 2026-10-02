package com.virexalo.editor.subtitles

import org.junit.Assert.assertEquals
import org.junit.Test

class SrtParserTest {
    @Test
    fun parsesCueTimingAndText() {
        val result = SrtParser.parse("1\n00:00:01,000 --> 00:00:02,500\nHello")
        assertEquals(1, result.size)
        assertEquals(1000L, result[0].startMs)
        assertEquals(2500L, result[0].endMs)
        assertEquals("Hello", result[0].text)
    }
}
