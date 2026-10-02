package com.virexalo.editor.drawing
data class DrawingPoint(val x:Float,val y:Float)
data class DrawingStroke(val points:List<DrawingPoint>,val width:Float=4f)