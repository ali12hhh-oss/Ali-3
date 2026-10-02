package com.virexalo.editor.snapshot

import android.graphics.Bitmap
import android.view.View

object SnapshotController {
    fun capture(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }
}
