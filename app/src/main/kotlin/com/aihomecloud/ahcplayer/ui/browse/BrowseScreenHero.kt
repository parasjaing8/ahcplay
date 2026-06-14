package com.aihomecloud.ahcplayer.ui.browse

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.aihomecloud.ahcplayer.data.model.BrowseItem
import com.aihomecloud.ahcplayer.data.model.WatchHistory
import com.aihomecloud.ahcplayer.data.tmdb.MediaMetadata
import com.aihomecloud.ahcplayer.ui.theme.Accent
import com.aihomecloud.ahcplayer.ui.theme.BgCardFocused
import com.aihomecloud.ahcplayer.ui.theme.BgPrimary
import com.aihomecloud.ahcplayer.ui.theme.Dimens
import com.aihomecloud.ahcplayer.ui.theme.TextPrimary
import com.aihomecloud.ahcplayer.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
internal fun MediaHero(
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
