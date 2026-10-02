package com.virexalo.editor.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.ClippingConfiguration
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.Transformer
import androidx.media3.ui.PlayerView
import com.virexalo.editor.R
import com.virexalo.editor.model.EditorProject
import com.virexalo.editor.model.EditorTool
import com.virexalo.editor.model.MediaKind
import com.virexalo.editor.model.TimelineClip
import com.virexalo.editor.timeline.TimelineEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class EditorActivity : AppCompatActivity() {
    companion object {
        private const val EXTRA_URI = "media_uri"
        fun intent(context: Context, uri: Uri) =
            Intent(context, EditorActivity::class.java).putExtra(EXTRA_URI, uri.toString())
    }

    private val viewModel: EditorViewModel by viewModels()
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var timeline: TimelineView
    private lateinit var emptyPreview: android.widget.TextView
    private lateinit var imagePreview: android.widget.ImageView
    private lateinit var overlayContainer: android.widget.FrameLayout
    private var mediaUri: Uri? = null
    private var durationMs = 1L
    private var trimStartMs = 0L
    private var trimEndMs = 1L
    private var currentProject: EditorProject? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)
        playerView = findViewById(R.id.playerView)
        timeline = findViewById(R.id.timelineView)
        emptyPreview = findViewById(R.id.emptyPreview)
        imagePreview = findViewById(R.id.imagePreview)
        overlayContainer = findViewById(R.id.overlayContainer)
        mediaUri = intent.getStringExtra(EXTRA_URI)?.let(Uri::parse)

        findViewById<android.view.View>(R.id.backButton).setOnClickListener { finish() }
        findViewById<android.view.View>(R.id.playButton).setOnClickListener { togglePlayback() }
        findViewById<android.view.View>(R.id.undoButton).setOnClickListener { viewModel.undo() }
        findViewById<android.view.View>(R.id.redoButton).setOnClickListener { viewModel.redo() }
        findViewById<android.view.View>(R.id.exportButton).setOnClickListener { exportTrimmed() }

        bindTool(R.id.trimTool, EditorTool.TRIM)
        bindTool(R.id.splitTool, EditorTool.SPLIT)
        bindTool(R.id.textTool, EditorTool.TEXT)
        bindTool(R.id.audioTool, EditorTool.AUDIO)
        bindTool(R.id.filterTool, EditorTool.FILTERS)
        bindTool(R.id.effectTool, EditorTool.EFFECTS)

        timeline.onTrimChanged = { start, end ->
            trimStartMs = start
            trimEndMs = end
        }
        timeline.onPositionChanged = { position ->
            player?.seekTo(position)
            viewModel.setPlayhead(position)
        }

        preparePlayer()
    }

    private fun bindTool(id: Int, tool: EditorTool) {
        findViewById<android.view.View>(id).setOnClickListener {
            viewModel.selectTool(tool)
            when (tool) {
                EditorTool.TRIM -> timeline.setTrim(trimStartMs, trimEndMs)
                EditorTool.SPLIT -> splitAtPlayhead()
                EditorTool.TEXT -> showTextTool()
                EditorTool.AUDIO -> openAudioPicker()
                EditorTool.FILTERS -> showCatalog(R.string.filters)
                EditorTool.EFFECTS -> showCatalog(R.string.effects)
                else -> Unit
            }
        }
    }

    private fun preparePlayer() {
        val uri = mediaUri ?: return
        val mime = contentResolver.getType(uri).orEmpty()
        if (mime.startsWith("image/")) {
            playerView.visibility = android.view.View.GONE
            imagePreview.visibility = android.view.View.VISIBLE
            imagePreview.setImageURI(uri)
            emptyPreview.visibility = android.view.View.GONE
            durationMs = 5000L
            trimStartMs = 0L
            trimEndMs = durationMs
            timeline.setTimeline(durationMs, 0L)
            currentProject = createInitialProject(uri)
            currentProject?.let(viewModel::start)
            return
        }
        playerView.visibility = android.view.View.VISIBLE
        imagePreview.visibility = android.view.View.GONE
        emptyPreview.visibility = android.view.View.GONE
        val exo = ExoPlayer.Builder(this).build()
        player = exo
        playerView.player = exo
        exo.setMediaItem(MediaItem.fromUri(uri))
        exo.prepare()
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    durationMs = exo.duration.coerceAtLeast(1L)
                    trimStartMs = 0L
                    trimEndMs = durationMs
                    timeline.setTimeline(durationMs, exo.currentPosition)
                    currentProject = createInitialProject(uri)
                    currentProject?.let(viewModel::start)
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                viewModel.setPlaying(isPlaying)
            }
        })
        lifecycleScope.launch {
            while (isActive && !isFinishing) {
                delay(100)
                timeline.setPosition(exo.currentPosition)
                viewModel.setPlayhead(exo.currentPosition)
            }
        }
    }

    private fun createInitialProject(uri: Uri): EditorProject {
        val kind = if (contentResolver.getType(uri).orEmpty().startsWith("image/")) MediaKind.IMAGE else MediaKind.VIDEO
        val clip = TimelineClip(
            id = java.util.UUID.randomUUID().toString(),
            uri = uri.toString(),
            kind = kind,
            durationMs = durationMs
        )
        return EditorProject(java.util.UUID.randomUUID().toString(), listOf(clip), clip.id)
    }

    private fun togglePlayback() {
        player?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    private fun splitAtPlayhead() {
        val project = currentProject ?: return
        val clip = project.selectedClip() ?: return
        val position = player?.currentPosition ?: return
        if (position <= trimStartMs || position >= trimEndMs) return
        val local = position - clip.startOnTimelineMs
        if (local <= 0L || local >= clip.durationMs) return
        val next = TimelineEngine.split(project, clip.id, local)
        currentProject = next
        viewModel.applyProject(next)
        Toast.makeText(this, R.string.split_done, Toast.LENGTH_SHORT).show()
    }

    private fun showTextTool() {
        val input = android.widget.EditText(this).apply {
            hint = getString(R.string.enter_text)
            setSingleLine(false)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.text)
            .setView(input)
            .setNegativeButton(R.string.close, null)
            .setPositiveButton(R.string.add_text) { _, _ ->
                val value = input.text.toString().trim()
                if (value.isNotEmpty()) addTextOverlay(value)
            }.show()
    }

    private fun addTextOverlay(value: String) {
        val textView = android.widget.TextView(this).apply {
            text = value
            setTextColor(android.graphics.Color.WHITE)
            textSize = 28f
            setShadowLayer(8f, 0f, 2f, android.graphics.Color.BLACK)
            setPadding(16, 8, 16, 8)
            isClickable = true
        }
        val params = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.CENTER
        )
        overlayContainer.addView(textView, params)
        textView.setOnTouchListener(object : android.view.View.OnTouchListener {
            var downX = 0f
            var downY = 0f
            var baseX = 0f
            var baseY = 0f
            override fun onTouch(v: android.view.View, event: android.view.MotionEvent): Boolean {
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX; downY = event.rawY
                        baseX = v.x; baseY = v.y
                        return true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        v.x = baseX + event.rawX - downX
                        v.y = baseY + event.rawY - downY
                        return true
                    }
                    android.view.MotionEvent.ACTION_UP -> return true
                }
                return true
            }
        })
        Toast.makeText(this, R.string.text_added, Toast.LENGTH_SHORT).show()
    }

    private fun openAudioPicker() {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "audio/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }, getString(R.string.audio)))
    }

    private fun showCatalog(title: Int) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(
                if (title == R.string.filters)
                    arrayOf("Original", "Mono", "Contrast", "Warm", "Soft")
                else
                    arrayOf("None", "Fade", "Flash", "Shake", "Zoom"),
                null
            )
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun exportTrimmed() {
        val uri = mediaUri ?: return
        if (trimEndMs <= trimStartMs) return
        val outputDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
        val output = File(outputDir, "Virexalo_${System.currentTimeMillis()}.mp4")
        val clip = ClippingConfiguration.Builder()
            .setStartPositionMs(trimStartMs)
            .setEndPositionMs(trimEndMs)
            .build()
        val media = MediaItem.Builder().setUri(uri).setClippingConfiguration(clip).build()
        val item = EditedMediaItem.Builder(media).build()
        val transformer = Transformer.Builder(this)
            .setTransformationRequest(
                TransformationRequest.Builder().setVideoMimeType("video/avc").build()
            )
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: androidx.media3.transformer.ExportResult) {
                    Toast.makeText(this@EditorActivity, R.string.export_done, Toast.LENGTH_LONG).show()
                }
                override fun onError(composition: Composition, exportResult: androidx.media3.transformer.ExportResult, exportException: ExportException) {
                    Toast.makeText(this@EditorActivity, R.string.export_failed, Toast.LENGTH_LONG).show()
                }
            }).build()
        player?.pause()
        transformer.start(item, output.absolutePath)
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}
