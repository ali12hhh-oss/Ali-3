package com.virexalo.editor

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VirexaloApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val report = buildReport(thread, throwable)
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_REPORT, report).apply()
            } catch (_: Throwable) {}
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun buildReport(thread: Thread, throwable: Throwable): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        return buildString {
            appendLine("Virexalo crash report")
            appendLine("Time: " + time)
            appendLine("Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")")
            appendLine("Device: " + Build.MANUFACTURER + " " + Build.MODEL)
            appendLine("Thread: " + thread.name)
            appendLine()
            appendLine(throwable.stackTraceToString())
        }
    }

    companion object {
        private const val PREFS = "crash_report"
        private const val KEY_REPORT = "latest"
        fun latestReport(context: Context): String? =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_REPORT, null)
        fun clearReport(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_REPORT).apply()
        fun copyReport(context: Context, report: String) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Virexalo crash report", report))
            Toast.makeText(context, "Crash report copied", Toast.LENGTH_SHORT).show()
        }
        fun shareReport(context: Context, report: String) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, report)
            }
            context.startActivity(Intent.createChooser(intent, "Share crash report"))
        }
    }
}
