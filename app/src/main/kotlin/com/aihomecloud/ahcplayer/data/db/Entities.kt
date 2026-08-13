package com.aihomecloud.ahcplayer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val host: String,
    val share: String,
    val port: Int = 445,
    val sourceType: String = "SMB",
    val username: String = "",
    val hasPin: Boolean = false,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "watch_history", primaryKeys = ["uri", "sourceId"])
data class WatchHistoryEntity(
    val uri: String,
    val sourceId: Long,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val entryId: Int? = null,
    val clientUpdatedAt: Long = 0L,
    val version: Int = 0,
    val dirty: Boolean = false,
    val lastWatchedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "media_metadata")
data class MediaMetadataEntity(
    @PrimaryKey val filename: String,
    val tmdbId: Int?,
    val displayTitle: String,
    val year: Int?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val genre: String?,
    val mediaType: String?,
    val overview: String?,
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "bookmark")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val positionMs: Long,
    val label: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_item")
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val uri: String,
    val title: String,
    val position: Int
)

@Entity(tableName = "pending_playback_report", primaryKeys = ["entryId", "sourceId"])
data class PendingPlaybackReportEntity(
    val entryId: Int,
    val sourceId: Long,
    val uri: String,
    val positionSeconds: Double,
    val durationSeconds: Double?,
    val clientUpdatedAt: Long,
    val attempts: Int = 0
)
