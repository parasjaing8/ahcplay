package com.aihomecloud.ahcplayer.data.source

import android.content.Context
import com.aihomecloud.ahcplayer.data.ahc.AhcRepository
import com.aihomecloud.ahcplayer.data.model.BrowseItem

object BrowseFetcher {
    suspend fun fetchItems(context: Context, ahcRepo: AhcRepository, uri: String): List<BrowseItem> {
        return when {
            uri.startsWith("ahc://") -> {
                val parsed = java.net.URI(uri)
                val host = parsed.host
                val port = parsed.port
                val nasPath = parsed.path.ifEmpty { "/srv/nas" }
                val query = uri.substringAfter("?", "")
                val share = query.split("&").firstOrNull { it.startsWith("share=") }
                    ?.removePrefix("share=").orEmpty().ifEmpty { "media" }
                val user = query.split("&").firstOrNull { it.startsWith("user=") }
                    ?.removePrefix("user=").orEmpty()
                ahcRepo.listFiles(host, port, nasPath, share, user)
                    .filter { it.name.isNotEmpty() && !it.name.startsWith(".") && '/' !in it.name }
            }
            uri.startsWith("file://") -> LocalFileBrowser.browse(uri)
            else -> SmbBrowser.browse(context, uri)
        }
    }
}
