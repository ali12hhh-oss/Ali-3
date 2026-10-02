package com.virexalo.editor.ui

import com.virexalo.editor.model.EditorTool

data class ToolPanelState(
    val tool: EditorTool = EditorTool.NONE,
    val visible: Boolean = false
)
