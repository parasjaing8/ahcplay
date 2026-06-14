package com.aihomecloud.ahcplayer.ui.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.aihomecloud.ahcplayer.data.model.BrowseItem
import com.aihomecloud.ahcplayer.data.tmdb.MediaMetadata
import com.aihomecloud.ahcplayer.ui.theme.Accent
import com.aihomecloud.ahcplayer.ui.theme.BgCard
import com.aihomecloud.ahcplayer.ui.theme.BgCardFocused
import com.aihomecloud.ahcplayer.ui.theme.BgPrimary
import com.aihomecloud.ahcplayer.ui.theme.Overlay
import com.aihomecloud.ahcplayer.ui.theme.TextPrimary
import com.aihomecloud.ahcplayer.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
internal fun DetailsOverlay(
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
internal fun OverlayButton(
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
