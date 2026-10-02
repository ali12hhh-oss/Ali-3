package com.virexalo.editor.media

import android.content.ContentResolver
import android.net.Uri
import com.virexalo.editor.model.MediaKind

object MediaTypeResolver {
    fun resolve(resolver: ContentResolver, uri: Uri): MediaKind {
        val type = resolver.getType(uri).orEmpty()
        return when {
            type == "image/gif" -> MediaKind.GIF
            type.startsWith("audio/") -> MediaKind.AUDIO
            type.startsWith("image/") -> MediaKind.IMAGE
            else -> MediaKind.VIDEO
        }
    }
}
