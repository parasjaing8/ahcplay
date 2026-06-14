package com.aihomecloud.ahcplayer.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aihomecloud.ahcplayer.data.model.MediaSource
import com.aihomecloud.ahcplayer.data.model.SourceType
import com.aihomecloud.ahcplayer.ui.theme.*

@Composable
fun SetupScreen(
    onSourceSelected: (MediaSource) -> Unit,
    vm: SetupViewModel = viewModel()
) {
    val sources by vm.sources.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("NAS") }
    var host by remember { mutableStateOf("") }
    var share by remember { mutableStateOf("media") }
    val connectFocus = remember { FocusRequester() }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .padding(horizontal = Dimens.marginH, vertical = Dimens.marginV)
    ) {
        // Left: form
        Column(
            modifier = Modifier
                .width(420.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Add SMB Source", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            AhcTextField(value = name, onValueChange = { name = it }, label = "Name",
                imeAction = ImeAction.Next)
            AhcTextField(value = host, onValueChange = { host = it }, label = "Host / IP",
                imeAction = ImeAction.Next)
            AhcTextField(value = share, onValueChange = { share = it }, label = "Share",
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { connectFocus.requestFocus() }))
            Spacer(Modifier.height(8.dp))
            AhcButton(
                text = "Connect",
                modifier = Modifier.focusRequester(connectFocus),
                onClick = {
                    if (host.isNotBlank() && share.isNotBlank()) {
                        val srcName = name.ifBlank { "$host/$share" }
                        vm.addSource(srcName, host, share)
                        onSourceSelected(MediaSource(name = srcName, host = host, share = share))
                    }
                }
            )
        }

        Spacer(Modifier.width(64.dp))

        // Right: saved sources
        Column(modifier = Modifier.weight(1f)) {
            Text("Saved Sources", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Spacer(Modifier.height(16.dp))
            if (sources.isEmpty()) {
                Text("No SMB sources yet", color = TextMuted)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sources) { src ->
                        SmbSourceCard(source = src, onClick = { onSourceSelected(src) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SmbSourceCard(source: MediaSource, onClick: () -> Unit) {
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(source.name, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Text("smb://${source.host}/${source.share}",
                    style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            Text("SMB", color = Accent, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun AhcTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    imeAction: ImeAction = ImeAction.Next,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    var editing by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = TextMuted) },
        singleLine = true,
        readOnly = !editing,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onDone = {
                editing = false
                keyboardController?.hide()
                keyboardActions.onDone?.invoke(this)
            },
            onNext = keyboardActions.onNext,
            onGo = keyboardActions.onGo,
            onSearch = keyboardActions.onSearch,
            onSend = keyboardActions.onSend,
            onPrevious = keyboardActions.onPrevious
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent,
            unfocusedBorderColor = TextMuted,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextSecondary,
            cursorColor = Accent,
            focusedContainerColor = BgCard,
            unfocusedContainerColor = BgCard
        ),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { if (!it.isFocused) editing = false }
            .onKeyEvent { event ->
                if (!editing && event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                ) {
                    editing = true
                    keyboardController?.show()
                    true
                } else {
                    false
                }
            }
    )
}

@Composable
fun AhcButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var focused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = if (focused) AccentDim else Accent),
        modifier = modifier.onFocusChanged { focused = it.isFocused }
    ) {
        Text(text, color = Color.White, style = MaterialTheme.typography.titleLarge)
    }
}
