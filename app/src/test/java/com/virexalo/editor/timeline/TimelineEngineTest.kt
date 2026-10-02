package com.virexalo.editor.timeline

import com.virexalo.editor.model.EditorProject
import com.virexalo.editor.model.MediaKind
import com.virexalo.editor.model.TimelineClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineEngineTest {
    private fun project(): EditorProject {
        val clip = TimelineClip("a", "content://video", MediaKind.VIDEO, durationMs = 10_000L)
        return EditorProject("p", listOf(clip), "a")
    }

    @Test
    fun trimChangesSourceWindow() {
        val result = TimelineEngine.trim(project(), "a", 2_000L, 7_000L)
        assertEquals(5_000L, result.clips.single().durationMs)
        assertEquals(2_000L, result.clips.single().sourceStartMs)
    }

    @Test
    fun splitCreatesTwoClips() {
        val result = TimelineEngine.split(project(), "a", 4_000L)
        assertEquals(2, result.clips.size)
        assertEquals(4_000L, result.clips[0].durationMs)
        assertEquals(6_000L, result.clips[1].durationMs)
        assertTrue(result.selectedClipId == result.clips[1].id)
    }
}
