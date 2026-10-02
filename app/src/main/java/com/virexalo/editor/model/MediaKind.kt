package com.virexalo.editor.model

enum class MediaKind { VIDEO, IMAGE, AUDIO }

fun MediaKind.isVisual(): Boolean = this == MediaKind.VIDEO || this == MediaKind.IMAGE
