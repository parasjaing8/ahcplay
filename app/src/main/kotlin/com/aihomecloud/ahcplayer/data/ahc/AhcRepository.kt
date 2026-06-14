package com.aihomecloud.ahcplayer.data.ahc

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.aihomecloud.ahcplayer.data.model.BrowseItem
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

    // Auto-pair at device level (no profile)
    suspend fun ensureToken(host: String, port: Int, username: String = ""): String {
        val existing = getToken(host, username)
        if (!existing.isNullOrEmpty()) return existing
        return autopair(host, port, username)
    }

    private suspend fun autopair(host: String, port: Int, username: String): String {
        val api = apiFor(host, port)
        val qr = api.getPairingInfo()
        val tokenResp = api.pair(AhcPairRequest(qr.serial, qr.key))
        saveToken(host, username, tokenResp.token)
        return tokenResp.token
    }

    // Used by DiscoverViewModel to probe a single IP
    suspend fun probeHost(host: String, port: Int = 8443): AhcDeviceInfo? {
        return try {
            val api = apiFor(host, port, connectTimeoutMs = 1500L, readTimeoutMs = 2000L)
            val qr = api.getPairingInfo()
            AhcDeviceInfo(host = host, port = port, serial = qr.serial, displayName = serialToDisplayName(qr.serial))
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
            if (e.code() == 401 || e.code() == 403) {
                clearToken(host, username)
                val newToken = getOrFetchToken(host, port, username)
                fetchPage(api, newToken, nasPath, smbShare, host, username)
            } else throw e
        }
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
            ensureToken(host, port, "")
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
                mimeType = item.mimeType ?: ""
            )
        }
    }

    companion object {
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
