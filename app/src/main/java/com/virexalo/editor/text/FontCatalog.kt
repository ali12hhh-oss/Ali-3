package com.virexalo.editor.text

data class FontDefinition(val id: String, val label: String, val family: String)

object FontCatalog {
    val all = listOf(
        FontDefinition("sans","Sans","sans-serif"),
        FontDefinition("serif","Serif","serif"),
        FontDefinition("mono","Mono","monospace"),
        FontDefinition("sans_medium","Sans Medium","sans-serif-medium")
    )
}
