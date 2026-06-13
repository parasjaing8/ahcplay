package com.aihomecloud.ahcplayer.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aihomecloud.ahcplayer.data.model.MediaSource
import com.aihomecloud.ahcplayer.data.model.SourceType
import com.aihomecloud.ahcplayer.data.model.WatchHistory
import com.aihomecloud.ahcplayer.ui.theme.*

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
    val ahcSources = sources.filter { it.sourceType == SourceType.AHC }

    if (ahcSources.isNotEmpty()) {
        WhoIsWatchingLayout(
            ahcSources = ahcSources,
            allSources = sources,
            onBrowseSource = onBrowseSource,
            onAddSource = onAddSource,
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
    onBrowseSource: (MediaSource) -> Unit,
    onAddSource: () -> Unit,
    onSettings: () -> Unit
) {
    val smbSources = allSources.filter { it.sourceType == SourceType.SMB }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF090910))) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(56.dp))

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
                    Text("Settings", color = if (settingsFocused) TextPrimary else TextMuted,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(Modifier.height(48.dp))

            Text(
                "Who's watching?",
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text("AHC Player", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            Spacer(Modifier.height(52.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(36.dp)
            ) {
                items(ahcSources) { src ->
                    ProfileCircle(source = src, onClick = { onBrowseSource(src) })
                }
                item { AddProfileCircle(onClick = onAddSource) }
            }

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

            Spacer(Modifier.height(80.dp))
        }
    }
}

private val avatarPalette = listOf(
    Color(0xFF7C4DFF), Color(0xFF00BCD4), Color(0xFF4CAF50),
    Color(0xFFFF9800), Color(0xFFE91E63), Color(0xFF607D8B),
    Color(0xFF9C27B0), Color(0xFF03A9F4)
)

@Composable
private fun ProfileCircle(source: MediaSource, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val name = source.username.ifEmpty { source.name }
    val color = avatarPalette[(name.hashCode() and 0x7FFFFFFF) % avatarPalette.size]

    Column(
        modifier = Modifier
            .width(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) BgCardFocused else Color.Transparent)
            .then(if (focused) Modifier.border(Dimens.focusBorder, Accent, RoundedCornerShape(12.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(name.take(1).uppercase(), fontSize = 36.sp, color = Color.White)
            }
            if (source.hasPin) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(BgPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🔒", fontSize = 12.sp)
                }
            }
        }
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (focused) TextPrimary else TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AddProfileCircle(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) BgCardFocused else Color.Transparent)
            .then(if (focused) Modifier.border(Dimens.focusBorder, Accent, RoundedCornerShape(12.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .border(1.dp, TextMuted.copy(alpha = 0.4f), CircleShape)
                .clip(CircleShape)
                .background(BgCard),
            contentAlignment = Alignment.Center
        ) {
            Text("+", fontSize = 40.sp, color = if (focused) TextPrimary else TextMuted)
        }
        Text(
            "Add Profile",
            style = MaterialTheme.typography.bodyMedium,
            color = if (focused) TextSecondary else TextMuted,
            textAlign = TextAlign.Center
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
