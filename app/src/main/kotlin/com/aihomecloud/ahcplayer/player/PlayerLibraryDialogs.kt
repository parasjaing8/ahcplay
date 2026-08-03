package com.aihomecloud.ahcplayer.player

import android.app.AlertDialog
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.aihomecloud.ahcplayer.R
import com.aihomecloud.ahcplayer.data.db.AppDatabase
import com.aihomecloud.ahcplayer.data.db.BookmarkDao
import com.aihomecloud.ahcplayer.data.db.BookmarkEntity
import com.aihomecloud.ahcplayer.data.db.PlaylistEntity
import com.aihomecloud.ahcplayer.data.db.PlaylistItemEntity
import kotlinx.coroutines.launch

/**
 * Bookmarks and Save Playlist dialogs. Kept out of PlayerActivity/PlayerMenuActions so
 * the player class stays focused on lifecycle, playback and input.
 */
object PlayerLibraryDialogs {

    /** Loads this file's bookmarks and shows the list/add/delete dialog. */
    fun bookmarks(activity: PlayerActivity) {
        activity.lifecycleScope.launch {
            val dao = AppDatabase.get(activity).bookmarkDao()
            showBookmarksDialog(activity, dao, dao.getForUri(activity.uri))
        }
    }

    private fun showBookmarksDialog(activity: PlayerActivity, dao: BookmarkDao, bookmarks: List<BookmarkEntity>) {
        val inflater = LayoutInflater.from(activity)
        val container = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(activity).apply { addView(container) }

        lateinit var dialog: AlertDialog

        if (bookmarks.isEmpty()) {
            container.addView(
                TextView(activity).apply {
                    text = activity.getString(R.string.bookmarks_empty)
                    setTextColor(0xB3FFFFFF.toInt())
                    setPadding(48, 24, 48, 24)
                }
            )
        }

        bookmarks.forEach { bookmark ->
            val row = inflater.inflate(R.layout.item_bookmark_row, container, false)
            row.findViewById<TextView>(R.id.bookmark_label).text = bookmark.label
            row.findViewById<TextView>(R.id.bookmark_time).text = formatTime(bookmark.positionMs)
            row.contentDescription = "${bookmark.label}, ${formatTime(bookmark.positionMs)}"
            row.setOnClickListener {
                activity.mediaPlayer.time = bookmark.positionMs
                activity.showSeekIndicator(bookmark.positionMs)
                dialog.dismiss()
            }
            row.findViewById<ImageButton>(R.id.bookmark_delete).apply {
                contentDescription = activity.getString(R.string.bookmark_delete_cd)
                setOnClickListener {
                    activity.lifecycleScope.launch {
                        dao.delete(bookmark)
                        dialog.dismiss()
                        bookmarks(activity)
                    }
                }
            }
            container.addView(row)
        }

        dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.bookmarks_title)
            .setView(scroll)
            .setPositiveButton(R.string.bookmark_add) { _, _ ->
                activity.lifecycleScope.launch {
                    val posMs = activity.mediaPlayer.time
                    dao.insert(
                        BookmarkEntity(uri = activity.uri, positionMs = posMs, label = formatTime(posMs))
                    )
                    activity.toast(activity.getString(R.string.bookmark_added))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Prompts for a name and saves the currently playing item as a one-item playlist. */
    fun savePlaylist(activity: PlayerActivity) {
        val input = EditText(activity).apply {
            hint = activity.getString(R.string.playlist_name_hint)
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.save_playlist_title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    activity.toast(activity.getString(R.string.playlist_name_invalid))
                    return@setPositiveButton
                }
                activity.lifecycleScope.launch {
                    val dao = AppDatabase.get(activity).playlistDao()
                    val playlistId = dao.insertPlaylist(PlaylistEntity(name = name))
                    dao.insertItem(
                        PlaylistItemEntity(
                            playlistId = playlistId,
                            uri = activity.uri,
                            title = activity.title,
                            position = 0
                        )
                    )
                    activity.toast(activity.getString(R.string.playlist_saved, name))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
