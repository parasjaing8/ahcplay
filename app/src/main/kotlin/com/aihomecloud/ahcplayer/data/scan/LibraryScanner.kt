package com.aihomecloud.ahcplayer.data.scan

import android.content.Context
import com.aihomecloud.ahcplayer.data.ahc.AhcRepository
import com.aihomecloud.ahcplayer.data.model.MediaSource
import com.aihomecloud.ahcplayer.data.source.BrowseFetcher
import com.aihomecloud.ahcplayer.data.tmdb.MetadataRepository

private const val MAX_DEPTH = 6

/** Recursively walks every enabled source and populates the metadata cache for each video file. */
object LibraryScanner {
    suspend fun scan(
        context: Context,
        sources: List<MediaSource>,
        forceRefresh: Boolean = false,
        onProgress: (Int) -> Unit = {}
    ) {
        val ahcRepo = AhcRepository(context)
        val metaRepo = MetadataRepository(context)
        var count = 0
        sources.forEach { source ->
            scanUri(context, ahcRepo, metaRepo, source.browseRootUri, depth = 0, forceRefresh) {
                count++
                onProgress(count)
            }
        }
    }

    private suspend fun scanUri(
        context: Context,
        ahcRepo: AhcRepository,
        metaRepo: MetadataRepository,
        uri: String,
        depth: Int,
        forceRefresh: Boolean,
        onItem: () -> Unit
    ) {
        if (depth > MAX_DEPTH) return
        val items = try {
            BrowseFetcher.fetchItems(context, ahcRepo, uri)
        } catch (e: Exception) {
            return
        }
        items.forEach { item ->
            if (item.isDirectory) {
                scanUri(context, ahcRepo, metaRepo, item.uri, depth + 1, forceRefresh, onItem)
            } else if (item.isVideo) {
                metaRepo.get(item.name, forceRefresh)
                onItem()
            }
        }
    }
}
