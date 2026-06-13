package com.aihomecloud.ahcplayer.ui.settings

import android.app.Application
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aihomecloud.ahcplayer.data.ahc.AhcRepository
import com.aihomecloud.ahcplayer.data.db.AppDatabase
import com.aihomecloud.ahcplayer.data.db.SourceEntity
import com.aihomecloud.ahcplayer.data.model.MediaSource
import com.aihomecloud.ahcplayer.data.model.SourceType
import com.aihomecloud.ahcplayer.ui.theme.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val ahcRepo = AhcRepository(app)

    val sources = db.sourceDao().getAll()
        .map { list ->
            list.map {
                MediaSource(it.id, it.name, it.host, it.share, it.port,
                    if (it.sourceType == "AHC") SourceType.AHC else SourceType.SMB,
                    username = it.username, hasPin = it.hasPin)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteSource(source: MediaSource) {
        viewModelScope.launch {
            db.sourceDao().delete(SourceEntity(id = source.id, name = source.name, host = source.host,
                share = source.share, port = source.port, sourceType = source.sourceType.name,
                username = source.username))
            if (source.sourceType == SourceType.AHC) {
                ahcRepo.clearToken(source.host, source.username)
            }
        }
    }

    fun clearWatchHistory() {
        viewModelScope.launch {
            db.watchHistoryDao().deleteAll()
        }
    }

    fun clearMetadataCache() {
        viewModelScope.launch {
            db.mediaMetadataDao().deleteAll()
        }
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel()
) {
    val sources by vm.sources.collectAsStateWithLifecycle()
    var showClearHistoryConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .padding(horizontal = Dimens.marginH, vertical = Dimens.marginV)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", style = MaterialTheme.typography.headlineLarge, color = TextPrimary,
                modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("Done", color = Accent) }
        }

        Spacer(Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(48.dp)
        ) {
            // Sources management
            Column(modifier = Modifier.weight(1f)) {
                Text("Sources", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                Spacer(Modifier.height(16.dp))
                if (sources.isEmpty()) {
                    Text("No sources configured.", color = TextMuted)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(sources) { src ->
                            SourceRow(
                                source = src,
                                onDelete = { vm.deleteSource(src) }
                            )
                        }
                    }
                }
            }

            // Data management
            Column(modifier = Modifier.weight(1f)) {
                Text("Data", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                Spacer(Modifier.height(16.dp))

                SettingsRow(
                    title = "Clear Watch History",
                    subtitle = "Remove all resume points",
                    onClick = { showClearHistoryConfirm = true }
                )
                Spacer(Modifier.height(8.dp))
                SettingsRow(
                    title = "Clear Metadata Cache",
                    subtitle = "Re-fetch TMDB posters",
                    onClick = { vm.clearMetadataCache() }
                )
                Spacer(Modifier.height(32.dp))

                Text("About", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                Spacer(Modifier.height(16.dp))
                Text("AHC Player v1.0", color = TextSecondary, style = MaterialTheme.typography.bodyLarge)
                Text("Powered by libVLC 3.6.3", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                Text("TMDB for metadata", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    if (showClearHistoryConfirm) {
        AlertDialog(
            onDismissRequest = { showClearHistoryConfirm = false },
            title = { Text("Clear watch history?", color = TextPrimary) },
            text = { Text("All resume points will be lost.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearWatchHistory()
                    showClearHistoryConfirm = false
                }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryConfirm = false }) { Text("Cancel", color = Accent) }
            },
            containerColor = BgCard
        )
    }
}

@Composable
private fun SourceRow(source: MediaSource, onDelete: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(BgCard)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(source.name, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Text(
                if (source.sourceType == SourceType.AHC) "${source.host}:${source.port} (AiHomeCloud)"
                else "smb://${source.host}/${source.share}",
                style = MaterialTheme.typography.bodyMedium, color = TextSecondary
            )
        }
        var btnFocused by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(if (btnFocused) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else BgCardFocused)
                .onFocusChanged { btnFocused = it.isFocused }
                .clickable { onDelete() }
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text("Remove", color = if (btnFocused) Color.White else TextMuted,
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) BgCardFocused else BgCard)
            .then(if (focused) Modifier.border(Dimens.focusBorder, Accent, RoundedCornerShape(8.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
    }
}
