package com.aihomecloud.ahcplayer.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.aihomecloud.ahcplayer.data.model.MediaSource
import com.aihomecloud.ahcplayer.data.model.SourceType
import com.aihomecloud.ahcplayer.data.model.WatchHistory
import com.aihomecloud.ahcplayer.ui.theme.*

private val ProfileAvatarSize = 112.dp
private val ProfileRingStroke = 4.dp
private const val ProfileAutoSelectMs = 3000

@Composable
fun HomeScreen(
    onBrowseSource: (MediaSource) -> Unit,
    onAddSource: () -> Unit,
    onSettings: () -> Unit,
    onResume: (WatchHistory) -> Unit,
    vm: HomeViewModel = viewModel()
) {
    val sources by vm.sources.collectAsStateWithLifecycle()
    val continueWatching by vm.continueWatching.collectAsStateWithLifecycle()
    val libraryStats by vm.libraryStats.collectAsStateWithLifecycle()
    val ahcSources = sources.filter { it.sourceType == SourceType.AHC }
    val lastUsedSourceId = continueWatching.firstOrNull()?.sourceId

    if (ahcSources.isNotEmpty()) {
        WhoIsWatchingLayout(
            ahcSources = ahcSources,
            allSources = sources,
            libraryStats = libraryStats,
            lastUsedSourceId = lastUsedSourceId,
            onBrowseSource = onBrowseSource,
            onSettings = onSettings
        )
    } else {
        ClassicHomeLayout(
            sources = sources,
            continueWatching = continueWatching,
            onBrowseSource = onBrowseSource,
            onAddSource = onAddSource,
            onSettings = onSettings,
            onResume = onResume
        )
    }
}

// ── Who's Watching (JioHotstar-style) ──────────────────────────────────────

@Composable
private fun WhoIsWatchingLayout(
    ahcSources: List<MediaSource>,
    allSources: List<MediaSource>,
    libraryStats: LibraryStats,
    lastUsedSourceId: Long?,
    onBrowseSource: (MediaSource) -> Unit,
    onSettings: () -> Unit
) {
    val smbSources = allSources.filter { it.sourceType == SourceType.SMB }
    val internalStorageSource = MediaSource(
        id = -1L,
        name = "Internal Storage",
        host = "/storage/emulated/0",
        share = "",
        sourceType = SourceType.INTERNAL
    )
    val profiles = ahcSources + internalStorageSource
    val defaultIndex = profiles.indexOfFirst { it.id == lastUsedSourceId }.let { if (it >= 0) it else 0 }

    Box(modifier = Modifier.fillMaxSize()) {
        ProfileBackdrop(backdrops = libraryStats.backdrops)

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            // Settings row at top-right
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.marginH),
                horizontalArrangement = Arrangement.End
            ) {
                var settingsFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (settingsFocused) BgCardFocused else Color.Transparent)
                        .then(if (settingsFocused) Modifier.border(1.dp, Accent, RoundedCornerShape(8.dp)) else Modifier)
                        .onFocusChanged { settingsFocused = it.isFocused }
                        .clickable { onSettings() }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("⚙ Settings", color = if (settingsFocused) TextPrimary else TextMuted,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(Modifier.height(16.dp))

            Text("AiHomeCloud", style = MaterialTheme.typography.displayMedium, color = Accent)
            Spacer(Modifier.height(6.dp))
            Text("Your Personal Streaming Platform",
                style = MaterialTheme.typography.bodyMedium, color = TextSecondary)

            Spacer(Modifier.height(20.dp))

            Text(
                "Who's Watching?",
                style = MaterialTheme.typography.displayLarge,
                color = TextPrimary
            )
            Spacer(Modifier.height(28.dp))

            ProfileRow(
                profiles = profiles,
                defaultIndex = defaultIndex,
                onSelect = onBrowseSource
            )

            Spacer(Modifier.height(16.dp))

            val statsParts = buildList {
                if (libraryStats.movies > 0) add("${libraryStats.movies} Movies")
                if (libraryStats.shows > 0) add("${libraryStats.shows} Shows")
                add("${ahcSources.size} Profile${if (ahcSources.size == 1) "" else "s"}")
            }
            Text(
                statsParts.joinToString("   •   "),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )

            if (smbSources.isNotEmpty()) {
                Spacer(Modifier.height(48.dp))
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.marginH)) {
                    Text("Local Storage",
                        style = MaterialTheme.typography.titleLarge, color = TextSecondary)
                    Spacer(Modifier.height(16.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimens.itemSpacing)) {
                        items(smbSources) { src ->
                            SourceCard(source = src, onClick = { onBrowseSource(src) })
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ProfileBackdrop(backdrops: List<String>) {
    Box(modifier = Modifier.fillMaxSize().background(BgPrimary)) {
        if (backdrops.isNotEmpty()) {
            val cells = List(6) { i -> backdrops[i % backdrops.size] }
            Column(modifier = Modifier.fillMaxSize().blur(24.dp)) {
                repeat(2) { row ->
                    Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        repeat(3) { col ->
                            SubcomposeAsyncImage(
                                model = cells[row * 3 + col],
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                loading = { Box(Modifier.fillMaxSize().background(BgCard)) },
                                error = { Box(Modifier.fillMaxSize().background(BgCard)) }
                            )
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(AccentDim.copy(alpha = 0.35f), BgPrimary, BgPrimary))
                )
            )
        }
        // Dark overlay so foreground content stays legible
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)))
        // Vignette
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)))
            )
        )
    }
}

