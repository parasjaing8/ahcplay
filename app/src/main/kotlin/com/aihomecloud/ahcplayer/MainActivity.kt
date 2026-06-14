package com.aihomecloud.ahcplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aihomecloud.ahcplayer.data.ahc.AhcRepository
import com.aihomecloud.ahcplayer.data.model.MediaSource
import com.aihomecloud.ahcplayer.data.model.SourceType
import com.aihomecloud.ahcplayer.data.model.WatchHistory
import com.aihomecloud.ahcplayer.player.PlayerActivity
import com.aihomecloud.ahcplayer.ui.browse.BrowseScreen
import com.aihomecloud.ahcplayer.ui.discover.DiscoverScreen
import com.aihomecloud.ahcplayer.ui.home.HomeScreen
import com.aihomecloud.ahcplayer.ui.profile.PinAuthScreen
import com.aihomecloud.ahcplayer.ui.profile.ProfileSelectScreen
import com.aihomecloud.ahcplayer.ui.settings.SettingsScreen
import com.aihomecloud.ahcplayer.ui.setup.SetupScreen
import com.aihomecloud.ahcplayer.ui.theme.*

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        setContent {
            AhcPlayerTheme {
                AppNavHost(
                    onPlayVideo = { uri, title, sourceId ->
                        startActivity(
                            Intent(this, PlayerActivity::class.java)
                                .putExtra(PlayerActivity.EXTRA_URI, uri)
                                .putExtra(PlayerActivity.EXTRA_TITLE, title)
                                .putExtra(PlayerActivity.EXTRA_SOURCE_ID, sourceId)
                        )
                    },
                    onFinish = { finish() }
                )
            }
        }
    }
}

/** Runtime permission needed to read video files from internal/USB storage. */
private val storagePermission: String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_VIDEO
    else Manifest.permission.READ_EXTERNAL_STORAGE

private fun hasStoragePermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, storagePermission) == PackageManager.PERMISSION_GRANTED

sealed class Screen {
    object Home : Screen()
    object Discover : Screen()
    object Setup : Screen()
    object Settings : Screen()
    data class ProfileSelect(val host: String, val port: Int, val deviceName: String) : Screen()
    data class PinAuth(val source: MediaSource) : Screen()
    data class Browse(val source: MediaSource) : Screen()
}

@Composable
fun AppNavHost(onPlayVideo: (uri: String, title: String, sourceId: Long) -> Unit, onFinish: () -> Unit) {
    val context = LocalContext.current
    val ahcRepo = remember { AhcRepository(context) }

    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var backStack by remember { mutableStateOf(listOf<Screen>()) }
    var showExitDialog by remember { mutableStateOf(false) }
    var pendingStorageSource by remember { mutableStateOf<MediaSource?>(null) }
    var showStoragePermissionDenied by remember { mutableStateOf(false) }

    fun navigate(to: Screen) {
        backStack = backStack + screen
        screen = to
    }

    fun goBack() {
        if (backStack.isNotEmpty()) {
            screen = backStack.last()
            backStack = backStack.dropLast(1)
        } else {
            showExitDialog = true
        }
    }

    // Routes AHC profile selection: if PIN required and no token → PinAuth; else → Browse
    fun navigateToSource(src: MediaSource) {
        if (src.sourceType == SourceType.AHC && src.hasPin && ahcRepo.getToken(src.host, src.username) == null) {
            navigate(Screen.PinAuth(src))
        } else {
            navigate(Screen.Browse(src))
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val src = pendingStorageSource
        pendingStorageSource = null
        if (src == null) return@rememberLauncherForActivityResult
        if (granted) navigateToSource(src) else showStoragePermissionDenied = true
    }

    // Internal/USB sources read files directly off device storage and need the
    // runtime storage permission; request it lazily on first use.
    fun openSource(src: MediaSource) {
        val needsStoragePermission = src.sourceType == SourceType.INTERNAL || src.sourceType == SourceType.USB
        if (needsStoragePermission && !hasStoragePermission(context)) {
            pendingStorageSource = src
            storagePermissionLauncher.launch(storagePermission)
        } else {
            navigateToSource(src)
        }
    }

    BackHandler(enabled = screen !is Screen.Home && screen !is Screen.Browse) { goBack() }
    BackHandler(enabled = screen is Screen.Home) { showExitDialog = true }
    BackHandler(enabled = showExitDialog) { showExitDialog = false }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val s = screen) {
            is Screen.Home -> HomeScreen(
                onBrowseSource = { src -> openSource(src) },
                onAddSource = { navigate(Screen.Discover) },
                onSettings = { navigate(Screen.Settings) },
                onResume = { history -> onPlayVideo(history.uri, history.title, history.sourceId) }
            )
            is Screen.Discover -> DiscoverScreen(
                onDeviceSelected = { host, port, name -> navigate(Screen.ProfileSelect(host, port, name)) },
                onAddSmb = { navigate(Screen.Setup) },
                onBack = { goBack() }
            )
            is Screen.ProfileSelect -> ProfileSelectScreen(
                host = s.host,
                port = s.port,
                deviceName = s.deviceName,
                onSourceAdded = { src ->
                    backStack = listOf(Screen.Home)
                    screen = Screen.Browse(src)
                },
                onBack = { goBack() }
            )
            is Screen.PinAuth -> PinAuthScreen(
                source = s.source,
                onSuccess = {
                    backStack = backStack.dropLastWhile { it is Screen.PinAuth }
                    screen = Screen.Browse(s.source)
                },
                onBack = { goBack() }
            )
            is Screen.Settings -> SettingsScreen(onBack = { goBack() })
            is Screen.Setup -> SetupScreen(
                onSourceSelected = { src ->
                    backStack = listOf(Screen.Home)
                    screen = Screen.Browse(src)
                }
            )
            is Screen.Browse -> BrowseScreen(
                rootUri = s.source.browseRootUri,
                sourceId = s.source.id,
                onPlayVideo = onPlayVideo,
                onBack = { goBack() }
            )
        }

        if (showExitDialog) {
            ExitDialog(onConfirm = { onFinish() }, onDismiss = { showExitDialog = false })
        }

        if (showStoragePermissionDenied) {
            StoragePermissionDeniedDialog(onDismiss = { showStoragePermissionDenied = false })
        }
    }
}

