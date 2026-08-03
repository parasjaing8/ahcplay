package com.aihomecloud.ahcplayer.player

import android.app.AlertDialog
import android.os.Handler
import android.text.InputType
import android.widget.EditText
import com.aihomecloud.ahcplayer.R
import org.videolan.libvlc.MediaPlayer

/** Formats a millisecond position as h:mm:ss / m:ss. */
internal fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/**
 * Stops playback after a chosen interval. Mirrors VLC's sleep timer, but tied to the
 * activity's own handler so it dies with the player rather than outliving it.
 */
class SleepTimer(private val handler: Handler, private val onFire: () -> Unit) {

    private var scheduledAtMs: Long = 0L
    private val runnable = Runnable {
        scheduledAtMs = 0L
        onFire()
    }

    val isActive: Boolean get() = scheduledAtMs > 0L

    fun set(minutes: Int) {
        cancel()
        if (minutes <= 0) return
        scheduledAtMs = System.currentTimeMillis() + minutes * 60_000L
        handler.postDelayed(runnable, minutes * 60_000L)
    }

    fun cancel() {
        handler.removeCallbacks(runnable)
        scheduledAtMs = 0L
    }

    fun statusLabel(activity: PlayerActivity): String {
        if (!isActive) return activity.getString(R.string.sleep_off)
        val remainingMin = ((scheduledAtMs - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0)
        return "${remainingMin + 1} min"
    }
}

/**
 * A-B repeat. Point A is captured on the first activation, B on the second; once both
 * are set, playback loops back to A whenever it passes B. Third activation clears it.
 */
class AbRepeat {

    var pointA: Long = -1L
        private set
    var pointB: Long = -1L
        private set

    val isArmed: Boolean get() = pointA >= 0L && pointB > pointA

    /** Advances the A -> B -> cleared cycle. Returns a message describing the new state. */
    fun advance(currentMs: Long, activity: PlayerActivity): String = when {
        pointA < 0L -> {
            pointA = currentMs
            activity.getString(R.string.ab_set_a, formatTime(currentMs))
        }
        pointB < 0L && currentMs > pointA -> {
            pointB = currentMs
            activity.getString(R.string.ab_set_b)
        }
        else -> {
            clear()
            activity.getString(R.string.ab_cleared)
        }
    }

    fun clear() {
        pointA = -1L
        pointB = -1L
    }

    /** Called on each time update; seeks back to A once B is passed. */
    fun onTick(player: MediaPlayer) {
        if (isArmed && player.time >= pointB) player.time = pointA
    }

    fun statusLabel(activity: PlayerActivity): String = when {
        isArmed -> "${formatTime(pointA)} - ${formatTime(pointB)}"
        pointA >= 0L -> "A: ${formatTime(pointA)}"
        else -> activity.getString(R.string.sleep_off)
    }
}

/** Video sizing modes, mapped onto libVLC's aspect-ratio + scale pair. */
enum class VideoScale(val label: String) {
    BEST_FIT("Best fit"),
    FIT_SCREEN("Fit screen"),
    FILL("Fill"),
    RATIO_16_9("16:9"),
    RATIO_4_3("4:3"),
    ORIGINAL("Original");

    fun apply(player: MediaPlayer, screenW: Int, screenH: Int) {
        when (this) {
            BEST_FIT -> { player.aspectRatio = null; player.scale = 0f }
            FIT_SCREEN -> { player.aspectRatio = "$screenW:$screenH"; player.scale = 0f }
            FILL -> { player.aspectRatio = null; player.scale = fillScale(player, screenW, screenH) }
            RATIO_16_9 -> { player.aspectRatio = "16:9"; player.scale = 0f }
            RATIO_4_3 -> { player.aspectRatio = "4:3"; player.scale = 0f }
            ORIGINAL -> { player.aspectRatio = null; player.scale = 1f }
        }
    }

    private fun fillScale(player: MediaPlayer, screenW: Int, screenH: Int): Float {
        val vt = player.currentVideoTrack ?: return 0f
        if (vt.width <= 0 || vt.height <= 0) return 0f
        val videoAspect = vt.width.toFloat() / vt.height
        val screenAspect = screenW.toFloat() / screenH
        return if (videoAspect > screenAspect) screenAspect / videoAspect else videoAspect / screenAspect
    }
}

/** Dialogs shared by the overflow menu. Kept free of state so the activity stays the owner. */
object PlayerDialogs {

    private val SPEEDS = floatArrayOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 3.0f, 4.0f)
    private val SLEEP_OPTIONS = intArrayOf(0, 15, 30, 45, 60, 90, 120)

