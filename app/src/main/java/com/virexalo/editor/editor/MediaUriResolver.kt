package com.virexalo.editor.editor

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

object MediaUriResolver {
    fun displayName(resolver: ContentResolver, uri: Uri): String {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) return it.getString(0)
        }
        return uri.lastPathSegment ?: "media"
    }
    fun mimeType(resolver: ContentResolver, uri: Uri): String? = resolver.getType(uri)
}
