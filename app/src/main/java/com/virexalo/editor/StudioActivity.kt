package com.virexalo.editor

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.virexalo.editor.editor.EditorActivity

class StudioActivity : AppCompatActivity() {
    private lateinit var grid: GridLayout
    private val requestCode = 41

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(16))
            setBackgroundColor(android.graphics.Color.rgb(9,11,20))
        }
        val title = TextView(this).apply {
            text = getString(R.string.studio_title)
            setTextColor(android.graphics.Color.WHITE)
            textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(14))
        }
        root.addView(title)
        grid = GridLayout(this).apply { columnCount = 3; useDefaultMargins = true }
        root.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        if (hasStoragePermission()) loadMedia() else requestStoragePermission()
    }

    private fun hasStoragePermission(): Boolean =
        if (Build.VERSION.SDK_INT >= 33)
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        else ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun requestStoragePermission() {
        val permissions = if (Build.VERSION.SDK_INT >= 33)
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        ActivityCompat.requestPermissions(this, permissions, requestCode)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == this.requestCode) loadMedia()
    }

    private fun loadMedia() {
        grid.removeAllViews()
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE)
        val selection = MediaStore.Files.FileColumns.MEDIA_TYPE + "=? OR " + MediaStore.Files.FileColumns.MEDIA_TYPE + "=?"
        val args = arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(), MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
        var count = 0
        contentResolver.query(collection, projection, selection, args, MediaStore.Files.FileColumns.DATE_ADDED + " DESC")?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val typeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            while (c.moveToNext() && count < 120) {
                val id = c.getLong(idCol)
                val isVideo = c.getInt(typeCol) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val base = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                addMedia(Uri.withAppendedPath(base, id.toString()))
                count++
            }
        }
        if (count == 0) Toast.makeText(this, R.string.studio_empty, Toast.LENGTH_SHORT).show()
    }

    private fun addMedia(uri: Uri) {
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(android.graphics.Color.rgb(23,29,48))
            layoutParams = GridLayout.LayoutParams().apply {
                width = dp(104); height = dp(104); setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            try {
                if (Build.VERSION.SDK_INT >= 29) setImageBitmap(contentResolver.loadThumbnail(uri, android.util.Size(320,320), null))
                else setImageURI(uri)
            } catch (_: Exception) { }
            setOnClickListener { startActivity(EditorActivity.intent(this@StudioActivity, uri)) }
        }
        grid.addView(image)
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
