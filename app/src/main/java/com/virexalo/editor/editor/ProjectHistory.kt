package com.virexalo.editor.editor

import com.virexalo.editor.model.EditorProject

class ProjectHistory(initial: EditorProject) {
    private val undoStack = ArrayDeque<EditorProject>()
    private val redoStack = ArrayDeque<EditorProject>()

    var current: EditorProject = initial
        private set

    fun apply(next: EditorProject) {
        if (next == current) return
        undoStack.addLast(current)
        current = next
        redoStack.clear()
    }

    fun undo(): EditorProject? {
        val previous = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        current = previous
        return current
    }

    fun redo(): EditorProject? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        current = next
        return current
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()
}
