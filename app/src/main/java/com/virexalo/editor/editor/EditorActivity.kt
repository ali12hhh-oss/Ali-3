package com.virexalo.editor.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import android.graphics.Color
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import com.virexalo.editor.audio.AudioClip
import android.media.MediaMetadataRetriever
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
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
    private lateinit var textEditorBar: android.view.View
    private lateinit var liveTextInput: android.widget.EditText
    private lateinit var textSizeSeek: android.widget.SeekBar
    private lateinit var fontRow: android.widget.LinearLayout
    private var activeText: android.widget.EditText? = null
    private var mediaUri: Uri? = null
    private var durationMs = 1L
    private var trimStartMs = 0L
    private var trimEndMs = 1L
    private var currentProject: EditorProject? = null
    private val audioPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) prepareAudioImport(uri)
    }
    private val audioClips = mutableListOf<AudioClip>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)
        playerView = findViewById(R.id.playerView)
        timeline = findViewById(R.id.timelineView)
        emptyPreview = findViewById(R.id.emptyPreview)
        imagePreview = findViewById(R.id.imagePreview)
        overlayContainer = findViewById(R.id.overlayContainer)
        textEditorBar = findViewById(R.id.textEditorBar)
        liveTextInput = findViewById(R.id.liveTextInput)
        textSizeSeek = findViewById(R.id.textSizeSeek)
        fontRow = findViewById(R.id.fontRow)
        setupLiveTextControls()
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

        lifecycleScope.launch {
            viewModel.state.collect { state ->
                if (state != null) currentProject = state.project
            }
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
        textEditorBar.visibility = View.VISIBLE
        if (activeText == null) addTextOverlay("")
        activeText?.let {
            it.beginBatchEdit()
            liveTextInput.setText(it.text)
            liveTextInput.setSelection(liveTextInput.length())
            it.endBatchEdit()
            liveTextInput.requestFocus()
            (getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .showSoftInput(liveTextInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun setupLiveTextControls() {
        liveTextInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                activeText?.setText(s ?: "")
                activeText?.setSelection(activeText?.length() ?: 0)
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        textSizeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                activeText?.setTextSize((16 + progress).toFloat())
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
        findViewById<View>(R.id.textDeleteButton).setOnClickListener {
            activeText?.let { overlayContainer.removeView(it) }
            activeText = null
            liveTextInput.setText("")
            textEditorBar.visibility = View.GONE
            viewModel.clearTool()
        }
        listOf(
            "Sans" to "sans-serif",
            "Serif" to "serif",
            "Mono" to "monospace",
            "Medium" to "sans-serif-medium",
            "Condensed" to "sans-serif-condensed"
        ).forEach { (label, family) ->
            val b = com.google.android.material.button.MaterialButton(this).apply {
                text = label
                minWidth = 100
                setOnClickListener { activeText?.typeface = Typeface.create(family, Typeface.NORMAL) }
            }
            fontRow.addView(b, android.widget.LinearLayout.LayoutParams(100, 46).apply { marginEnd = 6 })
        }
        listOf(
            "White" to Color.WHITE, "Black" to Color.BLACK, "Red" to Color.rgb(244,67,54),
            "Yellow" to Color.rgb(255,235,59), "Green" to Color.rgb(76,175,80),
            "Blue" to Color.rgb(33,150,243), "Purple" to Color.rgb(156,39,176)
        ).forEach { (label, color) ->
            val b = com.google.android.material.button.MaterialButton(this).apply {
                text = label
                minWidth = 92
                setTextColor(color)
                setOnClickListener { activeText?.setTextColor(color) }
            }
            fontRow.addView(b, android.widget.LinearLayout.LayoutParams(92, 46).apply { marginEnd = 6 })
        }
    }

    private fun addTextOverlay(value: String) {
        val textView = DirectTransformText(this).apply {
            setText(value)
            setTextColor(Color.WHITE)
            setTextSize(28f)
            setShadowLayer(8f, 0f, 2f, Color.BLACK)
            setPadding(16, 8, 16, 8)
            beginTransformMode(true)
        }
        val params = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.CENTER
        )
        overlayContainer.addView(textView, params)
        activeText = textView
        textEditorBar.visibility = View.VISIBLE
        liveTextInput.setText(value)
        textView.requestFocus()
        Toast.makeText(this, R.string.text_added, Toast.LENGTH_SHORT).show()
    }

    private fun openAudioPicker() {
        audioPicker.launch(arrayOf("audio/*"))
    }

    private fun prepareAudioImport(uri: Uri) {
        val retriever = MediaMetadataRetriever()
        val duration = runCatching {
            retriever.setDataSource(this, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 1L
        }.getOrDefault(1L)
        retriever.release()
        val startInput = android.widget.EditText(this).apply { hint = "0"; inputType = 2 }
        val endInput = android.widget.EditText(this).apply {
            hint = (duration / 1000L).toString()
            inputType = 2
        }
        val box = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 8, 32, 8)
            addView(startInput)
            addView(endInput)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.audio_trim_before_import)
            .setMessage(getString(R.string.audio_duration_seconds, duration / 1000L))
            .setView(box)
            .setNegativeButton(R.string.close, null)
            .setPositiveButton(R.string.import_audio) { _, _ ->
                val start = (startInput.text.toString().toLongOrNull() ?: 0L) * 1000L
                val end = (endInput.text.toString().toLongOrNull() ?: (duration / 1000L)) * 1000L
                val safeStart = start.coerceIn(0L, duration - 1L)
                val safeEnd = end.coerceIn(safeStart + 1L, duration)
                audioClips += AudioClip(
                    id = java.util.UUID.randomUUID().toString(),
                    uri = uri.toString(),
                    sourceStartMs = safeStart,
                    sourceEndMs = safeEnd,
                    startOnTimelineMs = player?.currentPosition ?: 0L
                )
                Toast.makeText(this, R.string.audio_imported, Toast.LENGTH_SHORT).show()
            }.show()
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
        val clip = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(trimStartMs)
            .setEndPositionMs(trimEndMs)
            .build()
        val media = MediaItem.Builder().setUri(uri).setClippingConfiguration(clip).build()
        val item = EditedMediaItem.Builder(media).build()
        val transformer = Transformer.Builder(this)
            .setVideoMimeType("video/avc")
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
