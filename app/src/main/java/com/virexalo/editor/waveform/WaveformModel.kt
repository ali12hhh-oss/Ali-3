package com.virexalo.editor.waveform

data class WaveformModel(val points: List<WaveformPoint>) {
    companion object {
        fun from(samples: FloatArray): WaveformModel =
            WaveformModel(samples.map { WaveformPoint(it.coerceIn(0f, 1f)) })
    }
}
