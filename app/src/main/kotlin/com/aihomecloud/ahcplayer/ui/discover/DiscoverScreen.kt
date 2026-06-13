package com.aihomecloud.ahcplayer.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aihomecloud.ahcplayer.data.ahc.AhcDeviceInfo
import com.aihomecloud.ahcplayer.ui.setup.AhcButton
import com.aihomecloud.ahcplayer.ui.setup.AhcTextField
import com.aihomecloud.ahcplayer.ui.theme.*

@Composable
fun DiscoverScreen(
    onDeviceSelected: (host: String, port: Int, deviceName: String) -> Unit,
    onAddSmb: () -> Unit,
    onBack: () -> Unit,
    vm: DiscoverViewModel = viewModel()
) {
    val devices by vm.devices.collectAsStateWithLifecycle()
    val isScanning by vm.isScanning.collectAsStateWithLifecycle()
    val subnet by vm.subnet.collectAsStateWithLifecycle()
    var showManual by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.startScan() }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .padding(horizontal = Dimens.marginH, vertical = Dimens.marginV),
        horizontalArrangement = Arrangement.spacedBy(48.dp)
    ) {
        // Left column: device list
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "AiHomeCloud Devices",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Accent,
                        strokeWidth = 2.dp
                    )
                }
            }
            Text(
                when {
                    isScanning && subnet.isNotEmpty() -> "Scanning $subnet.1–254…"
                    isScanning -> "Starting scan…"
                    devices.isEmpty() -> "No devices found"
                    else -> "Found ${devices.size} device${if (devices.size != 1) "s" else ""}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(devices) { device ->
                    DeviceCard(device = device, onClick = {
                        onDeviceSelected(device.host, device.port, device.displayName)
                    })
                }

                // Manual entry item at bottom
                item {
                    ManualEntryRow(
                        expanded = showManual,
                        onExpand = { showManual = true },
                        onConnect = { host, port -> onDeviceSelected(host, port, "AiHomeCloud @ $host") }
                    )
                }
            }
        }

        // Right column: instructions + SMB button
        Column(
            modifier = Modifier.width(280.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Add Source", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                "Select an AiHomeCloud device found on your network, or enter its IP manually.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(Modifier.height(16.dp))
            Divider(color = TextMuted.copy(alpha = 0.2f))
            Spacer(Modifier.height(8.dp))
            Text(
                "SMB Share",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Text(
                "Connect directly to a Samba/Windows network share.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            AhcButton(text = "Add SMB Source", onClick = onAddSmb)
            Spacer(Modifier.weight(1f))
            var backFocused by remember { mutableStateOf(false) }
            TextButton(
                onClick = onBack,
                modifier = Modifier.onFocusChanged { backFocused = it.isFocused }
            ) {
                Text("Back", color = if (backFocused) TextPrimary else TextMuted)
            }
        }
    }
}

@Composable
private fun DeviceCard(device: AhcDeviceInfo, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) BgCardFocused else BgCard)
            .then(if (focused) Modifier.border(Dimens.focusBorder, Accent, RoundedCornerShape(8.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("💻", fontSize = 28.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text(device.displayName, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Text(device.host, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        if (focused) {
            Text("Select", style = MaterialTheme.typography.labelMedium, color = Accent)
        }
    }
}

@Composable
private fun ManualEntryRow(
    expanded: Boolean,
    onExpand: () -> Unit,
    onConnect: (host: String, port: Int) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8443") }
    val connectFocus = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(BgCard)
            .then(if (focused && !expanded) Modifier.border(Dimens.focusBorder, Accent, RoundedCornerShape(8.dp)) else Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (!expanded) Modifier
                    .onFocusChanged { focused = it.isFocused }
                    .clickable { onExpand() }
                else Modifier)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("➕", fontSize = 20.sp)
            Spacer(Modifier.width(12.dp))
            Text("Enter IP manually", style = MaterialTheme.typography.titleLarge,
                color = if (focused || expanded) TextPrimary else TextSecondary)
        }

        if (expanded) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AhcTextField(value = host, onValueChange = { host = it }, label = "Host / IP",
                    imeAction = ImeAction.Next)
                AhcTextField(value = port, onValueChange = { port = it }, label = "Port",
                    imeAction = ImeAction.Done)
                AhcButton(
                    text = "Connect",
                    modifier = Modifier.focusRequester(connectFocus),
                    onClick = {
                        val p = port.toIntOrNull() ?: 8443
                        if (host.isNotBlank()) onConnect(host.trim(), p)
                    }
                )
            }
        }
    }
}
