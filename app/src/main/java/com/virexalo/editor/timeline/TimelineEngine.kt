package com.virexalo.editor.timeline

import com.virexalo.editor.model.EditorProject
import com.virexalo.editor.model.TimelineClip

object TimelineEngine {
    fun trim(project: EditorProject, clipId: String, startMs: Long, endMs: Long): EditorProject {
        val clip = project.clips.firstOrNull { it.id == clipId } ?: return project
        val sourceEnd = clip.sourceEndMs ?: (clip.sourceStartMs + clip.durationMs)
        val safeStart = startMs.coerceIn(0L, clip.durationMs - 1L)
        val safeEnd = endMs.coerceIn(safeStart + 1L, clip.durationMs)
        val updated = clip.copy(
            sourceStartMs = clip.sourceStartMs + safeStart,
            sourceEndMs = (clip.sourceStartMs + safeEnd).coerceAtMost(sourceEnd),
            durationMs = safeEnd - safeStart
        )
        return project.copy(clips = project.clips.map { if (it.id == clipId) updated else it })
    }

    fun split(project: EditorProject, clipId: String, atMs: Long): EditorProject {
        val clip = project.clips.firstOrNull { it.id == clipId } ?: return project
        val cut = atMs.coerceIn(1L, clip.durationMs - 1L)
        val first = clip.copy(
            sourceEndMs = clip.sourceStartMs + cut,
            durationMs = cut
        )
        val second = TimelineClip(
            id = java.util.UUID.randomUUID().toString(),
            uri = clip.uri,
            kind = clip.kind,
            sourceStartMs = clip.sourceStartMs + cut,
            sourceEndMs = (clip.sourceEndMs ?: (clip.sourceStartMs + clip.durationMs)),
            startOnTimelineMs = clip.startOnTimelineMs + cut,
            durationMs = clip.durationMs - cut
        )
        return project.copy(
            clips = project.clips.flatMap { if (it.id == clipId) listOf(first, second) else listOf(it) },
            selectedClipId = second.id
        )
    }
}
