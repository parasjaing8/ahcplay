package com.aihomecloud.ahcplayer.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aihomecloud.ahcplayer.data.ahc.AhcUserProfile
import com.aihomecloud.ahcplayer.data.model.MediaSource
import com.aihomecloud.ahcplayer.ui.setup.AhcButton
import com.aihomecloud.ahcplayer.ui.theme.*

@Composable
fun ProfileSelectScreen(
    host: String,
    port: Int,
    deviceName: String,
    onSourceAdded: (MediaSource) -> Unit,
    onBack: () -> Unit,
    vm: ProfileSelectViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(host, port) { vm.loadProfiles(host, port) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .padding(horizontal = Dimens.marginH, vertical = Dimens.marginV)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(deviceName, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                Text("$host:$port", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            var backFocused by remember { mutableStateOf(false) }
            TextButton(onClick = onBack, modifier = Modifier.onFocusChanged { backFocused = it.isFocused }) {
                Text("Back", color = if (backFocused) TextPrimary else TextMuted)
            }
        }
        Spacer(Modifier.height(32.dp))

        when (val s = state) {
            is ProfileScreenState.Loading, is ProfileScreenState.Saving -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Accent)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (s is ProfileScreenState.Saving) "Logging in…" else "Loading profiles…",
                            color = TextSecondary
                        )
                    }
                }
            }

            is ProfileScreenState.ShowProfiles -> {
                Text("Who's watching?", style = MaterialTheme.typography.headlineLarge, color = TextPrimary)
                Spacer(Modifier.height(24.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(s.profiles) { profile ->
                        ProfileCard(
                            profile = profile,
                            onClick = { vm.selectProfile(profile, onSourceAdded) }
                        )
                    }
                }
            }

            is ProfileScreenState.PinEntry -> {
                PinEntrySection(
                    profile = s.profile,
                    pin = s.pin,
                    error = s.error,
                    onDigit = { vm.appendPin(it) },
                    onDelete = { vm.deletePin() },
                    onConfirm = { vm.confirmPin(onSourceAdded) },
                    onBack = { vm.retryProfiles() }
                )
            }

            is ProfileScreenState.Errored -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp)
                ) {
                    Text(s.message, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    AhcButton("Retry", onClick = { vm.retryProfiles() })
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(profile: AhcUserProfile, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val avatarColors = listOf(
        Color(0xFF7C4DFF), Color(0xFF00BCD4), Color(0xFF4CAF50),
        Color(0xFFFF9800), Color(0xFFE91E63), Color(0xFF607D8B)
    )
    val colorIndex = (profile.name.hashCode() and 0x7FFFFFFF) % avatarColors.size

    Column(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) BgCardFocused else BgCard)
            .then(if (focused) Modifier.border(Dimens.focusBorder, Accent, RoundedCornerShape(12.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(72.dp).clip(CircleShape).background(avatarColors[colorIndex]),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (profile.iconEmoji.isNotEmpty()) profile.iconEmoji
                else profile.name.take(1).uppercase(),
                fontSize = if (profile.iconEmoji.isNotEmpty()) 32.sp else 28.sp,
                color = Color.White
            )
        }
        Text(profile.name, style = MaterialTheme.typography.titleLarge,
            color = if (focused) TextPrimary else TextSecondary)
        if (profile.hasPin) {
            Text("🔒", fontSize = 16.sp)
        }
    }
}

@Composable
private fun PinEntrySection(
    profile: AhcUserProfile,
    pin: String,
    error: String?,
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    Text("Enter PIN for ${profile.name}",
        style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
    Spacer(Modifier.height(24.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(6) { i ->
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (i < pin.length) Accent else BgCard)
                    .border(1.dp, if (i < pin.length) Accent else TextMuted, CircleShape)
            )
        }
    }

    if (error != null) {
        Spacer(Modifier.height(8.dp))
        Text(error, color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.height(32.dp))

    val digits = listOf("1","2","3","4","5","6","7","8","9","","0","⌫")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        digits.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { label ->
                    if (label.isEmpty()) {
                        Spacer(Modifier.size(72.dp))
                    } else {
                        PinKey(label = label, onClick = {
                            if (label == "⌫") onDelete() else onDigit(label)
                        })
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(24.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        var backFocused by remember { mutableStateOf(false) }
        TextButton(onClick = onBack, modifier = Modifier.onFocusChanged { backFocused = it.isFocused }) {
            Text("Back", color = if (backFocused) TextPrimary else TextMuted)
        }
        AhcButton("Confirm", onClick = onConfirm, enabled = pin.isNotEmpty())
    }
}

@Composable
private fun PinKey(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) BgCardFocused else BgCard)
            .then(if (focused) Modifier.border(Dimens.focusBorder, Accent, RoundedCornerShape(8.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 24.sp, color = if (focused) Accent else TextPrimary)
    }
}
