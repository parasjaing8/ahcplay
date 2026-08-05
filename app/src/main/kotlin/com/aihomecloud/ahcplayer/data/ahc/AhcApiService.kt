package com.aihomecloud.ahcplayer.data.ahc

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

// The pairing DTOs (AhcPairQrResponse / AhcPairRequest / AhcTokenResponse) lived here until
// 2026-08-05. They served the device-level auto-pairing path removed from AhcRepository — an
// administrative capability that does not belong in a consumption-only app. Removed rather than
// kept "in case", so nothing can quietly wire them back up.

/** `GET /api/health` — unauthenticated, and the only device identity this app needs. */
data class AhcHealthResponse(
    val status: String,
    val serial: String,
    val deviceName: String = ""
)

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

/** `GET /api/v1/files/search/semantic` — meaning-based search across the whole library. */
data class AhcSemanticHit(
    val path: String,
    val filename: String,
    val score: Float = 0f,
)

data class AhcSemanticResponse(
    val results: List<AhcSemanticHit> = emptyList(),
    val query: String = "",
    val count: Int = 0,
)

/** Whether this server offers it at all — false on any server without the embedding runtime. */
data class AhcSemanticStatus(
    val available: Boolean = false,
    val indexedCount: Int = 0,
)
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
    // Identity probe. Unauthenticated by design on the server, and deliberately NOT
    // /api/v1/pair/qr — that one is administrative and loopback-only. See AhcRepository.
    @GET("api/health")
    suspend fun getHealth(): AhcHealthResponse

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

    // GET /api/v1/files/search/semantic — searches the WHOLE library by meaning, unlike the
    // local substring filter which only sees the folder currently open.
    @GET("api/v1/files/search/semantic")
    suspend fun searchSemantic(
        @Header("Authorization") bearer: String,
        @Query("q") query: String,
        @Query("limit") limit: Int = 30,
    ): AhcSemanticResponse

    @GET("api/v1/files/search/semantic/status")
    suspend fun semanticStatus(@Header("Authorization") bearer: String): AhcSemanticStatus

    // POST /api/v1/auth/login — profile-level auth; returns {accessToken, refreshToken}
    @POST("api/v1/auth/login")
    suspend fun loginWithProfile(@Body body: AhcLoginRequest): AhcLoginResponse
}

fun buildAhcRetrofit(baseUrl: String, client: OkHttpClient): AhcApiService =
    Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AhcApiService::class.java)

fun serialToDisplayName(serial: String): String {
    val parts = serial.split("-")
    return if (parts.size >= 2) "${parts[0]} ${parts.drop(1).joinToString(" ")}" else serial
}
