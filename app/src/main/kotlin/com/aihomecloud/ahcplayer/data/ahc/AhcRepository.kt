package com.aihomecloud.ahcplayer.data.ahc

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.aihomecloud.ahcplayer.data.model.BrowseItem
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import com.aihomecloud.ahcplayer.data.prefs.SecurePrefs

private const val TAG = "AhcRepository"
private const val PREFS_NAME = "ahc_tokens"

class AhcRepository(context: Context) {

    private val prefs: SharedPreferences = SecurePrefs.create(context, PREFS_NAME)

    // username="" = device-level token (auto-pair); username="Paras" = profile token
    private fun tokenKey(host: String, username: String = "") =
        if (username.isEmpty()) "token_$host" else "token_${host}_$username"

    private fun certPinKey(host: String) = "certpin_$host"

    fun getToken(host: String, username: String = "") =
        prefs.getString(tokenKey(host, username), null)

    private fun saveToken(host: String, username: String = "", token: String) {
        prefs.edit().putString(tokenKey(host, username), token).apply()
    }

    fun clearToken(host: String, username: String = "") {
        prefs.edit().remove(tokenKey(host, username)).apply()
    }

    /**
     * A client pinned to this host, for image loading. Shares the same TOFU pin store as
     * [apiFor] so thumbnails are subject to exactly the same certificate check as API
     * calls — a separately-pinned image path would be a hole in the same trust decision.
     */
    fun imageClientFor(host: String): okhttp3.OkHttpClient = buildAhcClient(
        pinnedPin = prefs.getString(certPinKey(host), null),
        onFirstSeen = { pin -> prefs.edit().putString(certPinKey(host), pin).apply() }
    )

    // Builds a Retrofit client pinned to this host's certificate (TOFU on first contact).
    private fun apiFor(host: String, port: Int, connectTimeoutMs: Long = 10_000, readTimeoutMs: Long = 30_000): AhcApiService {
        val client = buildAhcClient(
            pinnedPin = prefs.getString(certPinKey(host), null),
            onFirstSeen = { pin -> prefs.edit().putString(certPinKey(host), pin).apply() },
            connectTimeoutMs = connectTimeoutMs,
            readTimeoutMs = readTimeoutMs
        )
        return buildAhcRetrofit("https://$host:$port/", client)
    }

    // Device-level auto-pairing used to live here (`ensureToken` / `autopair`). It fetched
    // /api/v1/pair/qr and paired with the key inside it — which the server's own docstring
    // describes as enough for "a full unconditional-admin pairing". That is an administrative
    // capability, and this app is consumption-only by design.
    //
    // It was reachable: `getOrFetchToken` fell back to it whenever no profile was chosen. It
    // simply never worked, because the server serves that endpoint to loopback only and 403s
    // the LAN — so the fallback produced an opaque HTTP error rather than a token, and the
    // administrative intent was never exercised. Removed 2026-08-05; that branch now says what
    // is actually wrong. Profile login (`loginWithProfile`) is the only way this app gets a
    // token, which is what "consumption-only" has to mean in code and not just in a document.

    /**
     * Identify a single address, for DiscoverViewModel's subnet sweep.
     *
     * Uses `/api/health`, which is unauthenticated and returns the device's serial and its
     * user-set name. This previously called `/api/v1/pair/qr` — an administrative,
     * loopback-only endpoint — so over the LAN it received 403 and returned null for every
     * address. The Discover screen's sweep therefore found nothing at all, silently, while the
     * home screen's port-scan discovery worked fine and hid the fault.
     *
     * The name also improves: health reports what the user actually called the device, rather
     * than a serial reformatted into something that looks like a name.
     */
    suspend fun probeHost(host: String, port: Int = 8443): AhcDeviceInfo? {
        return try {
            val api = apiFor(host, port, connectTimeoutMs = 1500L, readTimeoutMs = 2000L)
            val health = api.getHealth()
            if (health.status != "ok") return null
            AhcDeviceInfo(
                host = host,
                port = port,
                serial = health.serial,
                displayName = health.deviceName.ifBlank { serialToDisplayName(health.serial) },
            )
        } catch (e: Exception) { null }
    }

