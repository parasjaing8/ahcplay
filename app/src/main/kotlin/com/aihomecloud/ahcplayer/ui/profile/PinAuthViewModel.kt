package com.aihomecloud.ahcplayer.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aihomecloud.ahcplayer.data.ahc.AhcRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class PinAuthState {
    data class Entering(val pin: String = "", val error: String? = null) : PinAuthState()
    object Authenticating : PinAuthState()
    object Success : PinAuthState()
}

class PinAuthViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AhcRepository(app)

    private val _state = MutableStateFlow<PinAuthState>(PinAuthState.Entering())
    val state: StateFlow<PinAuthState> = _state.asStateFlow()

    fun appendDigit(d: String) {
        val s = _state.value as? PinAuthState.Entering ?: return
        if (s.pin.length < 6) _state.value = s.copy(pin = s.pin + d, error = null)
    }

    fun deleteDigit() {
        val s = _state.value as? PinAuthState.Entering ?: return
        if (s.pin.isNotEmpty()) _state.value = s.copy(pin = s.pin.dropLast(1))
    }

    fun confirm(host: String, port: Int, username: String) {
        val s = _state.value as? PinAuthState.Entering ?: return
        if (s.pin.isEmpty()) return
        _state.value = PinAuthState.Authenticating
        viewModelScope.launch {
            try {
                repo.loginWithProfile(host, port, username, s.pin)
                _state.value = PinAuthState.Success
            } catch (e: Exception) {
                _state.value = PinAuthState.Entering(pin = s.pin, error = "Wrong PIN")
            }
        }
    }

    fun reset() {
        _state.value = PinAuthState.Entering()
    }
}
