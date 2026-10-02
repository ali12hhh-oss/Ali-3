package com.virexalo.editor.render

import com.virexalo.editor.export.ExportSettings
import com.virexalo.editor.model.EditorProject

data class RenderPlan(
    val project: EditorProject,
    val settings: ExportSettings
)
