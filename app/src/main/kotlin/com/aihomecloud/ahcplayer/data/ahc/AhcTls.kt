package com.aihomecloud.ahcplayer.data.ahc

import okhttp3.OkHttpClient
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** SHA-256 hash of a certificate's public key (SPKI), base64-encoded. */
fun spkiPin(cert: X509Certificate): String =
    Base64.getEncoder().encodeToString(
        MessageDigest.getInstance("SHA-256").digest(cert.publicKey.encoded)
    )

/**
 * Trust-on-first-use: accepts an AHC NAS's self-signed certificate the first time
 * it's seen for a host and pins its public key via [onFirstSeen]. Any later
 * connection presenting a different key is rejected as a possible MITM.
 */
private class TofuTrustManager(
    private val pinnedPin: String?,
    private val onFirstSeen: (String) -> Unit
) : X509TrustManager {
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        val leaf = chain.firstOrNull() ?: throw CertificateException("No certificate presented")
        leaf.checkValidity()
        val pin = spkiPin(leaf)
        if (pinnedPin == null) {
            onFirstSeen(pin)
        } else if (pin != pinnedPin) {
            throw CertificateException("Certificate for this device changed unexpectedly — re-pair the device")
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

/**
 * OkHttp client for an AHC NAS host. These are self-signed devices addressed by LAN
 * IP, so we pin the device's certificate public key on first contact instead of
 * relying on a CA. Hostname verification is disabled because the cert won't carry
 * the LAN IP as a SAN — identity is enforced by the pinned key above, not hostname.
 */
fun buildAhcClient(
    pinnedPin: String?,
    onFirstSeen: (String) -> Unit,
    connectTimeoutMs: Long = 10_000,
    readTimeoutMs: Long = 30_000
): OkHttpClient {
    val trustManager = TofuTrustManager(pinnedPin, onFirstSeen)
    val sslContext = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
    }
    return OkHttpClient.Builder()
        .sslSocketFactory(sslContext.socketFactory, trustManager)
        .hostnameVerifier { _, _ -> true }
        .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
        .build()
}
