package com.virexalo.editor.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
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
import androidx.media3.common.C
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.Effects
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.Crop
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.StaticOverlaySettings
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
import com.google.common.collect.ImmutableList

@androidx.media3.common.util.UnstableApi
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
    private var activeText: DraggableTextView? = null
    private var mediaUri: Uri? = null
    private var durationMs = 1L
    private var trimStartMs = 0L
    private var trimEndMs = 1L
    private var currentProject: EditorProject? = null
    private val audioPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) prepareAudioImport(uri)
    }
    private val audioClips = mutableListOf<AudioClip>()
    private var filterPreset = 0
    private var brightness = 0f
    private var contrast = 0f
    private var saturation = 0f
    private var cropPreset = 0
    private var speed = 1f

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
        findViewById<android.view.View>(R.id.textCloseButton).setOnClickListener {
            textEditorBar.visibility = View.GONE
            (getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .hideSoftInputFromWindow(liveTextInput.windowToken, 0)
            viewModel.clearTool()
        }
        findViewById<android.view.View>(R.id.fullscreenButton).setOnClickListener { showFullscreen() }
        findViewById<android.view.View>(R.id.fullscreenCloseButton).setOnClickListener { hideFullscreen() }
        findViewById<android.view.View>(R.id.fullscreenPlayButton).setOnClickListener { toggleFullscreenPlayback() }
        findViewById<android.view.View>(R.id.toolCloseButton).setOnClickListener { closeToolPanel() }
        findViewById<android.widget.SeekBar>(R.id.toolSeek).setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) { applyToolValue(p) }
            override fun onStartTrackingTouch(s: SeekBar) = Unit
            override fun onStopTrackingTouch(s: SeekBar) = Unit
        })
        findViewById<android.view.View>(R.id.overlayTool).setOnClickListener { pickOverlay() }
        bindTool(R.id.drawTool, EditorTool.DRAW)
        bindTool(R.id.voiceTool, EditorTool.VOICE_OVER)
        bindTool(R.id.subtitlesTool, EditorTool.SUBTITLES)
        bindTool(R.id.cropTool, EditorTool.CROP)
        bindTool(R.id.canvasTool, EditorTool.CANVAS)
        bindTool(R.id.speedTool, EditorTool.SPEED)
        bindTool(R.id.transitionTool, EditorTool.TRANSITIONS)
        bindTool(R.id.maskTool, EditorTool.MASK)
        bindTool(R.id.chromaTool, EditorTool.CHROMA_KEY)
        bindTool(R.id.keyframeTool, EditorTool.KEYFRAMES)

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
                EditorTool.FILTERS -> showFilterCatalog()
                EditorTool.EFFECTS -> showEffectCatalog()
                EditorTool.CROP -> showCropCatalog()
                EditorTool.CANVAS -> showCanvasCatalog()
                EditorTool.SPEED -> openToolPanel("Speed")
                EditorTool.ADJUST -> openToolPanel("Adjust")
                EditorTool.TRANSITIONS -> openToolPanel("Transitions")
                EditorTool.MASK -> openToolPanel("Mask")
                EditorTool.CHROMA_KEY -> openToolPanel("Chroma Key")
                EditorTool.KEYFRAMES -> openToolPanel("Keyframes")
                EditorTool.DRAW -> openToolPanel("Draw")
                EditorTool.VOICE_OVER -> Toast.makeText(this, "Voice over", Toast.LENGTH_SHORT).show()
                EditorTool.SUBTITLES -> Toast.makeText(this, "Subtitles", Toast.LENGTH_SHORT).show()
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
            totalTimeText().text = formatTime(durationMs)
            timeline.setMediaThumbnail(frameAt(uri))
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
                    totalTimeText().text = formatTime(durationMs)
                    timeline.setMediaThumbnail(frameAt(uri))
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
                findViewById<android.widget.TextView>(R.id.currentTimeText).text = formatTime(exo.currentPosition)
                findViewById<android.view.View>(R.id.playButton).contentDescription = if (exo.isPlaying) getString(R.string.pause) else getString(R.string.play)
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
                liveTextInput.setSelection(liveTextInput.length)
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
        val textView = DraggableTextView(this).apply {
            setText(value)
            setTextColor(Color.WHITE)
            setTextSize(28f)
            setShadowLayer(8f, 0f, 2f, Color.BLACK)
            setPadding(16, 8, 16, 8)
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

    private fun pickOverlay() {
        registerOverlayPicker.launch(arrayOf("image/*", "video/*", "image/gif"))
    }

    private val registerOverlayPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        val iv = android.widget.ImageView(this).apply {
            setImageURI(uri)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = true
        }
        val p = android.widget.FrameLayout.LayoutParams(dp(150), dp(150), Gravity.CENTER)
        overlayContainer.addView(iv, p)
        iv.setOnTouchListener(object : View.OnTouchListener {
            var dx = 0f; var dy = 0f
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { dx = v.x - e.rawX; dy = v.y - e.rawY; return true }
                    MotionEvent.ACTION_MOVE -> { v.x = e.rawX + dx; v.y = e.rawY + dy; return true }
                }
                return true
            }
        })
    }

    private fun showFilterCatalog() {
        val frame = frameAt(mediaUri ?: return)
        val names = arrayOf("Original", "Mono", "Warm", "Cool", "High Contrast")
        val effects = arrayOf(
            listOf<androidx.media3.common.Effect>(),
            listOf(HslAdjustment.Builder().adjustSaturation(-100f).build()),
            listOf(HslAdjustment.Builder().adjustHue(18f).adjustLightness(5f).build()),
            listOf(HslAdjustment.Builder().adjustHue(-18f).adjustSaturation(8f).build()),
            listOf(Contrast(0.45f), Brightness(0.04f))
        )
        val row = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        names.indices.forEach { i ->
            val box = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(5), dp(4), dp(5), dp(4))
            }
            val preview = android.widget.ImageView(this)
            preview.layoutParams = android.widget.LinearLayout.LayoutParams(dp(82), dp(58))
            preview.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            preview.setImageBitmap(frame)
            if (i != 0) {
                val cm = android.graphics.ColorMatrix()
                when (i) {
                    1 -> cm.setSaturation(0f)
                    2 -> cm.set(floatArrayOf(1.08f,0f,0f,0f,0f, 0f,0.96f,0f,0f,0f, 0f,0f,0.82f,0f,0f, 0f,0f,0f,1f,0f))
                    3 -> cm.set(floatArrayOf(0.84f,0f,0f,0f,0f, 0f,0.95f,0f,0f,0f, 0f,0f,1.10f,0f,0f, 0f,0f,0f,1f,0f))
                    4 -> cm.setSaturation(1.45f)
                }
                preview.colorFilter = android.graphics.ColorMatrixColorFilter(cm)
            }
            val label = android.widget.TextView(this).apply {
                text = names[i]
                textSize = 11f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            }
            box.addView(preview); box.addView(label)
            box.setOnClickListener {
                filterPreset = i
                applyVideoEffects()
                (box.parent as? android.view.ViewGroup)?.let { }
            }
            row.addView(box)
        }
        AlertDialog.Builder(this).setTitle(R.string.filters).setView(row).setNegativeButton(R.string.close, null).show()
    }

    private fun imagePreviewPaint(view: android.widget.ImageView, matrix: android.graphics.ColorMatrix) {
        view.colorFilter = android.graphics.ColorMatrixColorFilter(matrix)
    }

    private fun showEffectCatalog() {
        val names = arrayOf("None", "Fade", "Soft Blur")
        AlertDialog.Builder(this)
            .setTitle(R.string.effects)
            .setItems(names) { _, which ->
                if (which == 2) {
                    openToolPanel("Soft Blur")
                } else {
                    viewModel.clearTool()
                }
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun showCropCatalog() {
        val names = arrayOf("Original", "1:1", "4:5", "9:16", "16:9")
        AlertDialog.Builder(this)
            .setTitle("Crop")
            .setItems(names) { _, which ->
                cropPreset = which
                applyVideoEffects()
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun showCanvasCatalog() {
        val names = arrayOf("Black", "Dark", "White")
        AlertDialog.Builder(this).setTitle("Canvas Background").setItems(names) { _, which ->
            val colors = intArrayOf(Color.BLACK, Color.rgb(18,20,30), Color.WHITE)
            overlayContainer.setBackgroundColor(colors[which])
        }.setNegativeButton(R.string.close, null).show()
    }

    private fun createVideoEffects(): List<androidx.media3.common.Effect> {
        val list = mutableListOf<androidx.media3.common.Effect>()
        when (filterPreset) {
            1 -> list += HslAdjustment.Builder().adjustSaturation(-100f).build()
            2 -> list += HslAdjustment.Builder().adjustHue(18f).adjustLightness(5f).build()
            3 -> list += HslAdjustment.Builder().adjustHue(-18f).adjustSaturation(8f).build()
            4 -> list += listOf(Contrast(0.45f), Brightness(0.04f))
        }
        if (brightness != 0f) list += Brightness(brightness)
        if (contrast != 0f) list += Contrast(contrast)
        if (saturation != 0f) list += HslAdjustment.Builder().adjustSaturation(saturation).build()
        when (cropPreset) {
            1 -> list += Crop(-1f, 1f, -1f, 1f)
            2 -> list += Crop(-0.8f, 0.8f, -1f, 1f)
            3 -> list += Crop(-0.5625f, 0.5625f, -1f, 1f)
            4 -> list += Crop(-1f, 1f, -0.5625f, 0.5625f)
        }
        val text = activeText?.text?.toString()?.trim().orEmpty()
        if (text.isNotEmpty()) {
            val overlay = TextOverlay.createStaticTextOverlay(
                android.text.SpannableString(text),
                StaticOverlaySettings.Builder()
                    .setBackgroundFrameAnchor(0f, 0f)
                    .setOverlayFrameAnchor(0f, 0f)
                    .setScale(0.75f, 0.75f)
                    .build()
            )
            list += OverlayEffect(mutableListOf<androidx.media3.effect.TextureOverlay>(overlay))
        }
        return list
    }

    private fun applyVideoEffects() {
        runCatching { player?.setVideoEffects(createVideoEffects()) }
            .onFailure { Toast.makeText(this, "Effect unavailable on this device", Toast.LENGTH_SHORT).show() }
    }

    private fun openToolPanel(title: String) {
        findViewById<View>(R.id.toolPanel).visibility = View.VISIBLE
        findViewById<android.widget.TextView>(R.id.toolTitle).text = title
        findViewById<SeekBar>(R.id.toolSeek).progress = 50
    }

    private fun closeToolPanel() {
        findViewById<View>(R.id.toolPanel).visibility = View.GONE
        viewModel.clearTool()
    }

    private fun applyToolValue(progress: Int) {
        val tool = viewModel.state.value?.tool ?: EditorTool.NONE
        if (tool == EditorTool.SPEED) {
            player?.setPlaybackSpeed((0.25f + progress / 100f * 1.75f).coerceIn(0.25f, 2f))
        }
    }

    private fun frameAt(uri: Uri): Bitmap? = runCatching {
        val mime = contentResolver.getType(uri).orEmpty()
        if (mime.startsWith("image/")) {
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        } else {
            val r = MediaMetadataRetriever()
            r.setDataSource(this, uri)
            val b = r.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            r.release()
            b
        }
    }.getOrNull()

    private fun totalTimeText() = findViewById<android.widget.TextView>(R.id.totalTimeText)

    private fun formatTime(ms: Long): String = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

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

    private fun showFullscreen() {
        val overlay = findViewById<View>(R.id.fullscreenOverlay)
        val fullPlayer = findViewById<PlayerView>(R.id.fullscreenPlayer)
        fullPlayer.player = player
        overlay.visibility = View.VISIBLE
    }

    private fun hideFullscreen() {
        findViewById<View>(R.id.fullscreenOverlay).visibility = View.GONE
        findViewById<PlayerView>(R.id.fullscreenPlayer).player = null
        playerView.player = player
    }

    private fun toggleFullscreenPlayback() {
        player?.let { if (it.isPlaying) it.pause() else it.play() }
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
        val itemBuilder = EditedMediaItem.Builder(media)
            .setEffects(Effects(emptyList(), createVideoEffects()))
        if (speed != 1f) {
            itemBuilder.setSpeed(object : SpeedProvider {
                override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
                override fun getSpeed(timeUs: Long): Float = speed
            })
        }
        val item = itemBuilder.build()
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

    private class DraggableTextView(context: Context) : androidx.appcompat.widget.AppCompatTextView(context) {
        private var dx = 0f; private var dy = 0f
        init {
            isClickable = true
            setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { dx = v.x - e.rawX; dy = v.y - e.rawY; true }
                    MotionEvent.ACTION_MOVE -> { v.x = e.rawX + dx; v.y = e.rawY + dy; true }
                    else -> true
                }
            }
        }
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
