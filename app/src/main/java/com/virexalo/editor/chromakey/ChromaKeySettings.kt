package com.virexalo.editor.chromakey

data class ChromaKeySettings(
    val enabled: Boolean = false,
    val red: Int = 0,
    val green: Int = 255,
    val blue: Int = 0,
    val threshold: Float = 0.35f,
    val softness: Float = 0.1f
)
