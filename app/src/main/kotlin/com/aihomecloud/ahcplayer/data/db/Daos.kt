package com.aihomecloud.ahcplayer.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources ORDER BY createdAt ASC")
    fun getAll(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources WHERE host = :host AND username = :username LIMIT 1")
    suspend fun getByHostAndUsername(host: String, username: String): SourceEntity?

    @Query("SELECT * FROM sources WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(source: SourceEntity): Long

    @Delete
    suspend fun delete(source: SourceEntity)

    @Query("UPDATE sources SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)
}

@Dao
interface MediaMetadataDao {
    @Query("SELECT * FROM media_metadata WHERE filename = :filename LIMIT 1")
    suspend fun get(filename: String): MediaMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MediaMetadataEntity)

    @Query("DELETE FROM media_metadata")
    suspend fun deleteAll()

    @Query("SELECT backdropUrl FROM media_metadata WHERE backdropUrl IS NOT NULL ORDER BY RANDOM() LIMIT :limit")
    fun getRandomBackdropsFlow(limit: Int = 6): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM media_metadata WHERE mediaType = 'Movie'")
    fun countMoviesFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM media_metadata WHERE mediaType = 'Series'")
    fun countShowsFlow(): Flow<Int>
}

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY lastWatchedAt DESC LIMIT 50")
    fun getRecent(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE sourceId = :sourceId ORDER BY lastWatchedAt DESC LIMIT 20")
    fun getRecentBySource(sourceId: Long): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE uri = :uri AND sourceId = :sourceId LIMIT 1")
    suspend fun getByUriAndSource(uri: String, sourceId: Long): WatchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WatchHistoryEntity)

    @Query("DELETE FROM watch_history WHERE uri = :uri AND sourceId = :sourceId")
    suspend fun delete(uri: String, sourceId: Long)

    @Query("DELETE FROM watch_history")
    suspend fun deleteAll()
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmark WHERE uri = :uri ORDER BY positionMs ASC")
    suspend fun getForUri(uri: String): List<BookmarkEntity>

    @Insert
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Delete
    suspend fun delete(bookmark: BookmarkEntity)
}

@Dao
interface PlaylistDao {
    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Insert
    suspend fun insertItem(item: PlaylistItemEntity): Long

    @Query("SELECT * FROM playlist ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlist_item WHERE playlistId = :playlistId ORDER BY position ASC")
    fun getItems(playlistId: Long): Flow<List<PlaylistItemEntity>>
}

@Dao
interface PendingPlaybackReportDao {
    @Query("SELECT * FROM pending_playback_report")
    suspend fun getAll(): List<PendingPlaybackReportEntity>

    @Query("SELECT * FROM pending_playback_report WHERE sourceId = :sourceId")
    suspend fun getForSource(sourceId: Long): List<PendingPlaybackReportEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(report: PendingPlaybackReportEntity)

    @Query("DELETE FROM pending_playback_report WHERE entryId = :entryId AND sourceId = :sourceId")
    suspend fun delete(entryId: Int, sourceId: Long)
}