    fun speed(activity: PlayerActivity, current: Float, onPick: (Float) -> Unit) {
        val labels = SPEEDS.map { "%.2fx".format(it) }.toTypedArray()
        val checked = SPEEDS.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }
        AlertDialog.Builder(activity)
            .setTitle(R.string.speed_title)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                onPick(SPEEDS[which])
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun jumpToTime(activity: PlayerActivity, durationMs: Long, onPick: (Long) -> Unit) {
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_DATETIME
            hint = activity.getString(R.string.jump_hint)
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.jump_title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val target = parseTime(input.text.toString())
                if (target == null || target > durationMs) {
                    activity.toast(activity.getString(R.string.jump_invalid))
                } else {
                    onPick(target)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Accepts ss, mm:ss or hh:mm:ss. Returns null when the text is not a valid time. */
    internal fun parseTime(raw: String): Long? {
        val parts = raw.trim().split(":")
        if (parts.isEmpty() || parts.size > 3) return null
        val numbers = parts.map { it.toIntOrNull() ?: return null }
        if (numbers.any { it < 0 }) return null
        val seconds = when (numbers.size) {
            1 -> numbers[0].toLong()
            2 -> numbers[0] * 60L + numbers[1]
            else -> numbers[0] * 3600L + numbers[1] * 60L + numbers[2]
        }
        return seconds * 1000L
    }

    fun sleepTimer(activity: PlayerActivity, onPick: (Int) -> Unit) {
        val labels = SLEEP_OPTIONS.map {
            if (it == 0) activity.getString(R.string.sleep_off) else "$it min"
        }.toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle(R.string.sleep_title)
            .setItems(labels) { _, which -> onPick(SLEEP_OPTIONS[which]) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun equalizer(activity: PlayerActivity, player: MediaPlayer) {
        val presetCount = MediaPlayer.Equalizer.getPresetCount()
        val labels = arrayOf(activity.getString(R.string.eq_off)) +
            (0 until presetCount).map { MediaPlayer.Equalizer.getPresetName(it) }
        AlertDialog.Builder(activity)
            .setTitle(R.string.eq_title)
            .setItems(labels) { _, which ->
                if (which == 0) {
                    player.setEqualizer(null)
                } else {
                    player.setEqualizer(MediaPlayer.Equalizer.createFromPreset(which - 1))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun repeatMode(activity: PlayerActivity, current: Boolean, onPick: (Boolean) -> Unit) {
        val labels = arrayOf(
            activity.getString(R.string.repeat_none),
            activity.getString(R.string.repeat_one)
        )
        AlertDialog.Builder(activity)
            .setTitle(R.string.repeat_title)
            .setSingleChoiceItems(labels, if (current) 1 else 0) { dialog, which ->
                onPick(which == 1)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun videoScale(activity: PlayerActivity, current: VideoScale, onPick: (VideoScale) -> Unit) {
        val values = VideoScale.entries.toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle(R.string.player_video_size)
            .setSingleChoiceItems(values.map { it.label }.toTypedArray(), current.ordinal) { dialog, which ->
                onPick(values[which])
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Chapter list for the current title; libVLC reports none for most plain video files. */
    fun chapters(activity: PlayerActivity, player: MediaPlayer, onPick: (Long) -> Unit) {
        val chapters = player.getChapters(player.title) ?: emptyArray()
        if (chapters.isEmpty()) {
            activity.toast(activity.getString(R.string.chapters_none))
            return
        }
        val labels = chapters.mapIndexed { index, chapter ->
            val name = chapter.name?.takeIf { it.isNotBlank() } ?: "Chapter ${index + 1}"
            "$name  (${formatTime(chapter.timeOffset)})"
        }.toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle(R.string.chapters_title)
            .setSingleChoiceItems(labels, player.chapter.coerceAtLeast(0)) { dialog, which ->
                onPick(chapters[which].timeOffset)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun videoInfo(activity: PlayerActivity, player: MediaPlayer, title: String, uri: String) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.video_info_title)
            .setMessage(buildInfoText(player, title, uri))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun buildInfoText(player: MediaPlayer, title: String, uri: String): String {
        val sb = StringBuilder()
        sb.appendLine(title).appendLine()
        sb.appendLine("Source: $uri")
        sb.appendLine("Duration: ${formatTime(player.length)}")
        player.currentVideoTrack?.let { vt ->
            sb.appendLine()
            sb.appendLine("Video: ${vt.width}x${vt.height}")
            if (vt.frameRateDen > 0) {
                sb.appendLine("Frame rate: %.2f fps".format(vt.frameRateNum.toFloat() / vt.frameRateDen))
            }
        }
        val audioCount = player.audioTracks?.count { it.id != -1 } ?: 0
        val spuCount = player.spuTracks?.count { it.id != -1 } ?: 0
        sb.appendLine()
        sb.appendLine("Audio tracks: $audioCount")
        sb.append("Subtitle tracks: $spuCount")
        return sb.toString()
    }
}
