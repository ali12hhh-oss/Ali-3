package com.virexalo.editor.history

class CommandHistory<T>(initial: T) {
    private val undo = ArrayDeque<T>()
    private val redo = ArrayDeque<T>()
    var current: T = initial
        private set

    fun apply(next: T) {
        if (next == current) return
        undo.addLast(current)
        current = next
        redo.clear()
    }

    fun undo(): T? {
        val previous = undo.removeLastOrNull() ?: return null
        redo.addLast(current)
        current = previous
        return current
    }

    fun redo(): T? {
        val next = redo.removeLastOrNull() ?: return null
        undo.addLast(current)
        current = next
        return current
    }
}
