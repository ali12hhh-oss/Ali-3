package com.virexalo.editor.audio
data class AudioSelection(val startMs:Long,val endMs:Long){init{require(startMs>=0&&endMs>startMs)}}