private val avatarPalette = listOf(
    Color(0xFF7C4DFF), Color(0xFF00BCD4), Color(0xFF4CAF50),
    Color(0xFFFF9800), Color(0xFFE91E63), Color(0xFF607D8B),
    Color(0xFF9C27B0), Color(0xFF03A9F4)
)

/**
 * JioHotstar-style profile row: the currently selected profile (initially the
 * last-used one) shows an animated ring that sweeps a full circle over
 * [ProfileAutoSelectMs]. If the ring completes without the user moving focus,
 * that profile auto-opens. Moving focus to another profile restarts the ring
 * on the newly focused profile.
 */
@Composable
private fun ProfileRow(
    profiles: List<MediaSource>,
    defaultIndex: Int,
    onSelect: (MediaSource) -> Unit
) {
    var selectedIndex by remember { mutableStateOf(defaultIndex.coerceIn(0, profiles.lastIndex)) }
    val progress = remember { Animatable(0f) }
    val focusRequesters = remember(profiles.size) { List(profiles.size) { FocusRequester() } }

    LaunchedEffect(Unit) {
        focusRequesters.getOrNull(selectedIndex)?.requestFocus()
    }

    LaunchedEffect(selectedIndex) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(ProfileAutoSelectMs, easing = LinearEasing))
        profiles.getOrNull(selectedIndex)?.let(onSelect)
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 80.dp),
        horizontalArrangement = Arrangement.spacedBy(40.dp)
    ) {
        itemsIndexed(profiles) { index, src ->
            ProfileCard(
                source = src,
                showRing = index == selectedIndex,
                progress = if (index == selectedIndex) progress.value else 0f,
                modifier = Modifier
                    .focusRequester(focusRequesters[index])
                    .onFocusChanged { if (it.isFocused) selectedIndex = index },
                onClick = { onSelect(src) }
            )
        }
    }
}

