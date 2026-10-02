package com.virexalo.editor.text

data class TextOverlay(
    val id: String,
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val color: Int = 0xFFFFFFFF.toInt(),
    val fontFamily: String = "sans-serif"
)
