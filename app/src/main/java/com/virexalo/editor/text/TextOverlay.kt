package com.virexalo.editor.text
import com.virexalo.editor.overlays.OverlayTransform
data class TextOverlay(val id:String,val text:String,val startMs:Long,val endMs:Long,val font:FontDefinition=FontCatalog.all.first(),val sizeSp:Float=32f,val color:Int=0xFFFFFFFF.toInt(),val transform:OverlayTransform=OverlayTransform())