    // Returns profiles from the device (GET /api/v1/users)
    suspend fun getProfiles(host: String, port: Int): List<AhcUserProfile> {
        val api = apiFor(host, port)
        return try {
            api.getProfiles().users
        } catch (e: Exception) {
            Log.w(TAG, "getProfiles failed: ${e.message}")
            emptyList()
        }
    }

    // Profile login: PIN-based = call /auth/login; no-PIN = use device token associated with profile name
    suspend fun loginWithProfile(host: String, port: Int, username: String, pin: String = ""): String {
        val api = apiFor(host, port)
        val resp = api.loginWithProfile(AhcLoginRequest(username, pin))
        saveToken(host, username, resp.accessToken)
        return resp.accessToken
    }

    /**
     * Whether this device offers meaning-based search.
     *
     * Any failure means no — an older server 404s the endpoint and one without the embedding
     * runtime answers false, and both mean the same thing to a TV: do not offer it.
     */
    suspend fun semanticAvailable(host: String, port: Int, username: String): Boolean = try {
        val token = getToken(host, username) ?: return false
        apiFor(host, port).semanticStatus("Bearer $token").available
    } catch (e: Exception) {
        false
    }

    /**
     * Meaning-based search over the whole library.
     *
     * The TV's existing search filters the folder currently open, by substring. This is the one
     * that can answer "kids at the beach" from the top level — which matters far more on a
     * remote control, where typing a filename is the slowest thing a person can do.
     */
    suspend fun searchSemantic(
        host: String, port: Int, username: String, query: String, smbShare: String = "",
    ): List<BrowseItem> = try {
        val token = getToken(host, username) ?: return emptyList()
        val userParam = if (username.isNotEmpty()) "&user=$username" else ""
        apiFor(host, port).searchSemantic("Bearer $token", query).results.map { hit ->
            // isVideo is derived from the name, not passed — the extension is the truth.
            BrowseItem(
                name = hit.filename,
                uri = "ahc://$host:$port${hit.path}?share=$smbShare$userParam",
                isDirectory = false,
            )
        }
    } catch (e: Exception) {
        Log.w(TAG, "semantic search failed: ${e.message}")
        emptyList()
    }

    suspend fun listFiles(
        host: String,
        port: Int,
        nasPath: String,
        smbShare: String,
        username: String = ""
    ): List<BrowseItem> {
        val token = getOrFetchToken(host, port, username)
        val api = apiFor(host, port)
        return try {
            fetchPage(api, token, nasPath, smbShare, host, username)
        } catch (e: retrofit2.HttpException) {
            when (e.code()) {
                // Stale credential: clear and retry once.
                401 -> {
                    clearToken(host, username)
                    fetchPage(api, getOrFetchToken(host, port, username), nasPath, smbShare, host, username)
                }
                // A permission decision, not an expired token. Re-authenticating cannot help,
                // and discarding a valid token here just churns credentials. The server
                // deliberately refuses a non-admin listing the NAS root or `personal`
                // directly, so at the root we enumerate the scopes this profile can see.
                403 -> if (isNasRoot(nasPath)) {
                    listVisibleScopes(api, token, smbShare, host, username)
                } else throw e
                else -> throw e
            }
        }
    }

    private fun isNasRoot(nasPath: String): Boolean {
        val trimmed = nasPath.trim().trimEnd('/')
        return trimmed.isEmpty() || trimmed == "/srv/nas"
    }

    /**
     * Top-level scopes this profile is allowed to see. Probed rather than assumed, so a
     * profile without access to a scope simply does not get it — the server stays the
     * authority on permission and the client never has to model it.
     */
    private suspend fun listVisibleScopes(
        api: AhcApiService,
        token: String,
        smbShare: String,
        host: String,
        username: String
    ): List<BrowseItem> = kotlinx.coroutines.coroutineScope {
        CANDIDATE_SCOPES.map { scope ->
            async {
                runCatching { fetchPage(api, token, "/$scope", smbShare, host, username) }
                    .getOrNull()
                    ?.let {
                        BrowseItem(
                            name = scope,
                            uri = "ahc://$host:8443/$scope?share=$smbShare" +
                                if (username.isEmpty()) "" else "&user=$username",
                            isDirectory = true
                        )
                    }
            }
        }.awaitAll().filterNotNull()
    }

