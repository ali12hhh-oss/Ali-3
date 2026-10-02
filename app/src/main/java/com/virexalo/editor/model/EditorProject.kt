package com.virexalo.editor.model

data class EditorProject(
    val id: String,
    val clips: List<TimelineClip> = emptyList(),
    val selectedClipId: String? = null
) {
    fun selectedClip(): TimelineClip? = clips.firstOrNull { it.id == selectedClipId }
    fun withSelected(id: String?): EditorProject = copy(selectedClipId = id)
}
