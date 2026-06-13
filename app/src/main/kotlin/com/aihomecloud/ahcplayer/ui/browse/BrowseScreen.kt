package com.aihomecloud.ahcplayer.ui.browse

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.aihomecloud.ahcplayer.data.model.BrowseItem
import com.aihomecloud.ahcplayer.data.model.WatchHistory
import com.aihomecloud.ahcplayer.data.tmdb.MediaMetadata
import com.aihomecloud.ahcplayer.ui.theme.Accent
import com.aihomecloud.ahcplayer.ui.theme.BgCard
import com.aihomecloud.ahcplayer.ui.theme.BgCardFocused
import com.aihomecloud.ahcplayer.ui.theme.BgPrimary
import com.aihomecloud.ahcplayer.ui.theme.Dimens
import com.aihomecloud.ahcplayer.ui.theme.Overlay
import com.aihomecloud.ahcplayer.ui.theme.TextMuted
import com.aihomecloud.ahcplayer.ui.theme.TextPrimary
import com.aihomecloud.ahcplayer.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

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
private fun MediaHero(
    item: BrowseItem,
    metadata: MediaMetadata?,
    history: WatchHistory?,
    height: Dp,
    playFocusRequester: FocusRequester,
    firstPosterFocusRequester: FocusRequester,
    onPlay: () -> Unit,
    onDetails: () -> Unit
) {
    val imageUrl = metadata?.backdropUrl ?: metadata?.posterUrl
    val usingPosterFallback = metadata?.backdropUrl == null && metadata?.posterUrl != null
    val title = metadata?.displayTitle ?: item.name.substringBeforeLast('.')

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF241B3D), Color(0xFF11101A), BgPrimary)
                )
            )
    ) {
        if (imageUrl != null) {
            SubcomposeAsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (usingPosterFallback) {
                            Modifier
                                .graphicsLayer(scaleX = 1.18f, scaleY = 1.18f)
                                .blur(26.dp)
                        } else {
                            Modifier
                        }
                    ),
                loading = { HeroImagePlaceholder() },
                error = { HeroImagePlaceholder() }
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color(0xF2070710),
                        0.52f to Color(0xB3070710),
                        1f to Color(0x26070710)
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x7A070710),
                        0.58f to Color.Transparent,
                        1f to BgPrimary
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .widthIn(max = 660.dp)
                .padding(start = Dimens.marginH, end = 24.dp, bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 46.sp,
                lineHeight = 49.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            val facts = mediaFacts(item, metadata)
            if (facts.isNotEmpty()) {
                Text(
                    text = facts,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = metadata?.overview?.takeIf { it.isNotBlank() }
                    ?: "Ready to play from your private library.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                lineHeight = 22.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 590.dp)
            )

            if (history != null) {
                ResumeProgress(history)
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeroActionButton(
                    text = if (history == null) "Play" else "Resume",
                    primary = true,
                    focusRequester = playFocusRequester,
                    downFocusRequester = firstPosterFocusRequester,
                    onClick = onPlay
                )
                HeroActionButton(
                    text = "Details",
                    primary = false,
                    downFocusRequester = firstPosterFocusRequester,
                    onClick = onDetails
                )
            }
        }
    }
}

@Composable
private fun HeroActionButton(
    text: String,
    primary: Boolean,
    downFocusRequester: FocusRequester,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = tween(180),
        label = "heroActionScale"
    )
    val background by animateColorAsState(
        targetValue = when {
            primary && focused -> Color.White
            primary -> Accent
            focused -> BgCardFocused
            else -> Color(0xB31A1924)
        },
        animationSpec = tween(180),
        label = "heroActionBackground"
    )
    val foreground = if (primary && focused) BgPrimary else TextPrimary

    Box(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusProperties { down = downFocusRequester }
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .shadow(if (focused) 18.dp else 4.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(8.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 30.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = foreground)
    }
}

@Composable
private fun MediaRail(
    title: String,
    videos: List<BrowseItem>,
    metadataMap: Map<String, MediaMetadata>,
    selectedUri: String,
    continueWatching: List<WatchHistory>,
    playFocusRequester: FocusRequester,
    firstPosterFocusRequester: FocusRequester,
    onSelected: (BrowseItem) -> Unit,
    onPlay: (BrowseItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgPrimary)
            .padding(top = 8.dp, bottom = 22.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.marginH),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${videos.size} title${if (videos.size == 1) "" else "s"}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = Dimens.marginH, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            itemsIndexed(videos, key = { _, item -> item.uri }) { index, item ->
                PosterCard(
                    item = item,
                    metadata = metadataMap[item.name],
                    history = continueWatching.firstOrNull { it.uri == item.uri },
                    selected = item.uri == selectedUri,
                    playFocusRequester = playFocusRequester,
                    modifier = if (index == 0) {
                        Modifier.focusRequester(firstPosterFocusRequester)
                    } else {
                        Modifier
                    },
                    onFocused = { onSelected(item) },
                    onClick = { onPlay(item) }
                )
            }
        }
    }
}

