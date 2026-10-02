package com.virexalo.editor.filters

data class FilterDefinition(
    val id: String,
    val name: String,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f
)
