package com.aihomecloud.ahcplayer.player

import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import com.aihomecloud.ahcplayer.R

/**
 * Overflow-menu action handling, kept out of PlayerActivity so that class stays
 * focused on lifecycle, playback and input.
 */
internal fun PlayerActivity.onMenuItemSelected(id: PlayerMenu.Id) {
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
private fun PlayerActivity.enterPopupPlayer() {
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
