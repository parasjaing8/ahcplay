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
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val uri: String,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val sourceId: Long,
    val lastWatchedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "media_metadata")
data class MediaMetadataEntity(
    @PrimaryKey val filename: String,
    val tmdbId: Int?,
    val displayTitle: String,
    val year: Int?,
    val posterUrl: String?,
    val overview: String?,
    val cachedAt: Long = System.currentTimeMillis()
)
