package com.virexalo.editor.transitions
data class Transition(val type:TransitionDefinition=BuiltinTransitions.all.first(),val durationMs:Long=type.durationMs)