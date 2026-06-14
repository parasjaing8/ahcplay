package com.aihomecloud.ahcplayer.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aihomecloud.ahcplayer.data.db.AppDatabase
import com.aihomecloud.ahcplayer.data.model.MediaSource
import com.aihomecloud.ahcplayer.data.model.SourceType
import com.aihomecloud.ahcplayer.data.model.WatchHistory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryStats(
    val backdrops: List<String> = emptyList(),
    val movies: Int = 0,
    val shows: Int = 0
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)

    private val _libraryStats = MutableStateFlow(LibraryStats())
    val libraryStats: StateFlow<LibraryStats> = _libraryStats.asStateFlow()

    init {
        viewModelScope.launch {
            val dao = db.mediaMetadataDao()
            _libraryStats.value = LibraryStats(
                backdrops = dao.getRandomBackdrops(6),
                movies = dao.countMovies(),
                shows = dao.countShows()
            )
        }
    }

    val sources = db.sourceDao().getAll()
        .map { list ->
            list.map {
                MediaSource(it.id, it.name, it.host, it.share, it.port,
                    if (it.sourceType == "AHC") SourceType.AHC else SourceType.SMB,
                    username = it.username, hasPin = it.hasPin)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val continueWatching = db.watchHistoryDao().getRecent()
        .map { list ->
            list.filter { it.positionMs > 5000 && it.progressFraction < 0.95f }
                .map { WatchHistory(it.uri.hashCode().toLong(), it.uri, it.title, it.positionMs, it.durationMs, it.sourceId, it.lastWatchedAt) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

private val com.aihomecloud.ahcplayer.data.db.WatchHistoryEntity.progressFraction: Float
    get() = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
