package com.aihomecloud.ahcplayer.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.aihomecloud.ahcplayer.data.model.MediaSource
import com.aihomecloud.ahcplayer.ui.theme.*

@Composable
fun PinAuthScreen(
    source: MediaSource,
    onSuccess: () -> Unit,
    onBack: () -> Unit,
    vm: PinAuthViewModel = viewModel()
) {
    // Reset synchronously so state is Entering before any LaunchedEffect fires
    val resetKey = remember(source) { vm.reset(); source.username }
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(resetKey, state) {
        if (state is PinAuthState.Success) onSuccess()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF090910)), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Profile avatar
            val avatarColors = listOf(
                Color(0xFF7C4DFF), Color(0xFF00BCD4), Color(0xFF4CAF50),
                Color(0xFFFF9800), Color(0xFFE91E63), Color(0xFF607D8B),
                Color(0xFF9C27B0), Color(0xFF03A9F4)
            )
            val avatarColor = avatarColors[(source.username.hashCode() and 0x7FFFFFFF) % avatarColors.size]

            Box(
                modifier = Modifier.size(80.dp).clip(CircleShape).background(avatarColor),
                contentAlignment = Alignment.Center
            ) {
                Text(source.username.take(1).uppercase(), fontSize = 32.sp, color = Color.White)
            }

            Spacer(Modifier.height(16.dp))
            Text(source.username, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("Enter PIN", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            Spacer(Modifier.height(32.dp))

            when (val s = state) {
                is PinAuthState.Entering, is PinAuthState.Authenticating -> {
                    val pin = (s as? PinAuthState.Entering)?.pin ?: ""
                    val error = (s as? PinAuthState.Entering)?.error
                    val busy = s is PinAuthState.Authenticating

                    // PIN dots
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        repeat(6) { i ->
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(if (i < pin.length) Accent else BgCard)
                                    .border(1.dp, if (i < pin.length) Accent else TextMuted, CircleShape)
                            )
                        }
                    }

                    if (error != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(error, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium)
                    }

                    Spacer(Modifier.height(28.dp))

                    if (busy) {
                        CircularProgressIndicator(color = Accent, modifier = Modifier.size(36.dp))
                    } else {
                        PinPad(
                            onDigit = { vm.appendDigit(it) },
                            onDelete = { vm.deleteDigit() },
                            onConfirm = { vm.confirm(source.host, source.port, source.username) },
                            confirmEnabled = pin.isNotEmpty()
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                    var backFocused by remember { mutableStateOf(false) }
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier.onFocusChanged { backFocused = it.isFocused }
                    ) {
                        Text("Back", color = if (backFocused) TextPrimary else TextMuted)
                    }
                }
                is PinAuthState.Success -> {}
            }
        }
    }
}

@Composable
private fun PinPad(
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫")
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
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
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            var focused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (focused) Accent else if (confirmEnabled) AccentDim else BgCard)
                    .then(if (focused) Modifier.border(Dimens.focusBorder, Color.White, RoundedCornerShape(8.dp)) else Modifier)
                    .onFocusChanged { focused = it.isFocused }
                    .clickable(enabled = confirmEnabled) { onConfirm() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Confirm", style = MaterialTheme.typography.titleMedium,
                    color = if (confirmEnabled) Color.White else TextMuted,
                    textAlign = TextAlign.Center)
            }
        }
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
