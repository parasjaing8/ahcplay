package com.aihomecloud.ahcplayer.player

import android.content.pm.PackageManager
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.aihomecloud.ahcplayer.R

/**
 * Right-anchored overflow menu, mirroring the option set VLC exposes on its player.
 * Rows are data-driven so the list stays declarative and D-pad focus order follows
 * the natural child order of [container].
 */
class PlayerMenu(
    private val activity: PlayerActivity,
    private val panel: View,
    private val container: LinearLayout
) {

    enum class Id {
        LOCK, SLEEP_TIMER, PLAYBACK_SPEED, JUMP_TO_TIME, EQUALIZER, PLAY_AS_AUDIO,
        POPUP_PLAYER, REPEAT_MODE, VIDEO_INFO, CHAPTERS, BOOKMARKS, AB_REPEAT,
        SAVE_PLAYLIST, CONTROL_SETTINGS, TIPS
    }

    private data class Row(
        val id: Id,
        @DrawableRes val icon: Int,
        @StringRes val label: Int
    )

    private val rows = listOf(
        Row(Id.LOCK, R.drawable.ic_lock, R.string.menu_lock),
        Row(Id.SLEEP_TIMER, R.drawable.ic_sleep_timer, R.string.menu_sleep_timer),
        Row(Id.PLAYBACK_SPEED, R.drawable.ic_speed, R.string.menu_playback_speed),
        Row(Id.JUMP_TO_TIME, R.drawable.ic_jump_time, R.string.menu_jump_to_time),
        Row(Id.EQUALIZER, R.drawable.ic_equalizer, R.string.menu_equalizer),
        Row(Id.PLAY_AS_AUDIO, R.drawable.ic_play_as_audio, R.string.menu_play_as_audio),
        Row(Id.POPUP_PLAYER, R.drawable.ic_popup, R.string.menu_popup_player),
        Row(Id.REPEAT_MODE, R.drawable.ic_repeat, R.string.menu_repeat_mode),
        Row(Id.VIDEO_INFO, R.drawable.ic_info, R.string.menu_video_info),
        Row(Id.CHAPTERS, R.drawable.ic_chapter, R.string.menu_go_to_chapter),
        Row(Id.BOOKMARKS, R.drawable.ic_bookmark, R.string.menu_bookmarks),
        Row(Id.AB_REPEAT, R.drawable.ic_ab_repeat, R.string.menu_ab_repeat),
        Row(Id.SAVE_PLAYLIST, R.drawable.ic_save_playlist, R.string.menu_save_playlist),
        Row(Id.CONTROL_SETTINGS, R.drawable.ic_control_settings, R.string.menu_control_settings),
        Row(Id.TIPS, R.drawable.ic_tips, R.string.menu_video_tips)
    )

    val isVisible: Boolean get() = panel.visibility == View.VISIBLE

    /** PiP needs API 26+; the Fire TV target (API 25) and older phones have no such mode. */
    private val pipSupported: Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private fun supported(id: Id): Boolean = when (id) {
        Id.POPUP_PLAYER -> pipSupported
        else -> true
    }

    fun show() {
        build()
        panel.visibility = View.VISIBLE
        container.getChildAt(0)?.requestFocus()
    }

    fun hide() {
        panel.visibility = View.GONE
    }

    private fun build() {
        container.removeAllViews()
        val inflater = LayoutInflater.from(activity)
        rows.forEach { row ->
            val view = inflater.inflate(R.layout.item_player_menu, container, false)
            val enabled = supported(row.id)
            view.findViewById<ImageView>(R.id.menu_icon).setImageResource(row.icon)
            view.findViewById<TextView>(R.id.menu_label).apply {
                setText(row.label)
                alpha = if (enabled) 1f else 0.4f
            }
            view.findViewById<TextView>(R.id.menu_value).text = valueFor(row.id, enabled)
            view.contentDescription = activity.getString(row.label)
            view.isFocusable = enabled
            view.isClickable = enabled
            if (enabled) view.setOnClickListener { activity.onMenuItemSelected(row.id) }
            container.addView(view)
        }
    }

    /** Trailing status text, so state is visible without opening the sub-dialog. */
    private fun valueFor(id: Id, enabled: Boolean): String = when {
        !enabled -> activity.getString(R.string.not_supported_on_device)
        id == Id.PLAYBACK_SPEED -> "%.2fx".format(activity.currentSpeed)
        id == Id.REPEAT_MODE -> activity.getString(
            if (activity.repeatOne) R.string.repeat_one else R.string.repeat_none
        )
        id == Id.AB_REPEAT -> activity.abRepeat.statusLabel(activity)
        id == Id.SLEEP_TIMER -> activity.sleepTimer.statusLabel(activity)
        else -> ""
    }
}
