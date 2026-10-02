package com.virexalo.editor.project

import java.util.UUID

object ProjectId {
    fun newId(): String = UUID.randomUUID().toString()
}
