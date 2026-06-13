package com.aihomecloud.ahcplayer.data.ahc

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class AhcPairQrResponse(
    val serial: String,
    val ip: String,
    val otp: String,
    val expiresAt: Long,
    @SerializedName("qrValue") val qrValue: String = ""
) {
    // key is embedded in qrValue: "aihomecloud://pair?serial=...&key=KEY&..."
    val key: String get() = qrValue.substringAfter("key=").substringBefore("&")
}

data class AhcPairRequest(val serial: String, val key: String)
data class AhcTokenResponse(val token: String)

data class AhcFileItem(
    val name: String,
    val path: String,
    @SerializedName("isDirectory") val isDirectory: Boolean,
    @SerializedName("sizeBytes") val sizeBytes: Long = 0,
    @SerializedName("mimeType") val mimeType: String? = null
)

data class AhcFileListResponse(
    val items: List<AhcFileItem>,
    @SerializedName("totalCount") val totalCount: Int,
    val page: Int,
    @SerializedName("pageSize") val pageSize: Int
)

data class AhcUserProfile(
    val name: String,
    @SerializedName("has_pin") val hasPin: Boolean = false,
    @SerializedName("icon_emoji") val iconEmoji: String = ""
)

data class AhcProfilesResponse(val users: List<AhcUserProfile>)
data class AhcLoginRequest(val name: String, val pin: String = "")
data class AhcLoginResponse(
    @SerializedName("accessToken") val accessToken: String,
    @SerializedName("refreshToken") val refreshToken: String = ""
)

data class AhcDeviceInfo(
    val host: String,
    val port: Int = 8443,
    val serial: String,
    val displayName: String
)

interface AhcApiService {
    @GET("api/v1/pair/qr")
    suspend fun getPairingInfo(): AhcPairQrResponse

    @POST("api/v1/pair")
    suspend fun pair(@Body body: AhcPairRequest): AhcTokenResponse

    @GET("api/v1/files/list")
    suspend fun listFiles(
        @Header("Authorization") bearer: String,
        @Query("path") path: String,
        @Query("page") page: Int = 0,
        @Query("page_size") pageSize: Int = 200,
        @Query("sort_by") sortBy: String = "name",
        @Query("sort_dir") sortDir: String = "asc"
    ): AhcFileListResponse

    // GET /api/v1/auth/users/names — list family profiles, no auth required
    @GET("api/v1/auth/users/names")
    suspend fun getProfiles(): AhcProfilesResponse

    // POST /api/v1/auth/login — profile-level auth; returns {accessToken, refreshToken}
    @POST("api/v1/auth/login")
    suspend fun loginWithProfile(@Body body: AhcLoginRequest): AhcLoginResponse
}

fun buildUnsafeTrustingClient(
    connectTimeoutMs: Long = 10_000,
    readTimeoutMs: Long = 30_000
): OkHttpClient {
    val trustAll = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
    val sslContext = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf<TrustManager>(trustAll), SecureRandom())
    }
    return OkHttpClient.Builder()
        .sslSocketFactory(sslContext.socketFactory, trustAll)
        .hostnameVerifier { _, _ -> true }
        .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
        .build()
}

fun buildAhcRetrofit(
    baseUrl: String,
    connectTimeoutMs: Long = 10_000,
    readTimeoutMs: Long = 30_000
): AhcApiService =
    Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(buildUnsafeTrustingClient(connectTimeoutMs, readTimeoutMs))
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AhcApiService::class.java)

fun serialToDisplayName(serial: String): String {
    val parts = serial.split("-")
    return if (parts.size >= 2) "${parts[0]} ${parts.drop(1).joinToString(" ")}" else serial
}
