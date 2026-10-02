package com.virexalo.editor.player

import androidx.media3.common.Player

class PreviewController(private val player: Player) {
    fun toggle() { if (player.isPlaying) player.pause() else player.play() }
    fun seekTo(positionMs: Long) { player.seekTo(positionMs.coerceAtLeast(0L)) }
    fun stop() { player.pause(); player.seekTo(0L) }
}