@Composable
private fun ExitDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val yesFocusRequester = remember { FocusRequester() }
    val noFocusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xCC000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(BgCard)
                .padding(horizontal = 56.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            Text("Exit AHC Player?", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                var yesFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .focusRequester(yesFocusRequester)
                        .focusProperties { right = noFocusRequester }
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (yesFocused) Accent else AccentDim)
                        .then(if (yesFocused) Modifier.border(Dimens.focusBorder, Color.White, RoundedCornerShape(8.dp)) else Modifier)
                        .onFocusChanged { yesFocused = it.isFocused }
                        .clickable { onConfirm() }
                        .padding(horizontal = 40.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Yes", style = MaterialTheme.typography.titleMedium, color = Color.White)
                }
                var noFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .focusRequester(noFocusRequester)
                        .focusProperties { left = yesFocusRequester }
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (noFocused) BgCardFocused else BgPrimary)
                        .border(1.dp, if (noFocused) Accent else TextMuted, RoundedCornerShape(8.dp))
                        .onFocusChanged { noFocused = it.isFocused }
                        .clickable { onDismiss() }
                        .padding(horizontal = 40.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No", style = MaterialTheme.typography.titleMedium,
                        color = if (noFocused) TextPrimary else TextSecondary)
                }
            }
        }
    }
    LaunchedEffect(Unit) { yesFocusRequester.requestFocus() }
}

@Composable
private fun StoragePermissionDeniedDialog(onDismiss: () -> Unit) {
    val okFocusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xCC000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(BgCard)
                .padding(horizontal = 56.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            Text("Storage permission needed", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Text(
                "AHC Player needs storage access to browse this source. Grant the permission in " +
                    "Android Settings > Apps > AHC Player > Permissions.",
                style = MaterialTheme.typography.bodyLarge, color = TextSecondary
            )
            var okFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .focusRequester(okFocusRequester)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (okFocused) Accent else AccentDim)
                    .then(if (okFocused) Modifier.border(Dimens.focusBorder, Color.White, RoundedCornerShape(8.dp)) else Modifier)
                    .onFocusChanged { okFocused = it.isFocused }
                    .clickable { onDismiss() }
                    .padding(horizontal = 40.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("OK", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
        }
    }
    LaunchedEffect(Unit) { okFocusRequester.requestFocus() }
}
