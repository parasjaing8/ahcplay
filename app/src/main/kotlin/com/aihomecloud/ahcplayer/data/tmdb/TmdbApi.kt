package com.aihomecloud.ahcplayer.data.tmdb

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class TmdbMovieResult(
    val id: Int,
    val title: String?,
    val name: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("first_air_date") val firstAirDate: String?,
    @SerializedName("genre_ids") val genreIds: List<Int>?,
    @SerializedName("media_type") val mediaType: String?,
    val overview: String?
) {
    val displayTitle: String get() = title ?: name ?: ""
    val year: Int? get() = (releaseDate ?: firstAirDate)?.take(4)?.toIntOrNull()
    fun posterUrl(size: String = "w342"): String? =
        posterPath?.let { "https://image.tmdb.org/t/p/$size$it" }
    fun backdropUrl(size: String = "w1280"): String? =
        backdropPath?.let { "https://image.tmdb.org/t/p/$size$it" }

    fun genreLabel(): String? {
        val genres = if (mediaType == "tv") TV_GENRES else MOVIE_GENRES
        return genreIds.orEmpty()
            .mapNotNull(genres::get)
            .take(2)
            .joinToString(" / ")
            .ifEmpty { null }
    }

    fun mediaTypeLabel(): String? = when (mediaType) {
        "movie" -> "Movie"
        "tv" -> "Series"
        else -> null
    }

    companion object {
        private val MOVIE_GENRES = mapOf(
            12 to "Adventure",
            14 to "Fantasy",
            16 to "Animation",
            18 to "Drama",
            27 to "Horror",
            28 to "Action",
            35 to "Comedy",
            36 to "History",
            37 to "Western",
            53 to "Thriller",
            80 to "Crime",
            99 to "Documentary",
            878 to "Science Fiction",
            9648 to "Mystery",
            10402 to "Music",
            10749 to "Romance",
            10751 to "Family",
            10752 to "War",
            10770 to "TV Movie"
        )

        private val TV_GENRES = mapOf(
            16 to "Animation",
            18 to "Drama",
            35 to "Comedy",
            37 to "Western",
            80 to "Crime",
            99 to "Documentary",
            9648 to "Mystery",
            10751 to "Family",
            10759 to "Action / Adventure",
            10762 to "Kids",
            10763 to "News",
            10764 to "Reality",
            10765 to "Sci-Fi / Fantasy",
            10766 to "Soap",
            10767 to "Talk",
            10768 to "War / Politics"
        )
    }
}

data class TmdbSearchResponse(val results: List<TmdbMovieResult>)

interface TmdbService {
    @GET("search/multi")
    suspend fun search(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("year") year: Int? = null,
        @Query("page") page: Int = 1
    ): TmdbSearchResponse
}

object TmdbClient {
    private const val BASE_URL = "https://api.themoviedb.org/3/"

    val service: TmdbService by lazy {
        val client = OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.NONE
            })
            .build()
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TmdbService::class.java)
    }
}
