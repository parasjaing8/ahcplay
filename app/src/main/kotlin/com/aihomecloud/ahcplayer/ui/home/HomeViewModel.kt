package com.aihomecloud.ahcplayer.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aihomecloud.ahcplayer.data.db.AppDatabase
import com.aihomecloud.ahcplayer.data.model.MediaSource
import com.aihomecloud.ahcplayer.data.model.SourceType
import com.aihomecloud.ahcplayer.data.model.WatchHistory
import com.aihomecloud.ahcplayer.data.source.LanHost
import com.aihomecloud.ahcplayer.data.source.LanScanner
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryStats(
    val backdrops: List<String> = emptyList(),
    val movies: Int = 0,
    val shows: Int = 0
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)

    val libraryStats = combine(
        db.mediaMetadataDao().getRandomBackdropsFlow(6),
        db.mediaMetadataDao().countMoviesFlow(),
        db.mediaMetadataDao().countShowsFlow()
    ) { backdrops, movies, shows -> LibraryStats(backdrops, movies, shows) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryStats())

    val sources = db.sourceDao().getAll()
        .map { list ->
            list.filter { it.enabled }.map {
                MediaSource(it.id, it.name, it.host, it.share, it.port,
                    sourceType = when (it.sourceType) {
                        "AHC" -> SourceType.AHC
                        "INTERNAL" -> SourceType.INTERNAL
                        "USB" -> SourceType.USB
                        else -> SourceType.SMB
                    },
                    username = it.username, hasPin = it.hasPin, enabled = it.enabled)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val scanner = LanScanner(app)

    private val _discovered = MutableStateFlow<List<LanHost>>(emptyList())
    /** Hosts found on the LAN, minus anything already saved as a source. */
    val discovered: StateFlow<List<LanHost>> = combine(_discovered, sources) { found, saved ->
        val known = saved.map { it.host }.toSet()
        found.filterNot { it.address in known }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private var scanJob: Job? = null

    init {
        startScan()
    }

    /** Sweeps the local subnet. Safe to call repeatedly; a running scan is reused. */
    fun startScan() {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            _scanning.value = true
            _discovered.value = emptyList()
            runCatching {
                scanner.scan { host ->
                    _discovered.update { current ->
                        if (current.any { it.address == host.address }) current
                        else (current + host).sortedBy { it.address }
                    }
                }
            }
            _scanning.value = false
        }
    }

    val continueWatching = db.watchHistoryDao().getRecent()
        .map { list ->
            list.filter { it.positionMs > 5000 && it.progressFraction < 0.95f }
                .map { WatchHistory(it.uri.hashCode().toLong(), it.uri, it.title, it.positionMs, it.durationMs, it.sourceId, it.lastWatchedAt) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

private val com.aihomecloud.ahcplayer.data.db.WatchHistoryEntity.progressFraction: Float
    get() = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
