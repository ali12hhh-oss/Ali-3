package com.virexalo.editor.editor

import com.virexalo.editor.model.EditorProject
import com.virexalo.editor.model.EditorTool

data class EditorState(
    val project: EditorProject,
    val tool: EditorTool = EditorTool.NONE,
    val playheadMs: Long = 0L,
    val isPlaying: Boolean = false
)
