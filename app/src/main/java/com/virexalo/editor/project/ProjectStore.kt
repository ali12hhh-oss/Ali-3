package com.virexalo.editor.project
import android.content.Context
import com.google.gson.Gson
import com.virexalo.editor.model.EditorProject
class ProjectStore(context:Context){private val p=context.getSharedPreferences("virexalo_projects",0);private val g=Gson();fun save(s:ProjectSnapshot)=p.edit().putString("current",g.toJson(s.project)).apply();fun load():ProjectSnapshot?=p.getString("current",null)?.let{runCatching{g.fromJson(it,EditorProject::class.java)}.getOrNull()?.let(::ProjectSnapshot)};fun clear()=p.edit().remove("current").apply()}