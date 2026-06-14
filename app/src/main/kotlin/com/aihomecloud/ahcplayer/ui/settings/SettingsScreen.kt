package com.aihomecloud.ahcplayer.ui.settings

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.aihomecloud.ahcplayer.data.prefs.AppPreferences
import com.aihomecloud.ahcplayer.data.prefs.SecurePrefs
import com.aihomecloud.ahcplayer.data.scan.LibraryScanner
import com.aihomecloud.ahcplayer.data.source.StorageHelper
import com.aihomecloud.ahcplayer.data.tmdb.TmdbClient
import com.aihomecloud.ahcplayer.ui.setup.AhcButton
import com.aihomecloud.ahcplayer.ui.setup.AhcTextField
import com.aihomecloud.ahcplayer.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

private enum class SettingsCategory(val label: String) {
    SOURCES("Sources"),
    DATA("Data"),
    TMDB("TMDB"),
    ABOUT("About")
}

enum class TmdbVerifyState { IDLE, CHECKING, VALID, INVALID }
enum class RescanState { IDLE, RUNNING, DONE }

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val ahcRepo = AhcRepository(app)
    private val prefs = AppPreferences(app)

    private fun mapSource(it: SourceEntity) = MediaSource(it.id, it.name, it.host, it.share, it.port,
        sourceType = when (it.sourceType) {
            "AHC" -> SourceType.AHC
            "USB" -> SourceType.USB
            "INTERNAL" -> SourceType.INTERNAL
            else -> SourceType.SMB
        },
        username = it.username, hasPin = it.hasPin, enabled = it.enabled)

    val sources = db.sourceDao().getAll()
        .map { list -> list.map(::mapSource) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var tmdbApiKey by mutableStateOf(prefs.getTmdbApiKey().orEmpty())
        private set

    var tmdbVerifyState by mutableStateOf(TmdbVerifyState.IDLE)
        private set

    var rescanState by mutableStateOf(RescanState.IDLE)
        private set

    var rescanCount by mutableStateOf(0)
        private set

    var usbVolumes by mutableStateOf<List<File>>(emptyList())
        private set

    fun updateTmdbApiKey(key: String) {
        tmdbApiKey = key
        tmdbVerifyState = TmdbVerifyState.IDLE
    }

    fun saveTmdbApiKey() {
        prefs.setTmdbApiKey(tmdbApiKey)
        verifyTmdbKey()
    }

    fun clearTmdbApiKey() {
        prefs.setTmdbApiKey(null)
        tmdbApiKey = ""
        tmdbVerifyState = TmdbVerifyState.IDLE
    }

    private fun verifyTmdbKey() {
        if (tmdbApiKey.isBlank()) {
            tmdbVerifyState = TmdbVerifyState.INVALID
            return
        }
        tmdbVerifyState = TmdbVerifyState.CHECKING
        viewModelScope.launch {
            tmdbVerifyState = try {
                if (TmdbClient.service.verifyKey(tmdbApiKey).success) TmdbVerifyState.VALID
                else TmdbVerifyState.INVALID
            } catch (e: Exception) {
                TmdbVerifyState.INVALID
            }
        }
    }

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

    fun setSourceEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            db.sourceDao().setEnabled(id, enabled)
        }
    }

    fun loadUsbVolumes() {
        viewModelScope.launch {
            usbVolumes = StorageHelper.getUsbVolumes()
        }
    }

    fun addUsbSource(path: String, label: String) {
        viewModelScope.launch {
            db.sourceDao().insert(SourceEntity(name = label, host = path, share = "", sourceType = "USB"))
        }
    }

    fun clearWatchHistory() {
        viewModelScope.launch {
            db.watchHistoryDao().deleteAll()
        }
    }

    fun rescanMetadata() {
        if (rescanState == RescanState.RUNNING) return
        viewModelScope.launch {
            rescanState = RescanState.RUNNING
            rescanCount = 0
            val enabledSources = db.sourceDao().getAll().first()
                .filter { it.enabled && it.sourceType != "INTERNAL" }
                .map(::mapSource)
            LibraryScanner.scan(getApplication(), enabledSources, forceRefresh = true) { count ->
                rescanCount = count
            }
            rescanState = RescanState.DONE
            delay(2000)
            rescanState = RescanState.IDLE
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
    var selectedCategory by remember { mutableStateOf(SettingsCategory.SOURCES) }

    Box(modifier = Modifier.fillMaxSize()) {
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
                // Sidebar
                Column(
                    modifier = Modifier.width(240.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SettingsCategory.entries.forEach { category ->
                        CategoryRow(
                            label = category.label,
                            selected = category == selectedCategory,
                            onFocus = { selectedCategory = category }
                        )
                    }
                }

                // Detail pane
                Column(modifier = Modifier.weight(1f)) {
                    when (selectedCategory) {
                        SettingsCategory.SOURCES -> SourcesPane(vm = vm, sources = sources)
                        SettingsCategory.DATA -> DataPane(
                            onClearHistory = { showClearHistoryConfirm = true }
                        )
                        SettingsCategory.TMDB -> TmdbPane(vm = vm)
                        SettingsCategory.ABOUT -> AboutPane()
                    }
                }
            }
        }

        RescanIndicator(
            state = vm.rescanState,
            count = vm.rescanCount,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp)
        )
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
private fun CategoryRow(label: String, selected: Boolean, onFocus: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) BgCardFocused else Color.Transparent)
            .then(if (focused) Modifier.border(Dimens.focusBorder, Accent, RoundedCornerShape(8.dp)) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clickable { onFocus() }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleLarge,
            color = if (selected) TextPrimary else TextSecondary
        )
    }
}

