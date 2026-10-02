package com.virexalo.editor.overlays

data class ImageOverlay(
    val id: String,
    val uri: String,
    val startMs: Long,
    val endMs: Long,
    val transform: OverlayTransform = OverlayTransform()
)
