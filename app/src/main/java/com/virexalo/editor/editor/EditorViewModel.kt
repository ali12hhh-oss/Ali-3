package com.virexalo.editor.editor

import androidx.lifecycle.ViewModel
import com.virexalo.editor.model.EditorProject
import com.virexalo.editor.model.EditorTool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class EditorViewModel : ViewModel() {
    private val toolController = ToolSelectionController()
    private var history: ProjectHistory? = null
    private val _state = MutableStateFlow<EditorState?>(null)
    val state: StateFlow<EditorState?> = _state.asStateFlow()

    fun start(project: EditorProject) {
        history = ProjectHistory(project)
        _state.value = EditorState(project = project)
    }

    fun applyProject(project: EditorProject) {
        history?.apply(project)
        _state.value = _state.value?.copy(project = project)
    }

    fun selectTool(tool: EditorTool) {
        toolController.select(tool)
        _state.value = _state.value?.copy(tool = tool)
    }

    fun clearTool() {
        toolController.clear()
        _state.value = _state.value?.copy(tool = EditorTool.NONE)
    }

    fun setPlayhead(positionMs: Long) {
        val current = _state.value ?: return
        val max = current.project.clips.maxOfOrNull { it.endOnTimelineMs } ?: 0L
        _state.value = current.copy(playheadMs = positionMs.coerceIn(0L, max))
    }

    fun setPlaying(playing: Boolean) {
        _state.value = _state.value?.copy(isPlaying = playing)
    }

    fun undo() {
        val project = history?.undo() ?: return
        _state.value = _state.value?.copy(project = project)
    }

    fun redo() {
        val project = history?.redo() ?: return
        _state.value = _state.value?.copy(project = project)
    }
}
