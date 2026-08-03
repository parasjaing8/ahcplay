package com.aihomecloud.ahcplayer.data.source

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/** A host on the local network that looks like it can serve media. */
data class LanHost(
    val address: String,
    val hasSmb: Boolean,
    val hasAhc: Boolean
) {
    val displayName: String get() = address
}

/**
 * Sweeps the current /24 for hosts with an open SMB (445) or AiHomeCloud (8443) port.
 *
 * Deliberately a plain TCP connect probe: it needs no permissions beyond network access,
 * works on networks where mDNS/NSD is unreliable, and finds NAS boxes that never
 * announce themselves. Discovery is a convenience — manual entry always remains.
 */
class LanScanner(private val context: Context) {

    private companion object {
        const val SMB_PORT = 445
        const val AHC_PORT = 8443

        /**
         * Generous because the sweep runs many probes at once over WiFi; a NAS that
         * answers in milliseconds when idle can still exceed a tight budget under load.
         */
        const val CONNECT_TIMEOUT_MS = 1200
        const val MAX_CONCURRENCY = 32
        const val FIRST_HOST = 1
        const val LAST_HOST = 254
    }

    /** Local IPv4 of this device, or null when not on a usable network. */
    fun localIp(): String? {
        val wifi = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        @Suppress("DEPRECATION")
        val raw = Formatter.formatIpAddress(wifi.connectionInfo.ipAddress)
        return raw.takeIf { it.isNotBlank() && it != "0.0.0.0" }
    }

    /**
     * Scans the local /24 and reports each responding host through [onFound] as soon as
     * it answers, so the UI can fill in progressively rather than waiting for the sweep.
     */
    suspend fun scan(onFound: (LanHost) -> Unit): List<LanHost> = withContext(Dispatchers.IO) {
        val subnet = localIp()?.substringBeforeLast('.') ?: return@withContext emptyList()
        val gate = Semaphore(MAX_CONCURRENCY)

        coroutineScope {
            (FIRST_HOST..LAST_HOST).map { octet ->
                async {
                    gate.withPermit {
                        val address = "$subnet.$octet"
                        // Probe both ports concurrently: sequential probes double the
                        // time a permit is held and starve the rest of the sweep.
                        val smb = async { isPortOpen(address, SMB_PORT) }
                        val ahc = async { isPortOpen(address, AHC_PORT) }
                        val hasSmb = smb.await()
                        val hasAhc = ahc.await()
                        if (!hasSmb && !hasAhc) return@withPermit null
                        LanHost(address = address, hasSmb = hasSmb, hasAhc = hasAhc)
                            .also(onFound)
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    private fun isPortOpen(address: String, port: Int): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MS)
            true
        }
    }.getOrDefault(false)
}
