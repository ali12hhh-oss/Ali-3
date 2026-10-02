package com.virexalo.editor.editor

import com.virexalo.editor.model.EditorProject

class ProjectHistory(initial: EditorProject) {
    private val undoStack = ArrayDeque<EditorProject>()
    private val redoStack = ArrayDeque<EditorProject>()
    var current: EditorProject = initial
        private set
    fun apply(next: EditorProject) {
        if (next == current) return
        undoStack.addLast(current); current = next; redoStack.clear()
    }
    fun undo(): EditorProject? {
        val p = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current); current = p; return current
    }
    fun redo(): EditorProject? {
        val n = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current); current = n; return current
    }
    fun canUndo() = undoStack.isNotEmpty()
    fun canRedo() = redoStack.isNotEmpty()
}
