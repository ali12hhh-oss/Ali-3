package com.virexalo.editor.speed
data class SpeedSettings(val factor:Float=1f){val safeFactor get()=factor.coerceIn(.25f,8f)}