package com.virexalo.editor

import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.virexalo.editor.editor.EditorActivity

class MainActivity : AppCompatActivity() {

    private val mediaPicker = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@registerForActivityResult
        startActivity(EditorActivity.intent(this, uri))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<android.view.View>(R.id.newProjectButton).setOnClickListener {
            showMediaTypeChooser()
        }
        findViewById<android.view.View>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<android.view.View>(R.id.languageButton).setOnClickListener {
            showLanguageDialog()
        }
    }

    private fun showMediaTypeChooser() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.choose_media)
            .setItems(arrayOf(getString(R.string.photos), getString(R.string.videos))) { _, which ->
                val mediaUri = if (which == 0) {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }
                val intent = Intent(Intent.ACTION_PICK, mediaUri).apply {
                    type = if (which == 0) "image/*" else "video/*"
                }
                mediaPicker.launch(intent)
            }
            .setNegativeButton(R.string.close, null)
            .show()
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