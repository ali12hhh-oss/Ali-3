package com.virexalo.editor.editor

import com.virexalo.editor.model.EditorTool

class ToolSelectionController {
    var selectedTool: EditorTool = EditorTool.NONE
        private set

    fun select(tool: EditorTool) {
        selectedTool = tool
    }

    fun clear() {
        selectedTool = EditorTool.NONE
    }
}
