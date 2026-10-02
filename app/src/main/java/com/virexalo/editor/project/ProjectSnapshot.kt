package com.virexalo.editor.project

import com.virexalo.editor.model.EditorProject
import com.virexalo.editor.audio.AudioClip
import com.virexalo.editor.text.TextOverlay

data class ProjectSnapshot(
    val project: EditorProject,
    val audio: List<AudioClip> = emptyList(),
    val text: List<TextOverlay> = emptyList()
)
