package com.virexalo.editor.project

import android.content.Context
import com.google.gson.Gson
import java.io.File

class ProjectStore(context: Context) {
    private val file = File(context.filesDir, "virexalo_projects.json")
    private val gson = Gson()

    fun save(snapshot: ProjectSnapshot) {
        file.writeText(gson.toJson(snapshot))
    }

    fun load(): ProjectSnapshot? =
        if (file.exists()) runCatching { gson.fromJson(file.readText(), ProjectSnapshot::class.java) }.getOrNull() else null
}
