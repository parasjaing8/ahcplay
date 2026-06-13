package com.aihomecloud.ahcplayer.ui.browse

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aihomecloud.ahcplayer.data.ahc.AhcRepository
import com.aihomecloud.ahcplayer.data.db.AppDatabase
import com.aihomecloud.ahcplayer.data.model.BrowseItem
import com.aihomecloud.ahcplayer.data.model.WatchHistory
import com.aihomecloud.ahcplayer.data.source.SmbBrowser
import com.aihomecloud.ahcplayer.data.tmdb.MediaMetadata
import com.aihomecloud.ahcplayer.data.tmdb.MetadataRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class BrowseState {
    object Idle : BrowseState()
    object Loading : BrowseState()
    data class Success(val items: List<BrowseItem>) : BrowseState()
    data class Error(val message: String) : BrowseState()
}

class BrowseViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow<BrowseState>(BrowseState.Idle)
    val state: StateFlow<BrowseState> = _state.asStateFlow()

    private val _currentUri = MutableStateFlow("")
    val currentUri: StateFlow<String> = _currentUri.asStateFlow()

    private val _metadata = MutableStateFlow<Map<String, MediaMetadata>>(emptyMap())
    val metadata: StateFlow<Map<String, MediaMetadata>> = _metadata.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _continueWatching = MutableStateFlow<List<WatchHistory>>(emptyList())
    val continueWatching: StateFlow<List<WatchHistory>> = _continueWatching.asStateFlow()

    private val metaRepo = MetadataRepository(app)
    private val ahcRepo = AhcRepository(app)
    private val db = AppDatabase.get(app)
    private val backStack = ArrayDeque<String>()
    private var allItems: List<BrowseItem> = emptyList()
    private var watchJob: Job? = null

    fun initBrowse(rootUri: String, sourceId: Long) {
        backStack.clear()
        watchJob?.cancel()
        _continueWatching.value = emptyList()
        if (sourceId > 0L) {
            watchJob = viewModelScope.launch {
                db.watchHistoryDao().getRecentBySource(sourceId).collect { list ->
                    _continueWatching.value = list
                        .filter { it.positionMs > 5000 && it.durationMs > 0 && it.positionMs.toFloat() / it.durationMs < 0.95f }
                        .map { WatchHistory(0L, it.uri, it.title, it.positionMs, it.durationMs, it.sourceId, it.lastWatchedAt) }
                }
            }
        }
        browse(rootUri)
    }

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
        applyFilter()
    }

    private fun applyFilter() {
        val q = _searchQuery.value.trim().lowercase()
        val filtered = if (q.isEmpty()) allItems
        else allItems.filter { it.name.lowercase().contains(q) }
        _state.value = BrowseState.Success(filtered)
    }

    fun browse(uri: String) {
        _currentUri.value = uri
        _searchQuery.value = ""
        viewModelScope.launch {
            _state.value = BrowseState.Loading
            try {
                val items = fetchItems(uri)
                allItems = items
                _state.value = BrowseState.Success(items)
                items.filter { it.isVideo }.forEach { item ->
                    launch { fetchMetadata(item.name) }
                }
            } catch (e: Exception) {
                _state.value = BrowseState.Error(e.message ?: "Browse failed")
            }
        }
    }

    private suspend fun fetchItems(uri: String): List<BrowseItem> {
        return if (uri.startsWith("ahc://")) {
            val parsed = java.net.URI(uri)
            val host = parsed.host
            val port = parsed.port
            val nasPath = parsed.path.ifEmpty { "/srv/nas" }
            val query = uri.substringAfter("?", "")
            val share = query.split("&").firstOrNull { it.startsWith("share=") }
                ?.removePrefix("share=").orEmpty().ifEmpty { "media" }
            val user = query.split("&").firstOrNull { it.startsWith("user=") }
                ?.removePrefix("user=").orEmpty()
            ahcRepo.listFiles(host, port, nasPath, share, user)
        } else {
            SmbBrowser.browse(getApplication(), uri)
        }
    }

    private suspend fun fetchMetadata(filename: String) {
        val meta = metaRepo.get(filename) ?: return
        _metadata.value = _metadata.value + (filename to meta)
    }

    fun push(uri: String) {
        backStack.addLast(_currentUri.value)
        browse(uri)
    }

    fun pop(): Boolean {
        if (backStack.isEmpty()) return false
        browse(backStack.removeLast())
        return true
    }

    val canGoBack: Boolean get() = backStack.isNotEmpty()
}
