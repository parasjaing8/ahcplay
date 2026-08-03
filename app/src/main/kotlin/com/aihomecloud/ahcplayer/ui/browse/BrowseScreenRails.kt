package com.aihomecloud.ahcplayer.ui.browse

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.aihomecloud.ahcplayer.data.model.BrowseItem
import com.aihomecloud.ahcplayer.data.model.WatchHistory
import com.aihomecloud.ahcplayer.data.metadata.MediaMetadata
import com.aihomecloud.ahcplayer.ui.theme.Accent
import com.aihomecloud.ahcplayer.ui.theme.BgCard
import com.aihomecloud.ahcplayer.ui.theme.BgCardFocused
import com.aihomecloud.ahcplayer.ui.theme.BgPrimary
import com.aihomecloud.ahcplayer.ui.theme.Dimens
import com.aihomecloud.ahcplayer.ui.theme.TextMuted
import com.aihomecloud.ahcplayer.ui.theme.TextPrimary
import com.aihomecloud.ahcplayer.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
internal fun MediaRail(
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

    val displayTitle = metadata?.displayTitle ?: item.name.substringBeforeLast('.')
    val posterDescription = buildString {
        append(displayTitle)
        listOfNotNull(metadata?.year?.toString(), metadata?.mediaType, qualityLabel(item.name))
            .forEach { append(", "); append(it) }
        history?.let { append(", ${(it.progressFraction * 100).roundToInt()}% watched") }
    }

    Column(
        modifier = modifier
            .width(158.dp)
            .focusProperties { up = playFocusRequester }
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .shadow(elevation, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) BgCardFocused else BgCard)
            .border(if (focused) 3.dp else 1.dp, borderColor, RoundedCornerShape(10.dp))
            .semantics { contentDescription = posterDescription }
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
                title = displayTitle
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
                text = displayTitle,
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
internal fun FolderRail(
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
internal fun FolderCard(folder: BrowseItem, onClick: () -> Unit) {
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
            .semantics { contentDescription = "${folder.name}, collection" }
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
internal fun ContinueWatchingRail(
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
            .semantics {
                contentDescription =
                    "${item.title}, ${(item.progressFraction * 100).roundToInt()}% watched, resume"
            }
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
internal fun OtherFilesRail(items: List<BrowseItem>) {
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
internal fun StaticFileCard(item: BrowseItem) {
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
internal fun PosterImage(
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
