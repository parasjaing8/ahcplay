package com.aihomecloud.ahcplayer.player

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aihomecloud.ahcplayer.R
import com.aihomecloud.ahcplayer.data.db.AppDatabase
import com.aihomecloud.ahcplayer.data.db.WatchHistoryEntity
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

class PlayerActivity : AppCompatActivity(), SurfaceHolder.Callback {

    companion object {
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_SOURCE_ID = "extra_source_id"
        private const val HIDE_CONTROLS_DELAY_MS = 4000L
        private const val SEEK_STEP_MS = 10_000L
        private const val PROGRESS_UPDATE_INTERVAL_MS = 500L
        private const val HISTORY_SAVE_INTERVAL_MS = 30_000L
    }

    private lateinit var libVlc: LibVLC
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var surfaceView: SurfaceView
    private lateinit var controlsLayout: View
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnRewind: ImageButton
    private lateinit var btnFastForward: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var tvPosition: TextView
    private lateinit var tvDuration: TextView
    private lateinit var tvTitle: TextView
    private lateinit var btnAudioTrack: ImageButton
    private lateinit var btnSubTrack: ImageButton
    private lateinit var tvSpeed: TextView

    private lateinit var uri: String
    private lateinit var title: String
    private var sourceId: Long = 0L
    private var resumePositionMs: Long = 0L
    private var surfaceReady = false

    private var controlsVisible = false
    private var playbackSpeed = 1.0f
    private val handler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable { hideControls() }
    private var isSeeking = false

