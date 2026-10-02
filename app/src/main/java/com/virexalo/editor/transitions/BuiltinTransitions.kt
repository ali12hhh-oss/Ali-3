package com.virexalo.editor.transitions

object BuiltinTransitions {
    val all = listOf(
        TransitionDefinition("cut","Cut",0L),
        TransitionDefinition("fade","Fade",400L),
        TransitionDefinition("crossfade","Crossfade",500L),
        TransitionDefinition("slide","Slide",450L)
    )
}
