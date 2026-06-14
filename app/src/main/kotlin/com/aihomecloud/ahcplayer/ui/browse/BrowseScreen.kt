package com.aihomecloud.ahcplayer.ui.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aihomecloud.ahcplayer.data.model.BrowseItem
import com.aihomecloud.ahcplayer.data.model.WatchHistory
import com.aihomecloud.ahcplayer.data.tmdb.MediaMetadata
import com.aihomecloud.ahcplayer.ui.theme.Accent
import com.aihomecloud.ahcplayer.ui.theme.BgPrimary
import com.aihomecloud.ahcplayer.ui.theme.Dimens
import com.aihomecloud.ahcplayer.ui.theme.TextMuted
import com.aihomecloud.ahcplayer.ui.theme.TextPrimary
import com.aihomecloud.ahcplayer.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun BrowseScreen(
    rootUri: String,
    sourceId: Long = 0L,
    onPlayVideo: (uri: String, title: String, sourceId: Long) -> Unit,
    onBack: () -> Unit,
    vm: BrowseViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val currentUri by vm.currentUri.collectAsStateWithLifecycle()
    val metadataMap by vm.metadata.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val continueWatching by vm.continueWatching.collectAsStateWithLifecycle()
    var searchActive by remember { mutableStateOf(false) }
    var selectedUri by remember(currentUri) { mutableStateOf<String?>(null) }
    var detailsItem by remember(currentUri) { mutableStateOf<BrowseItem?>(null) }
    val searchFocusRequester = remember { FocusRequester() }
    val playFocusRequester = remember { FocusRequester() }
    val firstPosterFocusRequester = remember { FocusRequester() }

    LaunchedEffect(rootUri) {
        vm.initBrowse(rootUri, sourceId)
    }

    LaunchedEffect(currentUri) {
        if (searchActive) {
            searchActive = false
            vm.setSearchQuery("")
        }
    }

    fun navigateBack() {
        when {
            searchActive -> {
                searchActive = false
                vm.setSearchQuery("")
            }
            !vm.pop() -> onBack()
        }
    }

    BackHandler(enabled = detailsItem == null) { navigateBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
    ) {
        when (val browseState = state) {
            is BrowseState.Idle -> Unit
            is BrowseState.Loading -> LoadingState()
            is BrowseState.Error -> ErrorState(
                message = browseState.message,
                onRetry = { vm.browse(currentUri) }
            )
            is BrowseState.Success -> {
                val videoItems = browseState.items.filter { it.isVideo }
                val folderItems = browseState.items.filter { it.isDirectory }
                val otherItems = browseState.items.filterNot { it.isVideo || it.isDirectory }
                val selectedItem = videoItems.firstOrNull { it.uri == selectedUri }
                    ?: videoItems.firstOrNull()

                LaunchedEffect(currentUri, videoItems.firstOrNull()?.uri, searchActive) {
                    if (selectedItem != null && !searchActive) {
                        selectedUri = selectedItem.uri
                        delay(120)
                        playFocusRequester.requestFocus()
                    }
                }

                when {
                    browseState.items.isEmpty() && continueWatching.isEmpty() -> EmptyState(
                        if (searchQuery.isBlank()) "No files found" else "No matches for \"$searchQuery\""
                    )
                    selectedItem != null -> ModernLibrary(
                        videos = videoItems,
                        folders = folderItems,
                        otherItems = otherItems,
                        metadataMap = metadataMap,
                        selectedItem = selectedItem,
                        selectedHistory = continueWatching.firstOrNull { it.uri == selectedItem.uri },
                        continueWatching = if (!vm.canGoBack) continueWatching else emptyList(),
                        playFocusRequester = playFocusRequester,
                        firstPosterFocusRequester = firstPosterFocusRequester,
                        onSelected = { selectedUri = it.uri },
                        onPlay = { item ->
                            val title = metadataMap[item.name]?.displayTitle
                                ?: item.name.substringBeforeLast('.')
                            onPlayVideo(item.uri, title, sourceId)
                        },
                        onOpenFolder = { vm.push(it.uri) },
                        onResume = { onPlayVideo(it.uri, it.title, it.sourceId) },
                        onDetails = { detailsItem = it }
                    )
                    else -> LibraryWithoutHero(
                        folders = folderItems,
                        otherItems = otherItems,
                        continueWatching = if (!vm.canGoBack) continueWatching else emptyList(),
                        onOpen = { vm.push(it.uri) },
                        onResume = { onPlayVideo(it.uri, it.title, it.sourceId) }
                    )
                }
            }
        }

        BrowseTopBar(
            title = browseTitle(currentUri, vm.canGoBack),
            searchActive = searchActive,
            searchQuery = searchQuery,
            searchFocusRequester = searchFocusRequester,
            onSearchChanged = vm::setSearchQuery,
            onSearchOpen = { searchActive = true },
            onBack = ::navigateBack,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(5f)
        )

        detailsItem?.let { item ->
            val metadata = metadataMap[item.name]
            DetailsOverlay(
                item = item,
                metadata = metadata,
                onPlay = {
                    detailsItem = null
                    val title = metadata?.displayTitle ?: item.name.substringBeforeLast('.')
                    onPlayVideo(item.uri, title, sourceId)
                },
                onDismiss = {
                    detailsItem = null
                    playFocusRequester.requestFocus()
                }
            )
        }
    }
}