@Composable
private fun PosterCard(
    item: BrowseItem,
    metadata: MediaMetadata?,
    history: WatchHistory?,
    selected: Boolean,
    playFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) Dimens.focusScale else 1f,
        animationSpec = tween(190),
        label = "posterScale"
    )
    val elevation by animateDpAsState(
        targetValue = if (focused) 22.dp else 4.dp,
        animationSpec = tween(190),
        label = "posterElevation"
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused) Accent else Color.Transparent,
        animationSpec = tween(190),
        label = "posterBorder"
    )

    Column(
        modifier = modifier
            .width(158.dp)
            .focusProperties { up = playFocusRequester }
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .shadow(elevation, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) BgCardFocused else BgCard)
            .border(if (focused) 3.dp else 1.dp, borderColor, RoundedCornerShape(10.dp))
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF2D2442), Color(0xFF12111A))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            PosterImage(
                url = metadata?.posterUrl,
                title = metadata?.displayTitle ?: item.name.substringBeforeLast('.')
            )

            qualityLabel(item.name)?.let { quality ->
                Text(
                    text = quality,
                    color = TextPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xCC08080D))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }

            if (selected && !focused) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .border(1.dp, Accent.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                )
            }

            if (history != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .background(Color(0x99000000))
                        .align(Alignment.BottomCenter)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(history.progressFraction.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(Accent)
                    )
                }
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = metadata?.displayTitle ?: item.name.substringBeforeLast('.'),
                style = MaterialTheme.typography.bodyMedium,
                color = if (focused) TextPrimary else TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOfNotNull(metadata?.year?.toString(), metadata?.mediaType)
                    .joinToString(" | ")
                    .ifEmpty { "From your library" },
                style = MaterialTheme.typography.labelMedium,
                color = if (focused) Accent else TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FolderRail(
    folders: List<BrowseItem>,
    onOpen: (BrowseItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 24.dp)
    ) {
        Text(
            "Collections",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = Dimens.marginH, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = Dimens.marginH, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(folders, key = { it.uri }) { folder ->
                FolderCard(folder = folder, onClick = { onOpen(folder) })
            }
        }
    }
}

@Composable
private fun FolderCard(folder: BrowseItem, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) 1.06f else 1f,
        tween(180),
        label = "folderScale"
    )

    Box(
        modifier = Modifier
            .width(238.dp)
            .height(112.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .shadow(if (focused) 16.dp else 3.dp, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.linearGradient(
                    if (focused) {
                        listOf(Color(0xFF5838B8), Color(0xFF241C3E))
                    } else {
                        listOf(Color(0xFF262033), Color(0xFF16151E))
                    }
                )
            )
            .border(
                if (focused) 3.dp else 1.dp,
                if (focused) Accent else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(10.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(18.dp)
    ) {
        Column(modifier = Modifier.align(Alignment.BottomStart)) {
            Text(
                "COLLECTION",
                style = MaterialTheme.typography.labelMedium,
                color = if (focused) Color.White else Accent
            )
            Spacer(Modifier.height(5.dp))
            Text(
                folder.name,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ContinueWatchingRail(
    history: List<WatchHistory>,
    videos: List<BrowseItem>,
    metadataMap: Map<String, MediaMetadata>,
    onResume: (WatchHistory) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 24.dp)
    ) {
        Text(
            "Continue Watching",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = Dimens.marginH, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = Dimens.marginH, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(history, key = { it.uri }) { item ->
                val browseItem = videos.firstOrNull { it.uri == item.uri }
                ContinueCard(
                    item = item,
                    metadata = browseItem?.let { metadataMap[it.name] },
                    onClick = { onResume(item) }
                )
            }
        }
    }
}

@Composable
private fun ContinueCard(
    item: WatchHistory,
    metadata: MediaMetadata?,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) 1.06f else 1f,
        tween(180),
        label = "continueScale"
    )

    Column(
        modifier = Modifier
            .width(250.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .shadow(if (focused) 16.dp else 3.dp, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) BgCardFocused else BgCard)
            .border(
                if (focused) 3.dp else 1.dp,
                if (focused) Accent else Color.Transparent,
                RoundedCornerShape(10.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF2B2143), Color(0xFF111018))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            PosterImage(
                url = metadata?.backdropUrl ?: metadata?.posterUrl,
                title = item.title,
                contentScale = ContentScale.Crop
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000))))
            )
            Text(
                "RESUME",
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(Color(0x99000000))
                    .align(Alignment.BottomCenter)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(item.progressFraction.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(Accent)
                )
            }
        }
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (focused) TextPrimary else TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${(item.progressFraction * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = Accent
            )
        }
    }
}

