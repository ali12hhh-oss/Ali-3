package com.virexalo.editor.masks
enum class MaskShape{RECTANGLE,CIRCLE,ROUNDED_RECTANGLE,LINEAR,RADIAL}
data class MaskDefinition(val shape:MaskShape,val feather:Float=0f)