package com.aihomecloud.ahcplayer.player

import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.aihomecloud.ahcplayer.R

/**
 * Bottom panel holding the Audio and Subtitles track lists plus their delay controls.
 * Both sections collapse independently, matching the layout VLC uses for track selection.
 */
class TrackPanel(
    private val activity: PlayerActivity,
    private val panel: View,
    private val container: LinearLayout
) {

    private companion object {
        /** libVLC expresses track delay in microseconds; the UI works in milliseconds. */
        const val US_PER_MS = 1000L
        const val DELAY_STEP_MS = 50L
    }

    private var audioExpanded = true
    private var subsExpanded = false

    val isVisible: Boolean get() = panel.visibility == View.VISIBLE

    fun show(focusSubtitles: Boolean = false) {
        if (focusSubtitles) {
            subsExpanded = true
            audioExpanded = false
        }
        build()
        panel.visibility = View.VISIBLE
        container.getChildAt(0)?.requestFocus()
    }

    fun hide() {
        panel.visibility = View.GONE
    }

    private fun rebuild() {
        val focused = container.focusedChild
        val index = if (focused != null) container.indexOfChild(focused) else 0
        build()
        container.getChildAt(index.coerceIn(0, container.childCount - 1))?.requestFocus()
    }

    private fun build() {
        container.removeAllViews()
        val player = activity.mediaPlayer

        addSection(R.string.tracks_audio, audioExpanded) {
            audioExpanded = !audioExpanded
            rebuild()
        }
        if (audioExpanded) {
            addDelayRow(
                labelRes = R.string.tracks_audio_delay,
                currentMs = player.audioDelay / US_PER_MS
            ) { deltaMs -> activity.adjustAudioDelay(deltaMs); rebuild() }

            val audioTracks = player.audioTracks ?: emptyArray()
            addTrackRow(activity.getString(R.string.tracks_disable), player.audioTrack == -1) {
                player.audioTrack = -1
                rebuild()
            }
            audioTracks.filter { it.id != -1 }.forEach { track ->
                addTrackRow(track.name, player.audioTrack == track.id) {
                    player.audioTrack = track.id
                    rebuild()
                }
            }
        }

        addDivider()

        addSection(R.string.tracks_subtitles, subsExpanded) {
            subsExpanded = !subsExpanded
            rebuild()
        }
        if (subsExpanded) {
            addDelayRow(
                labelRes = R.string.tracks_subtitle_delay,
                currentMs = player.spuDelay / US_PER_MS
            ) { deltaMs -> activity.adjustSubtitleDelay(deltaMs); rebuild() }

            val spuTracks = player.spuTracks ?: emptyArray()
            addTrackRow(activity.getString(R.string.tracks_none), player.spuTrack == -1) {
                player.spuTrack = -1
                rebuild()
            }
            spuTracks.filter { it.id != -1 }.forEach { track ->
                addTrackRow(track.name, player.spuTrack == track.id) {
                    player.spuTrack = track.id
                    rebuild()
                }
            }
        }
    }

    private fun inflater(): LayoutInflater = LayoutInflater.from(activity)

    private fun addSection(titleRes: Int, expanded: Boolean, onToggle: () -> Unit) {
        val view = inflater().inflate(R.layout.item_track_section, container, false)
        view.findViewById<TextView>(R.id.section_title).setText(titleRes)
        view.findViewById<ImageView>(R.id.section_chevron).setImageResource(
            if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more
        )
        view.contentDescription = activity.getString(titleRes)
        view.setOnClickListener { onToggle() }
        container.addView(view)
    }

    private fun addTrackRow(label: String, selected: Boolean, onSelect: () -> Unit) {
        val view = inflater().inflate(R.layout.item_track_row, container, false)
        view.findViewById<TextView>(R.id.track_label).apply {
            text = label
            alpha = if (selected) 1f else 0.75f
        }
        view.findViewById<ImageView>(R.id.track_check).visibility =
            if (selected) View.VISIBLE else View.INVISIBLE
        view.contentDescription = label
        view.setOnClickListener { onSelect() }
        container.addView(view)
    }

    /**
     * Delay row: D-pad left/right (or tap) nudges by [DELAY_STEP_MS], centre press resets to zero.
     */
    private fun addDelayRow(labelRes: Int, currentMs: Long, onAdjust: (Long) -> Unit) {
        val view = inflater().inflate(R.layout.item_delay_row, container, false)
        view.findViewById<TextView>(R.id.delay_label).setText(labelRes)
        view.findViewById<TextView>(R.id.delay_value).text =
            activity.getString(R.string.delay_value, currentMs)
        view.contentDescription =
            "${activity.getString(labelRes)} ${activity.getString(R.string.delay_value, currentMs)}"
        view.setOnClickListener { onAdjust(-currentMs) }
        view.setOnKeyListener { _, keyCode, event ->
            if (event.action != android.view.KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> { onAdjust(DELAY_STEP_MS); true }
                android.view.KeyEvent.KEYCODE_DPAD_LEFT -> { onAdjust(-DELAY_STEP_MS); true }
                else -> false
            }
        }
        container.addView(view)
    }

    private fun addDivider() {
        val divider = View(activity).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(0x33FFFFFF)
        }
        container.addView(divider)
    }
}