@Composable
private fun OtherFilesRail(items: List<BrowseItem>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 24.dp)
    ) {
        Text(
            "Other Files",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = Dimens.marginH, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = Dimens.marginH, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(items, key = { it.uri }) { item ->
                StaticFileCard(item)
            }
        }
    }
}

@Composable
private fun StaticFileCard(item: BrowseItem) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .width(220.dp)
            .height(90.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (focused) BgCardFocused else BgCard)
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) Accent else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(9.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = false) {}
            .padding(16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            item.name,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
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
private fun DetailsOverlay(
    item: BrowseItem,
    metadata: MediaMetadata?,
    onPlay: () -> Unit,
    onDismiss: () -> Unit
) {
    val playFocusRequester = remember { FocusRequester() }
    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(20f)
            .background(Overlay),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 880.dp)
                .heightIn(max = 500.dp)
                .padding(32.dp)
                .shadow(32.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF15141D))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .padding(30.dp),
            horizontalArrangement = Arrangement.spacedBy(30.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(210.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF2D2442), Color(0xFF12111A))
                        )
                    )
            ) {
                PosterImage(
                    url = metadata?.posterUrl,
                    title = metadata?.displayTitle ?: item.name.substringBeforeLast('.')
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    metadata?.displayTitle ?: item.name.substringBeforeLast('.'),
                    style = MaterialTheme.typography.displayMedium,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    mediaFacts(item, metadata).ifEmpty { "From your private library" },
                    style = MaterialTheme.typography.titleLarge,
                    color = Accent
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    metadata?.overview?.takeIf { it.isNotBlank() }
                        ?: "No synopsis is available for this title yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    lineHeight = 23.sp,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(28.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    OverlayButton(
                        text = "Play",
                        primary = true,
                        focusRequester = playFocusRequester,
                        onClick = onPlay
                    )
                    OverlayButton(text = "Close", primary = false, onClick = onDismiss)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(80)
        playFocusRequester.requestFocus()
    }
}

@Composable
private fun OverlayButton(
    text: String,
    primary: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    primary && focused -> Color.White
                    primary -> Accent
                    focused -> BgCardFocused
                    else -> BgCard
                }
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) Color.White else Color.White.copy(alpha = 0.15f),
                RoundedCornerShape(8.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 11.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleLarge,
            color = if (primary && focused) BgPrimary else TextPrimary
        )
    }
}

@Composable
private fun PosterImage(
    url: String?,
    title: String,
    contentScale: ContentScale = ContentScale.Crop
) {
    if (url != null) {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = null,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
            loading = { PosterPlaceholder(title) },
            error = { PosterPlaceholder(title) }
        )
    } else {
        PosterPlaceholder(title)
    }
}

@Composable
private fun PosterPlaceholder(title: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF302546), Color(0xFF15131E))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            title.take(1).uppercase(),
            color = Accent.copy(alpha = 0.8f),
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun HeroImagePlaceholder() {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF2A2040), Color(0xFF111019), BgPrimary)
                )
            )
    )
}

@Composable
private fun ResumeProgress(history: WatchHistory) {
    Column(
        modifier = Modifier.width(300.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "CONTINUE WATCHING",
                style = MaterialTheme.typography.labelMedium,
                color = Accent,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${(history.progressFraction * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.2f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(history.progressFraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(Accent)
            )
        }
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

private fun mediaFacts(item: BrowseItem, metadata: MediaMetadata?): String {
    return listOfNotNull(
        metadata?.year?.toString(),
        metadata?.mediaType,
        metadata?.genre,
        qualityLabel(item.name)
    ).distinct().joinToString("  |  ")
}

private fun qualityLabel(filename: String): String? {
    val normalized = filename.lowercase()
    return when {
        "2160p" in normalized || "4k" in normalized -> "4K"
        "1080p" in normalized -> "1080P"
        "720p" in normalized -> "720P"
        else -> null
    }
}