    private val progressRunnable = object : Runnable {
        override fun run() {
            if (!isSeeking && ::mediaPlayer.isInitialized && mediaPlayer.isPlaying) {
                updateProgress(mediaPlayer.time, mediaPlayer.length)
            }
            handler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS)
        }
    }

    private val historySaveRunnable = object : Runnable {
        override fun run() {
            saveHistory()
            handler.postDelayed(this, HISTORY_SAVE_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        uri = intent.getStringExtra(EXTRA_URI) ?: run { finish(); return }
        title = intent.getStringExtra(EXTRA_TITLE)
            ?: uri.substringAfterLast('/').let { android.net.Uri.decode(it).substringBeforeLast('.') }
        sourceId = intent.getLongExtra(EXTRA_SOURCE_ID, 0L)

        setContentView(R.layout.activity_player)
        bindViews()
        setupSeekBar()
        setupButtons()
        tvTitle.text = title

        libVlc = LibVLC(this, arrayListOf(
            "--no-osd",
            "--network-caching=3000",
            "--file-caching=1000",
            "--live-caching=1000"
        ))
        mediaPlayer = MediaPlayer(libVlc)
        mediaPlayer.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> runOnUiThread {
                    btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                    // Seek to resume position once after first play
                    if (resumePositionMs > 0 && surfaceReady) {
                        mediaPlayer.time = resumePositionMs
                        resumePositionMs = 0L
                    }
                }
                MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped -> runOnUiThread {
                    btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                }
                MediaPlayer.Event.TimeChanged -> runOnUiThread {
                    if (!isSeeking) updateProgress(mediaPlayer.time, mediaPlayer.length)
                }
                MediaPlayer.Event.EndReached -> runOnUiThread {
                    clearHistory()
                    finish()
                }
            }
        }

        surfaceView.holder.addCallback(this)

        // Load existing history for resume
        lifecycleScope.launch {
            val existing = AppDatabase.get(this@PlayerActivity).watchHistoryDao().getByUri(uri)
            if (existing != null && existing.positionMs > 5_000L) {
                resumePositionMs = existing.positionMs
            }
        }

        prepareMedia(uri)
        showControls()
        handler.postDelayed(progressRunnable, PROGRESS_UPDATE_INTERVAL_MS)
        handler.postDelayed(historySaveRunnable, HISTORY_SAVE_INTERVAL_MS)
    }

    private fun prepareMedia(uri: String) {
        val media = Media(libVlc, android.net.Uri.parse(uri))
        media.setHWDecoderEnabled(true, false)
        media.addOption(":network-caching=3000")
        media.addOption(":clock-jitter=0")
        mediaPlayer.media = media
        media.release()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        val vout = mediaPlayer.vlcVout
        vout.setVideoSurface(holder.surface, holder)
        vout.attachViews()
        mediaPlayer.play()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        mediaPlayer.vlcVout.setWindowSize(w, h)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        mediaPlayer.vlcVout.detachViews()
    }

    private fun updateProgress(posMs: Long, durMs: Long) {
        if (durMs > 0) {
            seekBar.max = durMs.toInt()
            seekBar.progress = posMs.toInt()
        }
        tvPosition.text = msToTime(posMs)
        tvDuration.text = msToTime(durMs)
    }

    private fun msToTime(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    private fun saveHistory() {
        if (!::mediaPlayer.isInitialized) return
        val pos = mediaPlayer.time
        val dur = mediaPlayer.length
        if (pos < 5_000L || dur <= 0L) return
        lifecycleScope.launch {
            AppDatabase.get(this@PlayerActivity).watchHistoryDao().upsert(
                WatchHistoryEntity(
                    uri = uri,
                    title = title,
                    positionMs = pos,
                    durationMs = dur,
                    sourceId = sourceId
                )
            )
        }
    }

    private fun clearHistory() {
        lifecycleScope.launch {
            AppDatabase.get(this@PlayerActivity).watchHistoryDao().delete(uri)
        }
    }

    private fun setupSeekBar() {
        // SeekBar is display-only on TV — D-pad seeking handled via onKeyDown
        seekBar.isFocusable = false
        seekBar.isFocusableInTouchMode = false
    }

    private fun setupButtons() {
        btnPlayPause.setOnClickListener { togglePlayPause() }
        btnRewind.setOnClickListener { seek(-SEEK_STEP_MS) }
        btnFastForward.setOnClickListener { seek(SEEK_STEP_MS) }
        btnAudioTrack.setOnClickListener { cycleAudioTrack() }
        btnSubTrack.setOnClickListener { cycleSubtitleTrack() }
        tvSpeed.setOnClickListener { cycleSpeed() }
    }

    private fun togglePlayPause() {
        if (mediaPlayer.isPlaying) mediaPlayer.pause() else mediaPlayer.play()
        showControls()
    }

    private fun seek(deltaMs: Long) {
        val duration = mediaPlayer.length.takeIf { it > 0 } ?: return
        val newTime = (mediaPlayer.time + deltaMs).coerceIn(0L, duration)
        mediaPlayer.time = newTime
        showControls()
    }

    private fun cycleAudioTrack() {
        val tracks = mediaPlayer.audioTracks ?: return
        val current = mediaPlayer.audioTrack
        val idx = tracks.indexOfFirst { it.id == current }
        val next = tracks[(idx + 1) % tracks.size]
        mediaPlayer.audioTrack = next.id
        Toast.makeText(this, "Audio: ${next.name}", Toast.LENGTH_SHORT).show()
        showControls()
    }

    private fun cycleSubtitleTrack() {
        val tracks = mediaPlayer.spuTracks ?: emptyArray()
        val ids = intArrayOf(-1) + tracks.map { it.id }.toIntArray()
        val names = arrayOf("None") + tracks.map { it.name }.toTypedArray()
        val current = mediaPlayer.spuTrack
        val idx = ids.indexOfFirst { it == current }.takeIf { it >= 0 } ?: 0
        val nextIdx = (idx + 1) % ids.size
        mediaPlayer.spuTrack = ids[nextIdx]
        Toast.makeText(this, "Sub: ${names[nextIdx]}", Toast.LENGTH_SHORT).show()
        showControls()
    }

    private fun cycleSpeed() {
        val speeds = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        val idx = speeds.indexOfFirst { it >= playbackSpeed - 0.01f }
        playbackSpeed = speeds[(idx + 1) % speeds.size]
        mediaPlayer.rate = playbackSpeed
        tvSpeed.text = "${playbackSpeed}x"
        showControls()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showControls() {
        controlsLayout.visibility = View.VISIBLE
        controlsVisible = true
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, HIDE_CONTROLS_DELAY_MS)
    }

    private fun hideControls() {
        controlsLayout.visibility = View.GONE
        controlsVisible = false
    }

    // Intercept D-pad before any child View (SeekBar, buttons) can consume it
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val handled = onKeyDown(event.keyCode, event)
            if (handled) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (controlsVisible) togglePlayPause() else showControls(); true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { togglePlayPause(); true }
            KeyEvent.KEYCODE_MEDIA_PLAY -> { mediaPlayer.play(); showControls(); true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { mediaPlayer.pause(); showControls(); true }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                seek(SEEK_STEP_MS); true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                seek(-SEEK_STEP_MS); true
            }
            KeyEvent.KEYCODE_DPAD_UP -> { showControls(); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { showControls(); true }
            KeyEvent.KEYCODE_BACK -> { saveHistory(); finish(); true }
            else -> false
        }
    }

    private fun bindViews() {
        surfaceView = findViewById(R.id.surface_view)
        controlsLayout = findViewById(R.id.controls_layout)
        btnPlayPause = findViewById(R.id.btn_play_pause)
        btnRewind = findViewById(R.id.btn_rewind)
        btnFastForward = findViewById(R.id.btn_fast_forward)
        seekBar = findViewById(R.id.seek_bar)
        tvPosition = findViewById(R.id.tv_position)
        tvDuration = findViewById(R.id.tv_duration)
        tvTitle = findViewById(R.id.tv_title)
        btnAudioTrack = findViewById(R.id.btn_audio_track)
        btnSubTrack = findViewById(R.id.btn_sub_track)
        tvSpeed = findViewById(R.id.tv_speed)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        saveHistory()
        mediaPlayer.stop()
        mediaPlayer.vlcVout.detachViews()
        mediaPlayer.release()
        libVlc.release()
    }
}
