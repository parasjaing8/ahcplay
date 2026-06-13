package com.aihomecloud.ahcplayer.data.source

import android.content.Context
import android.net.Uri
import com.aihomecloud.ahcplayer.data.model.BrowseItem
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.util.MediaBrowser
import org.videolan.libvlc.interfaces.IMedia
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object SmbBrowser {

    suspend fun browse(context: Context, uri: String): List<BrowseItem> =
        withTimeout(15_000L) {
            suspendCancellableCoroutine { cont ->
                val libVlc = LibVLC(context, arrayListOf("--no-osd"))
                val items = mutableListOf<BrowseItem>()

                val browser = object : MediaBrowser.EventListener {
                    override fun onMediaAdded(index: Int, media: IMedia) {
                        val itemUri = media.uri.toString()
                        val name = media.uri.lastPathSegment
                            ?.let { Uri.decode(it) }
                            ?: itemUri.substringAfterLast('/')
                        val isDir = media.type == IMedia.Type.Directory
                        items.add(BrowseItem(name = name, uri = itemUri, isDirectory = isDir))
                        media.release()
                    }

                    override fun onMediaRemoved(index: Int, media: IMedia) {
                        media.release()
                    }

                    override fun onBrowseEnd() {
                        if (cont.isActive) {
                            items.sortWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                            cont.resume(items)
                        }
                        libVlc.release()
                    }
                }

                val mediaBrowser = MediaBrowser(libVlc, browser)
                mediaBrowser.browse(Uri.parse(uri), 0)

                cont.invokeOnCancellation {
                    mediaBrowser.release()
                    libVlc.release()
                }
            }
        }
}
