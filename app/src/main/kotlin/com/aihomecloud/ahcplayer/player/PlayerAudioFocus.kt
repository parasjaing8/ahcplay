package com.aihomecloud.ahcplayer.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

/**
 * Audio focus plus the "becoming noisy" broadcast.
 *
 * Without this the player keeps playing at full volume over an incoming call or
 * another app's notification, and keeps blasting into the room when headphones are
 * unplugged — both are outright bugs rather than missing polish.
 */
class PlayerAudioFocus(
    private val context: Context,
    private val onPause: () -> Unit,
    private val onResume: () -> Unit,
    private val onDuck: (ducked: Boolean) -> Unit
) {

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** Set when focus loss paused us, so we only auto-resume what we auto-paused. */
    private var pausedByFocusLoss = false
    private var focusRequest: AudioFocusRequest? = null
    private var noisyReceiverRegistered = false

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Another app took focus permanently — stay paused, do not auto-resume.
                pausedByFocusLoss = false
                onPause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                pausedByFocusLoss = true
                onPause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onDuck(true)
            AudioManager.AUDIOFOCUS_GAIN -> {
                onDuck(false)
                if (pausedByFocusLoss) {
                    pausedByFocusLoss = false
                    onResume()
                }
            }
        }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                pausedByFocusLoss = false
                onPause()
            }
        }
    }

    fun request(): Boolean {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build()
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(focusListener)
                .build()
                .also { focusRequest = it }
                .let { audioManager.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        registerNoisyReceiver()
        return granted == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    fun abandon() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
        unregisterNoisyReceiver()
    }

    private fun registerNoisyReceiver() {
        if (noisyReceiverRegistered) return
        context.registerReceiver(
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        )
        noisyReceiverRegistered = true
    }

    private fun unregisterNoisyReceiver() {
        if (!noisyReceiverRegistered) return
        runCatching { context.unregisterReceiver(noisyReceiver) }
        noisyReceiverRegistered = false
    }

    /** Current music-stream volume as a 0..1 fraction, for the swipe gesture. */
    fun volumeFraction(): Float {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return 0f
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    /** Nudges music volume by a 0..1 fraction delta; returns the new fraction. */
    fun adjustVolume(deltaFraction: Float): Float {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return 0f
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val target = (current + (deltaFraction * max)).toInt().coerceIn(0, max)
        if (target != current) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        }
        return target.toFloat() / max
    }
}
