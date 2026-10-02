package com.virexalo.editor.export

enum class ExportResolution(val width: Int, val height: Int) {
    HD(1280, 720),
    FULL_HD(1920, 1080),
    UHD(3840, 2160)
}

enum class ExportFrameRate(val fps: Int) { FPS_24(24), FPS_30(30), FPS_60(60) }

data class ExportSettings(
    val resolution: ExportResolution = ExportResolution.FULL_HD,
    val frameRate: ExportFrameRate = ExportFrameRate.FPS_30,
    val videoBitrate: Int = 8_000_000,
    val removeAudio: Boolean = false
)