@Composable
private fun PaneHeader(title: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun RescanIndicator(state: RescanState, count: Int, modifier: Modifier = Modifier) {
    when (state) {
        RescanState.RUNNING -> Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Scanned $count", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = Accent,
                strokeWidth = 3.dp
            )
        }
        RescanState.DONE -> Box(
            modifier = modifier.size(32.dp).clip(CircleShape).background(Success),
            contentAlignment = Alignment.Center
        ) {
            Text("✓", color = Color.White, style = MaterialTheme.typography.titleLarge)
        }
        RescanState.IDLE -> {}
    }
}

@Composable
private fun SourcesPane(vm: SettingsViewModel, sources: List<MediaSource>) {
    var showUsbPicker by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        PaneHeader("Sources")
        if (sources.isEmpty()) {
            Text("No sources configured.", color = TextMuted)
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sources) { src ->
                    SourceRow(
                        source = src,
                        onDelete = { vm.deleteSource(src) },
                        onToggleEnabled = { vm.setSourceEnabled(src.id, !src.enabled) }
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        SettingsRow(
            title = "Add USB Storage",
            subtitle = "Browse a connected USB drive",
            onClick = {
                vm.loadUsbVolumes()
                showUsbPicker = true
            }
        )
    }

    if (showUsbPicker) {
        UsbVolumePickerDialog(
            volumes = vm.usbVolumes,
            onSelect = { vol ->
                vm.addUsbSource(vol.absolutePath, vol.name)
                showUsbPicker = false
            },
            onDismiss = { showUsbPicker = false }
        )
    }
}

@Composable
private fun DataPane(onClearHistory: () -> Unit) {
    PaneHeader("Data")
    SettingsRow(
        title = "Clear Watch History",
        subtitle = "Remove all resume points",
        onClick = onClearHistory
    )
}

@Composable
private fun TmdbPane(vm: SettingsViewModel) {
    PaneHeader("TMDB")
    Text(
        "Used to fetch posters, backdrops, and metadata for your library.",
        color = TextSecondary, style = MaterialTheme.typography.bodyMedium
    )
    Spacer(Modifier.height(16.dp))

    AhcTextField(
        value = vm.tmdbApiKey,
        onValueChange = vm::updateTmdbApiKey,
        label = "TMDB API Key"
    )
    Spacer(Modifier.height(8.dp))

    val statusText = if (vm.tmdbApiKey.isNotBlank()) "Using custom key"
        else "No key configured — posters and metadata disabled. Get a free key at themoviedb.org."
    Text(statusText, color = TextMuted, style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(16.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        AhcButton(
            text = "Save",
            onClick = vm::saveTmdbApiKey
        )
        AhcButton(
            text = "Clear",
            onClick = { vm.clearTmdbApiKey() }
        )
        TmdbVerifyBadge(vm.tmdbVerifyState)
    }

    Spacer(Modifier.height(24.dp))
    SettingsRow(
        title = "Rescan",
        subtitle = "Re-fetch posters, backdrops, and metadata",
        onClick = vm::rescanMetadata
    )
}

@Composable
private fun TmdbVerifyBadge(state: TmdbVerifyState) {
    when (state) {
        TmdbVerifyState.CHECKING -> CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = Accent,
            strokeWidth = 2.dp
        )
        TmdbVerifyState.VALID -> Box(
            modifier = Modifier.size(24.dp).clip(CircleShape).background(Success),
            contentAlignment = Alignment.Center
        ) {
            Text("✓", color = Color.White, style = MaterialTheme.typography.bodyMedium)
        }
        TmdbVerifyState.INVALID -> Box(
            modifier = Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error),
            contentAlignment = Alignment.Center
        ) {
            Text("✕", color = Color.White, style = MaterialTheme.typography.bodyMedium)
        }
        TmdbVerifyState.IDLE -> {}
    }
}

