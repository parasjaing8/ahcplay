package com.aihomecloud.ahcplayer.data.metadata

import android.content.Context
import com.aihomecloud.ahcplayer.data.db.AppDatabase
import com.aihomecloud.ahcplayer.data.db.MediaMetadataEntity

data class MediaMetadata(
    val displayTitle: String,
    val year: Int?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val genre: String?,
    val mediaType: String?,
    val overview: String?
)

/**
 * Derives display metadata from the filename alone.
 *
 * There is deliberately no online metadata provider. TMDB was removed because its API
 * terms prohibit commercial use without a separate written agreement, which conflicts
 * with a paid tier, and because requiring each user to obtain their own API key was real
 * onboarding friction. Artwork comes from frames extracted from the media itself, which
 * carries no licensing exposure and — unlike any online database — also works for home
 * video that no catalogue has ever indexed.
 *
 * `posterUrl` and `backdropUrl` therefore hold local file URIs once a thumbnail has been
 * generated, and null until then. Callers already treat them as opaque image references.
 */
class MetadataRepository(context: Context) {
    private val dao = AppDatabase.get(context).mediaMetadataDao()

    suspend fun get(filename: String, forceRefresh: Boolean = false): MediaMetadata? {
        if (!forceRefresh) {
            dao.get(filename)?.let { return it.toMetadata() }
        }
        return derive(filename, TitleParser.parse(filename))
    }

    private suspend fun derive(filename: String, parsed: ParsedTitle): MediaMetadata {
        // Preserve any artwork already generated for this file across a rescan.
        val existingPoster = dao.get(filename)?.posterUrl
        val entity = MediaMetadataEntity(
            filename = filename,
            tmdbId = null,
            displayTitle = parsed.title,
            year = parsed.year,
            posterUrl = existingPoster,
            backdropUrl = null,
            genre = null,
            mediaType = if (parsed.isTvShow) "Series" else "Movie",
            overview = null
        )
        dao.upsert(entity)
        return entity.toMetadata()
    }

    private fun MediaMetadataEntity.toMetadata() = MediaMetadata(
        displayTitle = displayTitle,
        year = year,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        genre = genre,
        mediaType = mediaType,
        overview = overview
    )
}
