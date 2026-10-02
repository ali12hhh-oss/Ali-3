package com.virexalo.editor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.virexalo.editor.editor.EditorActivity

class MainActivity : AppCompatActivity() {
    private val mediaPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(EditorActivity.intent(this, uri))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<com.google.android.material.button.MaterialButton>(R.id.newProjectButton).setOnClickListener {
            mediaPicker.launch(arrayOf("video/*", "image/*"))
        }
    }
}