@Composable
private fun AboutPane() {
    PaneHeader("About")
    Text("AHC Player v1.0", color = TextSecondary, style = MaterialTheme.typography.bodyLarge)
    Text("Powered by libVLC 3.6.3", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
    Text("TMDB for metadata", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
    if (!SecurePrefs.isEncrypted) {
        Spacer(Modifier.height(16.dp))
        Text(
            "Warning: secure storage is unavailable on this device. Login tokens and your TMDB " +
                "key are stored unencrypted.",
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun SourceRow(source: MediaSource, onDelete: () -> Unit, onToggleEnabled: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(BgCard)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EnabledToggle(enabled = source.enabled, onToggle = onToggleEnabled)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(source.name, style = MaterialTheme.typography.titleLarge,
                color = if (source.enabled) TextPrimary else TextMuted)
            Text(
                when (source.sourceType) {
                    SourceType.AHC -> "${source.host}:${source.port} (AiHomeCloud)"
                    SourceType.SMB -> "smb://${source.host}/${source.share}"
                    SourceType.USB -> "USB: ${source.host}"
                    SourceType.INTERNAL -> "Internal Storage"
                },
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
private fun EnabledToggle(enabled: Boolean, onToggle: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (enabled) Success else BgCardFocused)
            .then(if (focused) Modifier.border(Dimens.focusBorder, Accent, RoundedCornerShape(4.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onToggle() },
        contentAlignment = Alignment.Center
    ) {
        if (enabled) Text("✓", color = Color.White, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun UsbVolumePickerDialog(
    volumes: List<File>,
    onSelect: (File) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select USB Drive", color = TextPrimary) },
        text = {
            if (volumes.isEmpty()) {
                Text("No USB drives detected.", color = TextSecondary)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    volumes.forEach { vol ->
                        var rowFocused by remember { mutableStateOf(false) }
                        Text(
                            vol.name,
                            color = if (rowFocused) Accent else TextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { rowFocused = it.isFocused }
                                .clickable { onSelect(vol) }
                                .padding(8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Accent) }
        },
        containerColor = BgCard
    )
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
