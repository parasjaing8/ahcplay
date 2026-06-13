package com.aihomecloud.ahcplayer.data.tmdb

import android.content.Context
import com.aihomecloud.ahcplayer.BuildConfig
import com.aihomecloud.ahcplayer.data.db.AppDatabase
import com.aihomecloud.ahcplayer.data.db.MediaMetadataEntity

data class MediaMetadata(
    val displayTitle: String,
    val year: Int?,
    val posterUrl: String?,
    val overview: String?
)

class MetadataRepository(context: Context) {
    private val dao = AppDatabase.get(context).mediaMetadataDao()

    suspend fun get(filename: String): MediaMetadata? {
        val cached = dao.get(filename)
        if (cached != null) return cached.toMetadata()

        val parsed = TitleParser.parse(filename)
        return try {
            val results = TmdbClient.service.search(
                apiKey = BuildConfig.TMDB_API_KEY,
                query = parsed.title,
                year = parsed.year
            ).results
            val best = results.firstOrNull() ?: return fallback(filename, parsed)
            val entity = MediaMetadataEntity(
                filename = filename,
                tmdbId = best.id,
                displayTitle = best.displayTitle.ifEmpty { parsed.title },
                year = best.year ?: parsed.year,
                posterUrl = best.posterUrl(),
                overview = best.overview
            )
            dao.upsert(entity)
            entity.toMetadata()
        } catch (e: Exception) {
            fallback(filename, parsed)
        }
    }

    private suspend fun fallback(filename: String, parsed: ParsedTitle): MediaMetadata {
        val entity = MediaMetadataEntity(
            filename = filename,
            tmdbId = null,
            displayTitle = parsed.title,
            year = parsed.year,
            posterUrl = null,
            overview = null
        )
        dao.upsert(entity)
        return entity.toMetadata()
    }

    private fun MediaMetadataEntity.toMetadata() = MediaMetadata(
        displayTitle = displayTitle,
        year = year,
        posterUrl = posterUrl,
        overview = overview
    )
}