@Composable
private fun ProfileCard(
    source: MediaSource,
    showRing: Boolean,
    progress: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val name = source.username.ifEmpty { source.name }
    val color = avatarPalette[(name.hashCode() and 0x7FFFFFFF) % avatarPalette.size]

    val scale by animateFloatAsState(if (focused) Dimens.focusScale else 1f, tween(200), label = "profileScale")
    val elevation by animateDpAsState(if (focused) 16.dp else 0.dp, tween(200), label = "profileElevation")

    Column(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(ProfileAvatarSize)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .shadow(elevation, CircleShape, ambientColor = Accent, spotColor = Accent)
                .drawWithContent {
                    drawContent()
                    if (showRing) {
                        val stroke = ProfileRingStroke.toPx()
                        val arcTopLeft = Offset(stroke / 2f, stroke / 2f)
                        val arcSize = Size(size.width - stroke, size.height - stroke)
                        drawArc(
                            color = AccentDim.copy(alpha = 0.35f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = Accent,
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                }
                .padding(ProfileRingStroke + 2.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            if (source.sourceType == SourceType.INTERNAL) {
                Text("📁", fontSize = 36.sp, color = Color.White)
            } else {
                Text(name.take(1).uppercase(), fontSize = 40.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
            if (source.hasPin) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(BgPrimary.copy(alpha = 0.8f))
                        .semantics { contentDescription = "PIN protected" },
                    contentAlignment = Alignment.Center
                ) {
                    Text("🔒", fontSize = 11.sp)
                }
            }
        }
        Text(
            name,
            style = MaterialTheme.typography.titleLarge,
            color = if (focused) TextPrimary else TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── Classic Home (no AHC configured) ──────────────────────────────────────

@Composable
private fun ClassicHomeLayout(
    sources: List<MediaSource>,
    continueWatching: List<WatchHistory>,
    onBrowseSource: (MediaSource) -> Unit,
    onAddSource: () -> Unit,
    onSettings: () -> Unit,
    onResume: (WatchHistory) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(BgPrimary)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        ) {
            HeroBanner(onSettings = onSettings)
            Column(
                modifier = Modifier.padding(horizontal = Dimens.marginH, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing)
            ) {
                if (continueWatching.isNotEmpty()) {
                    SectionRow(title = "Continue Watching") {
                        continueWatching.forEach { item ->
                            ResumeCard(item = item, onClick = { onResume(item) })
                        }
                    }
                }
                SectionRow(title = "My Sources") {
                    sources.forEach { src ->
                        SourceCard(source = src, onClick = { onBrowseSource(src) })
                    }
                    AddSourceCard(onClick = onAddSource)
                }
            }
        }
    }
}

@Composable
private fun HeroBanner(onSettings: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(AccentDim.copy(alpha = 0.4f), BgPrimary)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = Dimens.marginH, vertical = 32.dp)
        ) {
            Text("AHC Player", color = TextPrimary, fontSize = 48.sp,
                style = MaterialTheme.typography.displayLarge)
            Text("Your private media, beautifully.",
                color = TextSecondary, style = MaterialTheme.typography.titleLarge)
        }
        var settingsFocused by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (settingsFocused) BgCardFocused else BgCard.copy(alpha = 0.7f))
                .border(if (settingsFocused) Dimens.focusBorder else 0.dp, Accent, RoundedCornerShape(8.dp))
                .onFocusChanged { settingsFocused = it.isFocused }
                .clickable { onSettings() }
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("Settings", color = if (settingsFocused) TextPrimary else TextSecondary,
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SectionRow(title: String, content: @Composable RowScope.() -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = TextPrimary,
            modifier = Modifier.padding(bottom = 16.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimens.itemSpacing)) {
            item { Row { content() } }
        }
    }
}

@Composable
private fun ResumeCard(item: WatchHistory, onClick: () -> Unit) {
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
            Text("▶", fontSize = 32.sp, color = if (focused) Accent else TextMuted)
            Box(
                modifier = Modifier.fillMaxWidth().height(3.dp)
                    .background(AccentDim.copy(alpha = 0.3f)).align(Alignment.BottomCenter)
            ) {
                Box(modifier = Modifier.fillMaxWidth(item.progressFraction).fillMaxHeight().background(Accent))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(item.title, style = MaterialTheme.typography.bodyMedium,
            color = if (focused) TextPrimary else TextSecondary,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp))
        Text("${(item.progressFraction * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium, color = Accent,
            modifier = Modifier.padding(horizontal = 8.dp))
    }
}

@Composable
private fun SourceCard(source: MediaSource, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .width(Dimens.cardWidth)
            .height(Dimens.cardHeight * 0.5f)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (focused) Brush.verticalGradient(listOf(Accent.copy(alpha = 0.3f), BgCardFocused))
                else Brush.verticalGradient(listOf(AccentDim.copy(alpha = 0.15f), BgCard))
            )
            .then(if (focused) Modifier.border(Dimens.focusBorder, Accent, RoundedCornerShape(8.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("NAS", fontSize = 28.sp, color = if (focused) Accent else TextSecondary)
            Spacer(Modifier.height(8.dp))
            Text(source.name, style = MaterialTheme.typography.bodyMedium,
                color = if (focused) TextPrimary else TextSecondary,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AddSourceCard(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .width(Dimens.cardWidth)
            .height(Dimens.cardHeight * 0.5f)
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) BgCardFocused else BgCard)
            .border(2.dp, if (focused) Accent else TextMuted.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("+", fontSize = 36.sp, color = if (focused) Accent else TextMuted)
            Spacer(Modifier.height(8.dp))
            Text("Add Source", style = MaterialTheme.typography.bodyMedium,
                color = if (focused) TextPrimary else TextMuted)
        }
    }
}
