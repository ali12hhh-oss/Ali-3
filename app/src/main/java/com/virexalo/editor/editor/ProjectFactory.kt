package com.virexalo.editor.editor

import android.content.ContentResolver
import android.net.Uri
import com.virexalo.editor.model.EditorProject
import com.virexalo.editor.model.MediaKind
import com.virexalo.editor.model.TimelineClip
import java.util.UUID

object ProjectFactory {
    fun createFromMedia(
        resolver: ContentResolver,
        uri: Uri,
        durationMs: Long
    ): EditorProject {
        val mime = resolver.getType(uri).orEmpty()
        val kind = when {
            mime.startsWith("image/") -> MediaKind.IMAGE
            else -> MediaKind.VIDEO
        }
        val clip = TimelineClip(
            id = UUID.randomUUID().toString(),
            uri = uri.toString(),
            kind = kind,
            durationMs = durationMs.coerceAtLeast(1L)
        )
        return EditorProject(
            id = UUID.randomUUID().toString(),
            clips = listOf(clip),
            selectedClipId = clip.id
        )
    }
}
