package com.aihomecloud.ahcplayer.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aihomecloud.ahcplayer.data.source.LanHost
import com.aihomecloud.ahcplayer.ui.theme.*

/**
 * "Found on your network" — servers discovered by a subnet sweep, offered for one-tap
 * add so the common case never requires typing an IP. Hidden entirely when there is
 * nothing new to show, so it never becomes empty furniture on the home screen.
 */
@Composable
fun DiscoveredSection(
    hosts: List<LanHost>,
    scanning: Boolean,
    onRescan: () -> Unit,
    onAdd: (LanHost) -> Unit
) {
    if (hosts.isEmpty() && !scanning) return

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Text(
                "Found on your network",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Spacer(Modifier.width(16.dp))
            if (scanning) {
                Text("scanning…", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            } else {
                RescanButton(onRescan)
            }
        }

        if (hosts.isEmpty()) {
            Text(
                "Looking for media servers…",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimens.itemSpacing)) {
                items(hosts, key = { it.address }) { host ->
                    DiscoveredCard(host = host, onClick = { onAdd(host) })
                }
            }
        }
    }
}

@Composable
private fun RescanButton(onRescan: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) BgCardFocused else BgCard.copy(alpha = 0.7f))
            .border(if (focused) Dimens.focusBorder else 0.dp, Accent, RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable { onRescan() }
            .semantics { contentDescription = "Scan again for servers on your network" }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            "Scan again",
            color = if (focused) TextPrimary else TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun DiscoveredCard(host: LanHost, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val kind = when {
        host.hasAhc && host.hasSmb -> "AiHomeCloud · SMB"
        host.hasAhc -> "AiHomeCloud"
        else -> "SMB"
    }
    Column(
        modifier = Modifier
            .width(Dimens.cardWidthWide)
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) BgCardFocused else BgCard)
            .then(
                if (focused) Modifier.border(Dimens.focusBorder, Accent, RoundedCornerShape(8.dp))
                else Modifier
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .semantics { contentDescription = "Add ${host.displayName}, $kind" }
            .padding(16.dp)
    ) {
        Text(
            kind,
            style = MaterialTheme.typography.labelMedium,
            color = if (focused) Accent else TextMuted
        )
        Spacer(Modifier.height(6.dp))
        Text(
            host.displayName,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            maxLines = 1
        )
        Spacer(Modifier.height(2.dp))
        Text("Tap to add", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}
