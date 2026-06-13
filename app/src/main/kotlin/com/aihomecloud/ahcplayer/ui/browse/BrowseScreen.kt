package com.aihomecloud.ahcplayer.ui.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.aihomecloud.ahcplayer.data.model.BrowseItem
import com.aihomecloud.ahcplayer.data.model.WatchHistory
import com.aihomecloud.ahcplayer.data.tmdb.MediaMetadata
import com.aihomecloud.ahcplayer.ui.theme.*

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
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(rootUri) {
        vm.initBrowse(rootUri, sourceId)
    }

    // Collapse search when navigating into a new folder
    LaunchedEffect(currentUri) {
        if (searchActive) {
            searchActive = false
            vm.setSearchQuery("")
        }
    }

    BackHandler { if (!vm.pop()) onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .padding(horizontal = Dimens.marginH, vertical = Dimens.marginV)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = run {
                    val parsed = android.net.Uri.parse(currentUri)
                    if (!vm.canGoBack) {
                        parsed.getQueryParameter("user")?.ifEmpty { null }
                            ?: parsed.getQueryParameter("share")
                            ?: "Media"
                    } else {
                        val seg = parsed.pathSegments.lastOrNull { it.isNotEmpty() }.orEmpty()
                        android.net.Uri.decode(seg).ifEmpty { "Media" }
                    }
                },
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            if (searchActive) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { vm.setSearchQuery(it) },
                    placeholder = { Text("Search…", color = TextMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = TextMuted.copy(alpha = 0.4f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextSecondary,
                        cursorColor = Accent,
                        focusedContainerColor = BgCard,
                        unfocusedContainerColor = BgCard
                    ),
                    modifier = Modifier.width(240.dp).focusRequester(searchFocusRequester)
                )
                LaunchedEffect(Unit) { searchFocusRequester.requestFocus() }
            } else {
                SearchIconButton(onClick = { searchActive = true })
            }
            Spacer(Modifier.width(8.dp))
            if (vm.canGoBack) {
                BackButton(onClick = { if (!vm.pop()) onBack() })
            } else {
                BackButton(onClick = onBack)
            }
        }

        Spacer(Modifier.height(16.dp))

        when (val s = state) {
            is BrowseState.Idle -> {}
            is BrowseState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }
            }
            is BrowseState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.message, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { vm.browse(currentUri) },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                            Text("Retry")
                        }
                    }
                }
            }
            is BrowseState.Success -> {
                if (s.items.isEmpty() && continueWatching.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No files found", color = TextMuted)
                    }
                } else {
                    val atRoot = !vm.canGoBack
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(Dimens.cardWidth),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.itemSpacing),
                        verticalArrangement = Arrangement.spacedBy(Dimens.itemSpacing)
                    ) {
                        if (atRoot && continueWatching.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    "Continue Watching",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimens.itemSpacing)) {
                                    items(continueWatching) { item ->
                                        BrowseResumeCard(item = item, onClick = {
                                            onPlayVideo(item.uri, item.title, item.sourceId)
                                        })
                                    }
                                }
                            }
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider(color = TextMuted.copy(alpha = 0.15f))
                                Spacer(Modifier.height(16.dp))
                            }
                        }
                        items(s.items) { item ->
                            val meta = metadataMap[item.name]
                            BrowseCard(
                                item = item,
                                metadata = meta,
                                onClick = {
                                    if (item.isDirectory) vm.push(item.uri)
                                    else if (item.isVideo) {
                                        val title = meta?.displayTitle ?: item.name.substringBeforeLast('.')
                                        onPlayVideo(item.uri, title, sourceId)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseResumeCard(item: WatchHistory, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .width(Dimens.cardWidthWide)
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) BgCardFocused else BgCard)
            .then(if (focused) Modifier.border(Dimens.focusBorder, Accent, RoundedCornerShape(8.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .padding(bottom = 8.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(Dimens.cardHeightWide).background(BgPrimary),
            contentAlignment = Alignment.Center
        ) {
            Text("▶", fontSize = 28.sp, color = if (focused) Accent else TextMuted)
            Box(
                modifier = Modifier.fillMaxWidth().height(3.dp)
                    .background(AccentDim.copy(alpha = 0.3f)).align(Alignment.BottomCenter)
            ) {
                Box(modifier = Modifier.fillMaxWidth(item.progressFraction).fillMaxHeight().background(Accent))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            item.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (focused) TextPrimary else TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Text(
            "${(item.progressFraction * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = Accent,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

@Composable
private fun SearchIconButton(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) BgCardFocused else BgCard)
            .then(if (focused) Modifier.border(Dimens.focusBorder, Accent, RoundedCornerShape(8.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("🔍", fontSize = 20.sp)
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) BgCardFocused else BgCard)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text("Back", color = if (focused) Accent else TextMuted,
            style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun BrowseCard(
    item: BrowseItem,
    metadata: MediaMetadata?,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale = if (focused) Dimens.focusScale else 1f

    Column(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) BgCardFocused else BgCard)
            .then(if (focused) Modifier.border(Dimens.focusBorder, Accent, RoundedCornerShape(8.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(Dimens.cardHeight * 0.75f).background(BgPrimary),
            contentAlignment = Alignment.Center
        ) {
            when {
                item.isDirectory -> {
                    Text("▶", style = MaterialTheme.typography.displayMedium,
                        color = if (focused) Accent else TextSecondary)
                }
                metadata?.posterUrl != null -> {
                    SubcomposeAsyncImage(
                        model = metadata.posterUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = {
                            Box(Modifier.fillMaxSize().background(BgCard),
                                contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Accent, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        },
                        error = {
                            Box(Modifier.fillMaxSize().background(BgCard),
                                contentAlignment = Alignment.Center) {
                                Text("▶", style = MaterialTheme.typography.displayMedium,
                                    color = if (focused) Accent else TextSecondary)
                            }
                        }
                    )
                }
                else -> {
                    Text("▶", style = MaterialTheme.typography.displayMedium,
                        color = if (focused) Accent else TextSecondary)
                }
            }
            if (focused) {
                Box(modifier = Modifier.fillMaxSize().background(AccentGlow))
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = metadata?.displayTitle ?: item.name.substringBeforeLast('.'),
            style = MaterialTheme.typography.bodyMedium,
            color = if (focused) TextPrimary else TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        if (metadata?.year != null) {
            Text("${metadata.year}", style = MaterialTheme.typography.labelMedium,
                color = TextMuted, modifier = Modifier.padding(horizontal = 8.dp))
        }
    }
}
