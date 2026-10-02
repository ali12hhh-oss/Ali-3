package com.virexalo.editor.services

import android.content.Context
import com.virexalo.editor.model.EditorProject
import com.virexalo.editor.project.ProjectSnapshot
import com.virexalo.editor.project.ProjectStore

class ProjectService(context: Context) {
    private val store = ProjectStore(context)
    fun save(project: EditorProject) = store.save(ProjectSnapshot(project))
    fun load(): ProjectSnapshot? = store.load()
}
