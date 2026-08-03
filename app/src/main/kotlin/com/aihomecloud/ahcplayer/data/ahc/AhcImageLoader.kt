package com.aihomecloud.ahcplayer.data.ahc

import android.content.Context
import android.net.Uri
import coil.ImageLoader
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap

/**
 * Coil image loading against an AiHomeCloud server.
 *
 * The backend already generates cached JPEG thumbnails from the media itself
 * (`GET /api/v1/files/thumbnail`), which is why this app carries no online metadata
 * provider: artwork is derived server-side from the user's own files, with no third
 * party and no licensing exposure.
 *
 * Two properties matter here and are the reason this is not a plain ImageLoader:
 *
 *  1. **Per-host certificate pinning.** Each server is a self-signed device addressed by
 *     LAN IP, pinned on first contact. A pin is meaningless across hosts, so one shared
 *     client cannot serve several servers — loaders are therefore built and cached per
 *     host, each reusing [buildAhcClient] rather than introducing new TLS code.
 *  2. **The bearer token must never leave its own host.** The interceptor attaches the
 *     token only when the outgoing request is actually addressed to the host this loader
 *     was built for. A redirect or a mis-set URL therefore cannot carry a session token
 *     to a third party.
 */
object AhcImageLoaders {

    private val cache = ConcurrentHashMap<String, ImageLoader>()

    /** Builds (or returns) the loader for one server. Keyed by host:port. */
    fun forHost(context: Context, repo: AhcRepository, host: String, port: Int, username: String = ""): ImageLoader =
        cache.getOrPut("$host:$port|$username") {
            ImageLoader.Builder(context.applicationContext)
                .okHttpClient { authenticatedClient(repo, host, port, username) }
                .build()
        }

    private fun authenticatedClient(
        repo: AhcRepository,
        host: String,
        port: Int,
        username: String
    ): OkHttpClient =
        repo.imageClientFor(host).newBuilder()
            .addInterceptor { chain ->
                val request = chain.request()
                // Scope the credential to this host and port explicitly. Anything else
                // goes out unauthenticated rather than leaking a session token.
                val sameServer = request.url.host == host && request.url.port == port
                val token = if (sameServer) repo.getToken(host, username) else null
                val outgoing = if (token.isNullOrEmpty()) request
                else request.newBuilder().addHeader("Authorization", "Bearer $token").build()
                chain.proceed(outgoing)
            }
            .build()

    /** Drops cached loaders for a host, e.g. after its token is cleared. */
    fun invalidate(host: String) {
        cache.keys.filter { it.startsWith("$host:") }.forEach { cache.remove(it) }
    }
}

/**
 * URL of the server-generated thumbnail for a file.
 *
 * [nasPath] is the server-side path of the media, as returned by the browse API —
 * not a local path and not an `ahc://` URI.
 */
fun ahcThumbnailUrl(host: String, port: Int, nasPath: String, size: Int = 512): String =
    Uri.Builder()
        .scheme("https")
        .encodedAuthority("$host:$port")
        .encodedPath("/api/v1/files/thumbnail")
        .appendQueryParameter("path", nasPath)
        .appendQueryParameter("size", size.toString())
        .build()
        .toString()
