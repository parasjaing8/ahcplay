package com.aihomecloud.ahcplayer.ui.browse

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aihomecloud.ahcplayer.data.ahc.AhcRepository
import com.aihomecloud.ahcplayer.data.db.AppDatabase
import com.aihomecloud.ahcplayer.data.model.BrowseItem
import com.aihomecloud.ahcplayer.data.model.WatchHistory
import com.aihomecloud.ahcplayer.data.source.BrowseFetcher
import com.aihomecloud.ahcplayer.data.metadata.MediaMetadata
import com.aihomecloud.ahcplayer.data.metadata.MetadataRepository
import com.aihomecloud.ahcplayer.data.ahc.AhcImageLoaders
import com.aihomecloud.ahcplayer.data.ahc.ahcThumbnailUrl
import coil.ImageLoader
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import android.net.Uri
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed class BrowseState {
    object Idle : BrowseState()
    object Loading : BrowseState()
    data class Success(val items: List<BrowseItem>) : BrowseState()
    data class Error(val message: String) : BrowseState()
}

private const val AHC_DEFAULT_PORT = 8443

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
    private val metadataSemaphore = Semaphore(5)

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
        // The local filter only ever sees the folder currently open. Semantic search sees the
        // whole library, which is what someone with a remote control actually wants — typing a
        // filename on a D-pad is the slowest thing a person can do on a TV.
        scheduleSemanticSearch(q)
    }

    private fun applyFilter() {
        val q = _searchQuery.value.trim().lowercase()
        val local = if (q.isEmpty()) allItems
        else allItems.filter { it.name.lowercase().contains(q) }
        // Substring hits in this folder are exact and stay first; library-wide matches extend
        // the tail, deduplicated by URI.
        val combined = if (q.isEmpty()) local else {
            val seen = local.mapTo(mutableSetOf()) { it.uri }
            local + _semanticHits.value.filter { it.uri !in seen }
        }
        _state.value = BrowseState.Success(combined)
    }

    private val _semanticHits = MutableStateFlow<List<BrowseItem>>(emptyList())
    val semanticHitCount: StateFlow<Int> =
        _semanticHits.map { it.size }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private var semanticJob: Job? = null
    private var semanticAvailable: Boolean? = null

    /**
     * Debounced, because on a D-pad every character arrives as its own keystroke and each one
     * would otherwise cost a round trip plus an inference on the board.
     */
    private fun scheduleSemanticSearch(query: String) {
        semanticJob?.cancel()
        val q = query.trim()
        if (q.length < 3) {
            _semanticHits.value = emptyList()
            applyFilterOnly()
            return
        }
        semanticJob = viewModelScope.launch {
            delay(450)
            val root = runCatching { Uri.parse(_currentUri.value) }.getOrNull() ?: return@launch
            if (root.scheme != "ahc") return@launch          // SMB sources have no server to ask
            val host = root.host ?: return@launch
            val port = root.port.takeIf { it > 0 } ?: AHC_DEFAULT_PORT
            val user = root.getQueryParameter("user").orEmpty()

            if (semanticAvailable == null) {
                semanticAvailable = ahcRepo.semanticAvailable(host, port, user)
            }
            if (semanticAvailable != true) return@launch

            val share = root.getQueryParameter("share").orEmpty()
            _semanticHits.value = ahcRepo.searchSemantic(host, port, user, q, share)
            applyFilterOnly()
        }
    }

    private fun applyFilterOnly() {
        val q = _searchQuery.value.trim().lowercase()
        val local = if (q.isEmpty()) allItems
        else allItems.filter { it.name.lowercase().contains(q) }
        val combined = if (q.isEmpty()) local else {
            val seen = local.mapTo(mutableSetOf()) { it.uri }
            local + _semanticHits.value.filter { it.uri !in seen }
        }
        _state.value = BrowseState.Success(combined)
    }

    fun browse(uri: String) {
        _currentUri.value = uri
        _searchQuery.value = ""
        semanticJob?.cancel()
        _semanticHits.value = emptyList()
        viewModelScope.launch {
            _state.value = BrowseState.Loading
            try {
                val items = fetchItems(uri)
                allItems = items
                _state.value = BrowseState.Success(items)
                items.filter { it.isVideo }.forEach { item ->
                    launch { fetchMetadata(item) }
                }
            } catch (e: Exception) {
                _state.value = BrowseState.Error(e.message ?: "Browse failed")
            }
        }
    }

    private suspend fun fetchItems(uri: String): List<BrowseItem> =
        BrowseFetcher.fetchItems(getApplication(), ahcRepo, uri)

    private suspend fun fetchMetadata(item: BrowseItem) {
        val meta = metadataSemaphore.withPermit { metaRepo.get(item.name) } ?: return
        // Artwork comes from a frame the server already extracted from the file itself.
        // The URL is derived from the item's own URI, so it needs no persistence.
        val withArt = if (meta.posterUrl == null) meta.copy(posterUrl = serverThumbnailFor(item)) else meta
        _metadata.update { it + (item.name to withArt) }
    }

    /**
     * Server-generated thumbnail for a video hosted by an AiHomeCloud server.
     *
     * Files are handed `smb://host/share/<relative>` URIs so libVLC can stream them
     * directly — only directories carry `ahc://`. So the server identity comes from the
     * directory currently being browsed, and the NAS path is recovered from the file's
     * own SMB URI by dropping the share segment.
     */
    private fun serverThumbnailFor(item: BrowseItem): String? {
        if (item.isDirectory) return null
        val root = android.net.Uri.parse(_currentUri.value)
        if (root.scheme != "ahc") return null
        val host = root.host ?: return null
        val smb = android.net.Uri.parse(item.uri)
        if (smb.scheme != "smb") return null
        val segments = smb.pathSegments
        if (segments.size < 2) return null
        val nasPath = "/" + segments.drop(1).joinToString("/")
        return ahcThumbnailUrl(host, root.port.takeIf { it > 0 } ?: AHC_DEFAULT_PORT, nasPath)
    }

    /**
     * Coil loader for the server currently being browsed. Per-host because each device's
     * certificate is pinned individually — one shared client could not pin correctly.
     */
    fun imageLoaderFor(uri: String): ImageLoader? {
        if (!uri.startsWith("ahc://")) return null
        val u = android.net.Uri.parse(uri)
        val host = u.host ?: return null
        val user = u.getQueryParameter("user").orEmpty()
        return AhcImageLoaders.forHost(
            getApplication(), ahcRepo, host, u.port.takeIf { it > 0 } ?: AHC_DEFAULT_PORT, user
        )
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
