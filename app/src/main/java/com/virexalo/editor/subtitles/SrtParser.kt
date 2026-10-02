package com.virexalo.editor.subtitles

object SrtParser {
    fun parse(source: String): List<SubtitleCue> {
        val blocks = source.trim().split(Regex("\\r?\\n\\r?\\n")).filter { it.isNotBlank() }
        return blocks.mapNotNull { block ->
            val lines = block.lines()
            val timing = lines.firstOrNull { it.contains("-->") } ?: return@mapNotNull null
            val parts = timing.split("-->")
            if (parts.size != 2) return@mapNotNull null
            val start = parseTime(parts[0].trim()) ?: return@mapNotNull null
            val end = parseTime(parts[1].trim()) ?: return@mapNotNull null
            val text = lines.dropWhile { !it.contains("-->") }.drop(1).joinToString("\n")
            SubtitleCue(start, end, text)
        }
    }

    private fun parseTime(value: String): Long? {
        val p = value.replace(',', '.').split(':', '.')
        if (p.size != 4) return null
        return runCatching {
            val h=p[0].toLong(); val m=p[1].toLong(); val s=p[2].toLong(); val ms=p[3].padEnd(3,'0').take(3).toLong()
            ((h*3600+m*60+s)*1000+ms)
        }.getOrNull()
    }
}