@Composable
private fun ModernLibrary(
    videos: List<BrowseItem>,
    folders: List<BrowseItem>,
    otherItems: List<BrowseItem>,
    metadataMap: Map<String, MediaMetadata>,
    selectedItem: BrowseItem,
    selectedHistory: WatchHistory?,
    continueWatching: List<WatchHistory>,
    playFocusRequester: FocusRequester,
    firstPosterFocusRequester: FocusRequester,
    onSelected: (BrowseItem) -> Unit,
    onPlay: (BrowseItem) -> Unit,
    onOpenFolder: (BrowseItem) -> Unit,
    onResume: (WatchHistory) -> Unit,
    onDetails: (BrowseItem) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heroHeight = maxHeight * 0.64f
        val selectedMetadata = metadataMap[selectedItem.name]

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 48.dp)
        ) {
            item(key = "hero") {
                MediaHero(
                    item = selectedItem,
                    metadata = selectedMetadata,
                    history = selectedHistory,
                    height = heroHeight,
                    playFocusRequester = playFocusRequester,
                    firstPosterFocusRequester = firstPosterFocusRequester,
                    onPlay = { onPlay(selectedItem) },
                    onDetails = { onDetails(selectedItem) }
                )
            }

            item(key = "explore") {
                MediaRail(
                    title = "Explore",
                    videos = videos,
                    metadataMap = metadataMap,
                    selectedUri = selectedItem.uri,
                    continueWatching = continueWatching,
                    playFocusRequester = playFocusRequester,
                    firstPosterFocusRequester = firstPosterFocusRequester,
                    onSelected = onSelected,
                    onPlay = onPlay
                )
            }

            if (folders.isNotEmpty()) {
                item(key = "folders") {
                    FolderRail(folders = folders, onOpen = onOpenFolder)
                }
            }

            if (continueWatching.isNotEmpty()) {
                item(key = "continue") {
                    ContinueWatchingRail(
                        history = continueWatching,
                        videos = videos,
                        metadataMap = metadataMap,
                        onResume = onResume
                    )
                }
            }

            if (otherItems.isNotEmpty()) {
                item(key = "other") {
                    OtherFilesRail(otherItems)
                }
            }
        }
    }
}

@Composable
private fun LibraryGrid(
    items: List<BrowseItem>,
    onOpen: (BrowseItem) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(220.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Dimens.marginH,
            top = 104.dp,
            end = Dimens.marginH,
            bottom = 48.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        items(items, key = { it.uri }) { item ->
            if (item.isDirectory) {
                FolderCard(folder = item, onClick = { onOpen(item) })
            } else {
                StaticFileCard(item)
            }
        }
    }
}

