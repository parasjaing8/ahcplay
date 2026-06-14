package com.aihomecloud.ahcplayer.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources ORDER BY createdAt ASC")
    fun getAll(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources WHERE host = :host AND username = :username LIMIT 1")
    suspend fun getByHostAndUsername(host: String, username: String): SourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(source: SourceEntity): Long

    @Delete
    suspend fun delete(source: SourceEntity)
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
    suspend fun getRandomBackdrops(limit: Int = 6): List<String>

    @Query("SELECT COUNT(*) FROM media_metadata WHERE mediaType = 'Movie'")
    suspend fun countMovies(): Int

    @Query("SELECT COUNT(*) FROM media_metadata WHERE mediaType = 'Series'")
    suspend fun countShows(): Int
}

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY lastWatchedAt DESC LIMIT 50")
    fun getRecent(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE sourceId = :sourceId ORDER BY lastWatchedAt DESC LIMIT 20")
    fun getRecentBySource(sourceId: Long): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE uri = :uri LIMIT 1")
    suspend fun getByUri(uri: String): WatchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WatchHistoryEntity)

    @Query("DELETE FROM watch_history WHERE uri = :uri")
    suspend fun delete(uri: String)

    @Query("DELETE FROM watch_history")
    suspend fun deleteAll()
}