    // For profile tokens: try cached first; if missing, auto-login with empty PIN (no-PIN profiles).
    // For PIN-protected profiles without a token, throws with a descriptive message.
    private suspend fun getOrFetchToken(host: String, port: Int, username: String): String {
        val cached = getToken(host, username)
        if (cached != null) return cached
        return if (username.isNotEmpty()) {
            try {
                loginWithProfile(host, port, username, "")
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 422) throw Exception("PIN required for profile '$username'. Please re-authenticate.")
                throw e
            }
        } else {
            // No profile chosen. This used to fall back to device-level auto-pairing, which
            // asked the server for an administrative pairing key — and got 403, because that
            // endpoint is loopback-only. So this branch has never actually produced a token;
            // it produced an opaque HTTP error. Say what is wrong instead. Every real path
            // into browsing goes through profile selection first, so reaching here means a
            // caller skipped it.
            throw IllegalStateException(
                "Choose a profile before browsing this device — AiHomeCloud issues access per profile."
            )
        }
    }

    private suspend fun fetchPage(
        api: AhcApiService, token: String, nasPath: String, smbShare: String, host: String, username: String = ""
    ): List<BrowseItem> {
        val resp = api.listFiles("Bearer $token", nasPath)
        val userParam = if (username.isNotEmpty()) "&user=$username" else ""
        return resp.items.map { item ->
            BrowseItem(
                name = item.name,
                uri = if (item.isDirectory)
                    "ahc://$host:8443${item.path}?share=$smbShare$userParam"
                else
                    nasPathToSmb(host, item.path, smbShare),
                isDirectory = item.isDirectory,
                sizeBytes = item.sizeBytes,
                mimeType = item.mimeType ?: "",
                entryId = item.entryId
            )
        }
    }

    sealed class PlaybackReportResult {
        data class Applied(val ack: AhcPlaybackPositionAck) : PlaybackReportResult()
        object RetryLater : PlaybackReportResult()   // IOException, HTTP 429, HTTP 5xx
        object Discard : PlaybackReportResult()       // applied=false, or a permanent 4xx (401/403/404/422)
    }

    suspend fun reportPlaybackPosition(
        host: String, port: Int, username: String,
        entryId: Int, positionSeconds: Double, durationSeconds: Double?, clientUpdatedAt: Long,
    ): PlaybackReportResult {
        val token = getToken(host, username) ?: return PlaybackReportResult.Discard
        return try {
            val ack = apiFor(host, port).setPlaybackPosition(
                "Bearer $token", entryId,
                AhcPlaybackPositionRequest(positionSeconds, durationSeconds, clientUpdatedAt)
            )
            if (ack.applied) PlaybackReportResult.Applied(ack) else PlaybackReportResult.Discard
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 429 || e.code() >= 500) PlaybackReportResult.RetryLater else PlaybackReportResult.Discard
        } catch (e: java.io.IOException) {
            PlaybackReportResult.RetryLater
        } catch (e: Exception) {
            Log.w(TAG, "reportPlaybackPosition failed: ${e.message}")
            PlaybackReportResult.Discard
        }
    }

    suspend fun fetchPlaybackPositions(host: String, port: Int, username: String): List<AhcPlaybackPositionEntry> = try {
        val token = getToken(host, username) ?: return emptyList()
        apiFor(host, port).getPlaybackPositions("Bearer $token")
    } catch (e: Exception) {
        Log.w(TAG, "fetchPlaybackPositions failed: ${e.message}")
        emptyList()
    }

    companion object {
        /** Top-level scopes an AiHomeCloud server organises content into. */
        private val CANDIDATE_SCOPES = listOf("entertainment", "family", "personal")

        fun nasPathToSmb(host: String, nasPath: String, share: String): String {
            val nasRoot = "/srv/nas/"
            val relative = if (nasPath.startsWith(nasRoot)) nasPath.removePrefix(nasRoot)
                           else nasPath.trimStart('/')
            return "smb://$host/$share/$relative"
        }

        fun parseAhcUri(uri: String): Triple<String, Int, String>? {
            return try {
                val u = java.net.URI(uri)
                Triple(u.host, u.port, u.path)
            } catch (e: Exception) { null }
        }
    }
}
