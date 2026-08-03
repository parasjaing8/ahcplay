package com.aihomecloud.ahcplayer.player

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
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
        private const val HIDE_SEEK_INDICATOR_DELAY_MS = 1500L
        private const val SEEK_STEP_MS = 10_000L
        private const val PROGRESS_UPDATE_INTERVAL_MS = 500L
        private const val HISTORY_SAVE_INTERVAL_MS = 30_000L
        private const val US_PER_MS = 1000L
    }

    private lateinit var libVlc: LibVLC
    lateinit var mediaPlayer: MediaPlayer
        private set

    private lateinit var surfaceView: SurfaceView
    private lateinit var controlsLayout: View
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnRewind: ImageButton
    private lateinit var btnFastForward: ImageButton
    private lateinit var btnAudioTrack: ImageButton
    private lateinit var btnSubTrack: ImageButton
    private lateinit var btnAspect: ImageButton
    private lateinit var btnMore: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var tvPosition: TextView
    private lateinit var tvDuration: TextView
    private lateinit var tvTitle: TextView
    private lateinit var seekIndicator: TextView

    private lateinit var playerMenu: PlayerMenu
    private lateinit var trackPanel: TrackPanel

    private lateinit var uri: String
    private lateinit var title: String
    private var sourceId: Long = 0L
    private var resumePositionMs: Long = 0L
    private var surfaceReady = false

    private var controlsVisible = false
    private var isSeeking = false
    private var isLocked = false

    var currentSpeed = 1.0f
        private set
    var repeatOne = false
        private set
    private var videoScale = VideoScale.BEST_FIT

    val abRepeat = AbRepeat()
    lateinit var sleepTimer: SleepTimer
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable { hideControls() }
    private val hideSeekIndicatorRunnable = Runnable { seekIndicator.visibility = View.GONE }

    private val progressRunnable = object : Runnable {
        override fun run() {
            if (!isSeeking && ::mediaPlayer.isInitialized && mediaPlayer.isPlaying) {
                updateProgress(mediaPlayer.time, mediaPlayer.length)
                abRepeat.onTick(mediaPlayer)
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

        sleepTimer = SleepTimer(handler) {
            toast(getString(R.string.sleep_off))
            saveHistory()
            finish()
        }

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
                    btnPlayPause.contentDescription = getString(R.string.player_pause)
                    if (resumePositionMs > 0 && surfaceReady) {
                        mediaPlayer.time = resumePositionMs
                        resumePositionMs = 0L
                    }
                }
                MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped -> runOnUiThread {
                    btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                    btnPlayPause.contentDescription = getString(R.string.player_play)
                }
                MediaPlayer.Event.TimeChanged -> runOnUiThread {
                    if (!isSeeking) updateProgress(mediaPlayer.time, mediaPlayer.length)
                }
                MediaPlayer.Event.EndReached -> runOnUiThread { onEndReached() }
            }
        }

        playerMenu = PlayerMenu(this, findViewById(R.id.panel_menu), findViewById(R.id.menu_container))
        trackPanel = TrackPanel(this, findViewById(R.id.panel_tracks), findViewById(R.id.tracks_container))

        surfaceView.holder.addCallback(this)

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

    private fun onEndReached() {
        if (repeatOne) {
            mediaPlayer.stop()
            prepareMedia(uri)
            mediaPlayer.play()
            return
        }
        clearHistory()
        finish()
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

    // ---- Menu / panel plumbing -------------------------------------------------

    fun onMenuItemSelected(id: PlayerMenu.Id) {
        playerMenu.hide()
        when (id) {
            PlayerMenu.Id.LOCK -> setLocked(true)
            PlayerMenu.Id.SLEEP_TIMER -> PlayerDialogs.sleepTimer(this) { minutes ->
                sleepTimer.set(minutes)
                toast(
                    if (minutes > 0) getString(R.string.sleep_set, minutes)
                    else getString(R.string.sleep_cancelled)
                )
            }
            PlayerMenu.Id.PLAYBACK_SPEED -> PlayerDialogs.speed(this, currentSpeed) { setSpeed(it) }
            PlayerMenu.Id.JUMP_TO_TIME -> PlayerDialogs.jumpToTime(this, mediaPlayer.length) { target ->
                mediaPlayer.time = target
                showSeekIndicator(target)
            }
            PlayerMenu.Id.EQUALIZER -> PlayerDialogs.equalizer(this, mediaPlayer)
            PlayerMenu.Id.REPEAT_MODE -> PlayerDialogs.repeatMode(this, repeatOne) { repeatOne = it }
            PlayerMenu.Id.VIDEO_INFO -> PlayerDialogs.videoInfo(this, mediaPlayer, title, uri)
            PlayerMenu.Id.AB_REPEAT -> toast(abRepeat.advance(mediaPlayer.time, this))
            PlayerMenu.Id.CHAPTERS -> PlayerDialogs.chapters(this, mediaPlayer) { target ->
                mediaPlayer.time = target
                showSeekIndicator(target)
            }
            PlayerMenu.Id.POPUP_PLAYER -> enterPopupPlayer()
            PlayerMenu.Id.BOOKMARKS,
            PlayerMenu.Id.SAVE_PLAYLIST,
            PlayerMenu.Id.PLAY_AS_AUDIO,
            PlayerMenu.Id.CONTROL_SETTINGS,
            PlayerMenu.Id.TIPS -> toast(getString(R.string.coming_soon))
        }
        showControls()
    }

    /** Picture-in-picture. Guarded because minSdk is 23 and TV devices have no PiP mode. */
    private fun enterPopupPlayer() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            toast(getString(R.string.not_supported_on_device))
            return
        }
        val track = mediaPlayer.currentVideoTrack
        val builder = PictureInPictureParams.Builder()
        if (track != null && track.width > 0 && track.height > 0) {
            builder.setAspectRatio(Rational(track.width, track.height))
        }
        closePanels()
        hideControls()
        enterPictureInPictureMode(builder.build())
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        // No room for chrome in the PiP window; restore it when the user expands again.
        if (isInPictureInPictureMode) {
            closePanels()
            hideControls()
        } else {
            showControls()
        }
    }

    fun adjustAudioDelay(deltaMs: Long) {
        mediaPlayer.audioDelay = mediaPlayer.audioDelay + deltaMs * US_PER_MS
    }

    fun adjustSubtitleDelay(deltaMs: Long) {
        mediaPlayer.spuDelay = mediaPlayer.spuDelay + deltaMs * US_PER_MS
    }

    fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun setSpeed(speed: Float) {
        currentSpeed = speed
        mediaPlayer.rate = speed
    }

    private fun setLocked(locked: Boolean) {
        isLocked = locked
        if (locked) {
            hideControls()
            toast(getString(R.string.locked_toast))
        } else {
            toast(getString(R.string.unlocked_toast))
            showControls()
        }
    }

    private fun anyPanelVisible(): Boolean = playerMenu.isVisible || trackPanel.isVisible

    private fun closePanels() {
        playerMenu.hide()
        trackPanel.hide()
    }

    // ---- Playback ---------------------------------------------------------------

    private fun updateProgress(posMs: Long, durMs: Long) {
        if (durMs > 0) {
            seekBar.max = durMs.toInt()
            seekBar.progress = posMs.toInt()
        }
        tvPosition.text = formatTime(posMs)
        tvDuration.text = formatTime(durMs)
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
        seekBar.isFocusable = false
        seekBar.isFocusableInTouchMode = false
    }

    private fun setupButtons() {
        btnPlayPause.setOnClickListener { togglePlayPause() }
        btnRewind.setOnClickListener { seek(-SEEK_STEP_MS) }
        btnFastForward.setOnClickListener { seek(SEEK_STEP_MS) }
        btnAudioTrack.setOnClickListener { openTracks(focusSubtitles = false) }
        btnSubTrack.setOnClickListener { openTracks(focusSubtitles = true) }
        btnAspect.setOnClickListener {
            PlayerDialogs.videoScale(this, videoScale) { picked ->
                videoScale = picked
                picked.apply(mediaPlayer, surfaceView.width, surfaceView.height)
                toast(picked.label)
            }
        }
        btnMore.setOnClickListener { openMenu() }

        // Touch devices get no key events, so the video surface itself toggles the overlay.
        surfaceView.setOnClickListener { onVideoTapped() }
    }

    private fun onVideoTapped() {
        when {
            isLocked -> toast(getString(R.string.locked_toast))
            anyPanelVisible() -> { closePanels(); showControls() }
            controlsVisible -> hideControls()
            else -> showControls()
        }
    }

    /** Panels replace the transport bar rather than stacking over it. */
    private fun openMenu() {
        hideControls()
        playerMenu.show()
    }

    private fun openTracks(focusSubtitles: Boolean) {
        hideControls()
        trackPanel.show(focusSubtitles)
    }

    private fun togglePlayPause() {
        if (mediaPlayer.isPlaying) mediaPlayer.pause() else mediaPlayer.play()
        showControls()
    }

    private fun seek(deltaMs: Long) {
        val duration = mediaPlayer.length.takeIf { it > 0 } ?: return
        val newTime = (mediaPlayer.time + deltaMs).coerceIn(0L, duration)
        mediaPlayer.time = newTime
        showSeekIndicator(newTime)
    }

    private fun showSeekIndicator(posMs: Long) {
        seekIndicator.text = formatTime(posMs)
        seekIndicator.visibility = View.VISIBLE
        handler.removeCallbacks(hideSeekIndicatorRunnable)
        handler.postDelayed(hideSeekIndicatorRunnable, HIDE_SEEK_INDICATOR_DELAY_MS)
    }

    private fun showControls() {
        if (isLocked) return
        val wasHidden = !controlsVisible
        controlsLayout.visibility = View.VISIBLE
        controlsVisible = true
        if (wasHidden) {
            controlsLayout.post { btnPlayPause.requestFocus() }
        }
        resetHideTimer()
    }

    private fun hideControls() {
        controlsLayout.visibility = View.GONE
        controlsVisible = false
    }

    private fun resetHideTimer() {
        handler.removeCallbacks(hideControlsRunnable)
        // Panels stay open until dismissed; only the transport bar auto-hides.
        if (!anyPanelVisible()) handler.postDelayed(hideControlsRunnable, HIDE_CONTROLS_DELAY_MS)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            // Locked: swallow everything except the unlock gesture (Back).
            if (isLocked) {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    setLocked(false)
                } else {
                    toast(getString(R.string.locked_toast))
                }
                return true
            }

            // Back closes an open panel before it leaves the player.
            if (event.keyCode == KeyEvent.KEYCODE_BACK && anyPanelVisible()) {
                closePanels()
                showControls()
                return true
            }

            when (event.keyCode) {
                KeyEvent.KEYCODE_BACK -> { saveHistory(); finish(); return true }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { togglePlayPause(); return true }
                KeyEvent.KEYCODE_MEDIA_PLAY -> { mediaPlayer.play(); showControls(); return true }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> { mediaPlayer.pause(); showControls(); return true }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { seek(SEEK_STEP_MS); return true }
                KeyEvent.KEYCODE_MEDIA_REWIND -> { seek(-SEEK_STEP_MS); return true }
            }

            if (anyPanelVisible()) return super.dispatchKeyEvent(event)

            if (!controlsVisible) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_RIGHT -> seek(SEEK_STEP_MS)
                    KeyEvent.KEYCODE_DPAD_LEFT -> seek(-SEEK_STEP_MS)
                    else -> showControls()
                }
                return true
            }
            resetHideTimer()
        }
        return super.dispatchKeyEvent(event)
    }

    private fun bindViews() {
        surfaceView    = findViewById(R.id.surface_view)
        controlsLayout = findViewById(R.id.controls_layout)
        btnPlayPause   = findViewById(R.id.btn_play_pause)
        btnRewind      = findViewById(R.id.btn_rewind)
        btnFastForward = findViewById(R.id.btn_fast_forward)
        btnAudioTrack  = findViewById(R.id.btn_audio_track)
        btnSubTrack    = findViewById(R.id.btn_sub_track)
        btnAspect      = findViewById(R.id.btn_aspect)
        btnMore        = findViewById(R.id.btn_more)
        seekBar        = findViewById(R.id.seek_bar)
        tvPosition     = findViewById(R.id.tv_position)
        tvDuration     = findViewById(R.id.tv_duration)
        tvTitle        = findViewById(R.id.tv_title)
        seekIndicator  = findViewById(R.id.seek_indicator)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        sleepTimer.cancel()
        saveHistory()
        mediaPlayer.stop()
        mediaPlayer.vlcVout.detachViews()
        mediaPlayer.release()
        libVlc.release()
    }
}
