package com.virexalo.editor.services

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.virexalo.editor.media.MediaMetadata

object MediaProbe {
    fun inspect(context: Context, uri: Uri): MediaMetadata {
        val resolver = context.contentResolver
        val name = com.virexalo.editor.editor.MediaUriResolver.displayName(resolver, uri)
        val mime = resolver.getType(uri)
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            MediaMetadata(
                displayName = name,
                mimeType = mime,
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            )
        } finally {
            retriever.release()
        }
    }
}
