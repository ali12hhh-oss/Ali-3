package com.virexalo.editor.model

enum class MediaKind { VIDEO, IMAGE, GIF, AUDIO }

fun MediaKind.isVisual(): Boolean =
    this == MediaKind.VIDEO || this == MediaKind.IMAGE || this == MediaKind.GIF