@Composable
private fun LibraryWithoutHero(
    folders: List<BrowseItem>,
    otherItems: List<BrowseItem>,
    continueWatching: List<WatchHistory>,
    onOpen: (BrowseItem) -> Unit,
    onResume: (WatchHistory) -> Unit
) {
    if (continueWatching.isEmpty()) {
        LibraryGrid(items = folders + otherItems, onOpen = onOpen)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 92.dp, bottom = 48.dp)
    ) {
        item(key = "continue") {
            ContinueWatchingRail(
                history = continueWatching,
                videos = emptyList(),
                metadataMap = emptyMap(),
                onResume = onResume
            )
        }
        if (folders.isNotEmpty()) {
            item(key = "folders") {
                FolderRail(folders = folders, onOpen = onOpen)
            }
        }
        if (otherItems.isNotEmpty()) {
            item(key = "other") {
                OtherFilesRail(otherItems)
            }
        }
    }
}

@Composable
private fun BrowseTopBar(
    title: String,
    searchActive: Boolean,
    searchQuery: String,
    searchFocusRequester: FocusRequester,
    onSearchChanged: (String) -> Unit,
    onSearchOpen: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xEB08080E), Color(0x9908080E), Color.Transparent)
                )
            )
            .padding(start = Dimens.marginH, top = 22.dp, end = Dimens.marginH, bottom = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineLarge,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (searchActive) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChanged,
                placeholder = { Text("Search library", color = TextMuted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = TextMuted.copy(alpha = 0.4f),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextSecondary,
                    cursorColor = Accent,
                    focusedContainerColor = Color(0xE61A1924),
                    unfocusedContainerColor = Color(0xE61A1924)
                ),
                modifier = Modifier
                    .width(290.dp)
                    .heightIn(max = 54.dp)
                    .focusRequester(searchFocusRequester)
            )
            LaunchedEffect(Unit) { searchFocusRequester.requestFocus() }
        } else {
            TopBarButton(text = "Search", onClick = onSearchOpen)
        }
        TopBarButton(text = "Back", onClick = onBack)
    }
}

@Composable
private fun TopBarButton(text: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Color.White else Color(0xB31A1924))
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) Color.White else Color.White.copy(alpha = 0.16f),
                RoundedCornerShape(8.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 17.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (focused) BgPrimary else TextSecondary
        )
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 92.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Accent)
            Spacer(Modifier.height(16.dp))
            Text("Loading your library", color = TextSecondary)
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 92.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(message, color = MaterialTheme.colorScheme.error)
            OverlayButton(text = "Retry", primary = true, onClick = onRetry)
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 92.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = TextMuted, style = MaterialTheme.typography.titleLarge)
    }
}

private fun browseTitle(currentUri: String, canGoBack: Boolean): String {
    val parsed = android.net.Uri.parse(currentUri)
    return if (!canGoBack) {
        parsed.getQueryParameter("user")?.ifEmpty { null }
            ?: parsed.getQueryParameter("share")
            ?: "Explore"
    } else {
        val segment = parsed.pathSegments.lastOrNull { it.isNotEmpty() }.orEmpty()
        android.net.Uri.decode(segment).ifEmpty { "Explore" }
    }
}

internal fun mediaFacts(item: BrowseItem, metadata: MediaMetadata?): String {
    return listOfNotNull(
        metadata?.year?.toString(),
        metadata?.mediaType,
        metadata?.genre,
        qualityLabel(item.name)
    ).distinct().joinToString("  |  ")
}

internal fun qualityLabel(filename: String): String? {
    val normalized = filename.lowercase()
    return when {
        "2160p" in normalized || "4k" in normalized -> "4K"
        "1080p" in normalized -> "1080P"
        "720p" in normalized -> "720P"
        else -> null
    }
}
