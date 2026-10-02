package com.virexalo.editor.ui

import com.virexalo.editor.model.EditorTool

data class ToolItem(val tool: EditorTool, val label: String)

object ToolCatalog {
    fun items(labels: Map<EditorTool, String>): List<ToolItem> =
        listOf(
            EditorTool.TRIM, EditorTool.SPLIT, EditorTool.TEXT,
            EditorTool.AUDIO, EditorTool.FILTERS, EditorTool.EFFECTS
        ).map { ToolItem(it, labels[it].orEmpty()) }
}
