package com.virexalo.editor.editor

import android.content.ContentResolver
import android.net.Uri
import com.virexalo.editor.model.*
import java.util.UUID

object ProjectFactory {
    fun createFromMedia(resolver: ContentResolver, uri: Uri, durationMs: Long): EditorProject {
        val mime = resolver.getType(uri).orEmpty()
        val kind = when {
            mime == "image/gif" -> MediaKind.GIF
            mime.startsWith("image/") -> MediaKind.IMAGE
            mime.startsWith("audio/") -> MediaKind.AUDIO
            else -> MediaKind.VIDEO
        }
        val clip = TimelineClip(
            UUID.randomUUID().toString(),
            uri.toString(),
            kind,
            durationMs = durationMs.coerceAtLeast(1L)
        )
        return EditorProject(UUID.randomUUID().toString(), listOf(clip), clip.id)
    }
}
