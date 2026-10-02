package com.virexalo.editor.render

object RenderValidator {
    fun validate(plan: RenderPlan): List<String> {
        val errors = mutableListOf<String>()
        if (plan.project.clips.isEmpty()) errors += "Project has no media clips"
        if (plan.project.clips.any { it.durationMs <= 0L }) errors += "A clip has an invalid duration"
        return errors
    }
}
