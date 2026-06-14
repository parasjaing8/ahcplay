package com.aihomecloud.ahcplayer.data.source

import com.aihomecloud.ahcplayer.data.model.BrowseItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI

object LocalFileBrowser {
    suspend fun browse(uri: String): List<BrowseItem> = withContext(Dispatchers.IO) {
        val dir = File(URI(uri).path)
        dir.listFiles().orEmpty()
            .filter { !it.name.startsWith(".") }
            .map { f ->
                BrowseItem(
                    name = f.name,
                    uri = "file://${f.absolutePath}",
                    isDirectory = f.isDirectory,
                    sizeBytes = if (f.isFile) f.length() else 0
                )
            }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }
}
