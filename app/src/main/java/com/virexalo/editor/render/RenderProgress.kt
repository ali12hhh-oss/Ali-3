package com.virexalo.editor.render
data class RenderProgress(val percent:Int,val message:String=""){val clampedPercent get()=percent.coerceIn(0,100)}