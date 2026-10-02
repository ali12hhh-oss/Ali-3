package com.virexalo.editor

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(android.graphics.Color.rgb(9,11,20))
        }
        val title = android.widget.TextView(this).apply {
            text = getString(R.string.settings_title)
            setTextColor(android.graphics.Color.WHITE); textSize = 23f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        root.addView(title)
        val language = android.widget.TextView(this).apply {
            text = getString(R.string.language_setting)
            setTextColor(android.graphics.Color.rgb(244,245,250)); textSize = 17f
            setPadding(0, dp(26), 0, dp(26))
            setOnClickListener { showLanguageDialog() }
        }
        root.addView(language)
        setContentView(root)
    }
    private fun showLanguageDialog() {
        val labels = arrayOf(getString(R.string.arabic), getString(R.string.english))
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle(R.string.choose_language)
            .setItems(labels) { _, which ->
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(if (which == 0) "ar" else "en"))
            }.show()
    }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}
