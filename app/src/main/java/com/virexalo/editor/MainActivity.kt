package com.virexalo.editor

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.virexalo.editor.editor.EditorActivity

class MainActivity : AppCompatActivity() {
    private val mediaPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) { }
        startActivity(EditorActivity.intent(this, uri))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<android.view.View>(R.id.newProjectButton).setOnClickListener {
            mediaPicker.launch(arrayOf("video/*", "image/*"))
        }
        findViewById<android.view.View>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<android.view.View>(R.id.languageButton).setOnClickListener {
            showLanguageDialog()
        }
    }

    private fun showLanguageDialog() {
        val labels = arrayOf(getString(R.string.arabic), getString(R.string.english))
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.choose_language)
            .setItems(labels) { _, which ->
                val tag = if (which == 0) "ar" else "en"
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
            }.show()
    }
}
