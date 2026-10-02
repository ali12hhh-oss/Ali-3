package com.virexalo.editor.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ClippingConfiguration
import androidx.media3.ui.PlayerView
import com.virexalo.editor.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class EditorActivity : AppCompatActivity() {
    companion object {
        private const val EXTRA_URI = "media_uri"
        fun intent(context: Context, uri: Uri) =
            Intent(context, EditorActivity::class.java).putExtra(EXTRA_URI, uri.toString())
    }

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var timeline: TimelineView
    private lateinit var emptyPreview: android.widget.TextView
    private var mediaUri: Uri? = null
    private var durationMs = 1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)
        playerView = findViewById(R.id.playerView)
        timeline = findViewById(R.id.timelineView)
        emptyPreview = findViewById(R.id.emptyPreview)
        mediaUri = intent.getStringExtra(EXTRA_URI)?.let(Uri::parse)

        findViewById<android.view.View>(R.id.backButton).setOnClickListener { finish() }
        findViewById<android.view.View>(R.id.playButton).setOnClickListener { togglePlayback() }
        findViewById<android.view.View>(R.id.exportButton).setOnClickListener { exportTrimmed() }
        findViewById<android.view.View>(R.id.trimTool).setOnClickListener {
            Toast.makeText(this, R.string.trim, Toast.LENGTH_SHORT).show()
        }
        findViewById<android.view.View>(R.id.splitTool).setOnClickListener { splitAtPlayhead() }
        findViewById<android.view.View>(R.id.textTool).setOnClickListener {
            Toast.makeText(this, R.string.text, Toast.LENGTH_SHORT).show()
        }
        findViewById<android.view.View>(R.id.audioTool).setOnClickListener {
            Toast.makeText(this, R.string.audio, Toast.LENGTH_SHORT).show()
        }
        findViewById<android.view.View>(R.id.filterTool).setOnClickListener {
            Toast.makeText(this, R.string.filters, Toast.LENGTH_SHORT).show()
        }
        findViewById<android.view.View>(R.id.effectTool).setOnClickListener {
            Toast.makeText(this, R.string.effects, Toast.LENGTH_SHORT).show()
        }

        preparePlayer()
    }

    private fun preparePlayer() {
        val uri = mediaUri ?: return
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
                    timeline.setTimeline(durationMs, exo.currentPosition)
                }
            }
        })
        lifecycleScope.launch {
            while (true) {
                kotlinx.coroutines.delay(100)
                if (!isFinishing) timeline.setPosition(exo.currentPosition)
            }
        }
    }

    private fun togglePlayback() {
        player?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    private fun splitAtPlayhead() {
        val p = player?.currentPosition ?: return
        if (p <= 0L || p >= durationMs) {
            Toast.makeText(this, R.string.split, Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "Split at " + (p / 1000) + "s", Toast.LENGTH_SHORT).show()
    }

    private fun exportTrimmed() {
        val uri = mediaUri ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val output = File(
                getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                "Virexalo_" + System.currentTimeMillis() + ".mp4"
            )
            val clip = ClippingConfiguration.Builder()
                .setStartPositionMs(0)
                .setEndPositionMs(durationMs)
                .build()
            val item = EditedMediaItem.Builder(
                MediaItem.Builder()
                    .setUri(uri)
                    .setClippingConfiguration(clip)
                    .build()
            ).build()
            val transformer = Transformer.Builder(this@EditorActivity)
                .setTransformationRequest(
                    TransformationRequest.Builder()
                        .setVideoMimeType("video/avc")
                        .build()
                )
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(
                        composition: Composition,
                        exportResult: androidx.media3.transformer.ExportResult
                    ) {
                        runOnUiThread {
                            Toast.makeText(
                                this@EditorActivity,
                                R.string.export_done,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: androidx.media3.transformer.ExportResult,
                        exportException: ExportException
                    ) {
                        runOnUiThread {
                            Toast.makeText(
                                this@EditorActivity,
                                R.string.export_failed,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }).build()
            transformer.start(item, output.absolutePath)
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