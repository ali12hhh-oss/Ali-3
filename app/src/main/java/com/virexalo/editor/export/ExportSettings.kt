package com.virexalo.editor.export
enum class ExportResolution(val width:Int,val height:Int){HD(1280,720),FULL_HD(1920,1080),FOUR_K(3840,2160)}
data class ExportSettings(val resolution:ExportResolution=ExportResolution.FULL_HD,val frameRate:Int=30,val includeAudio:Boolean=true)