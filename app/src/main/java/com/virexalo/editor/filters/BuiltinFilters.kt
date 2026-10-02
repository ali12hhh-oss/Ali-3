package com.virexalo.editor.filters

object BuiltinFilters {
    val all = listOf(
        FilterDefinition("original","Original"),
        FilterDefinition("mono","Mono", saturation = 0f),
        FilterDefinition("contrast","Contrast", contrast = 1.2f),
        FilterDefinition("warm","Warm", saturation = 1.1f),
        FilterDefinition("soft","Soft", brightness = 0.08f, contrast = 0.95f)
    )
}
