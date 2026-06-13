package com.aihomecloud.ahcplayer.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aihomecloud.ahcplayer.data.ahc.AhcRepository
import com.aihomecloud.ahcplayer.data.ahc.AhcUserProfile
import com.aihomecloud.ahcplayer.data.db.AppDatabase
import com.aihomecloud.ahcplayer.data.db.SourceEntity
import com.aihomecloud.ahcplayer.data.model.MediaSource
import com.aihomecloud.ahcplayer.data.model.SourceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ProfileScreenState {
    object Loading : ProfileScreenState()
    data class ShowProfiles(val profiles: List<AhcUserProfile>) : ProfileScreenState()
    data class PinEntry(
        val profile: AhcUserProfile,
        val pin: String = "",
        val error: String? = null
    ) : ProfileScreenState()
    object Saving : ProfileScreenState()
    data class Errored(val message: String) : ProfileScreenState()
}

class ProfileSelectViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow<ProfileScreenState>(ProfileScreenState.Loading)
    val state: StateFlow<ProfileScreenState> = _state.asStateFlow()

    private val repo = AhcRepository(app)
    private val db = AppDatabase.get(app)

    private var currentHost = ""
    private var currentPort = 8443
    private var loadedProfiles: List<AhcUserProfile> = emptyList()

    fun loadProfiles(host: String, port: Int) {
        currentHost = host
        currentPort = port
        viewModelScope.launch {
            _state.value = ProfileScreenState.Loading
            try {
                val profiles = repo.getProfiles(host, port)
                loadedProfiles = profiles
                _state.value = if (profiles.isEmpty())
                    ProfileScreenState.Errored("No profiles found on this device")
                else
                    ProfileScreenState.ShowProfiles(profiles)
            } catch (e: Exception) {
                _state.value = ProfileScreenState.Errored(e.message ?: "Failed to load profiles")
            }
        }
    }

    fun selectProfile(profile: AhcUserProfile, onSourceAdded: (MediaSource) -> Unit) {
        if (profile.hasPin) {
            _state.value = ProfileScreenState.PinEntry(profile)
        } else {
            loginAndSave(profile, "", onSourceAdded)
        }
    }

    fun appendPin(digit: String) {
        val s = _state.value as? ProfileScreenState.PinEntry ?: return
        if (s.pin.length < 6) _state.value = s.copy(pin = s.pin + digit, error = null)
    }

    fun deletePin() {
        val s = _state.value as? ProfileScreenState.PinEntry ?: return
        if (s.pin.isNotEmpty()) _state.value = s.copy(pin = s.pin.dropLast(1))
    }

    fun confirmPin(onSourceAdded: (MediaSource) -> Unit) {
        val s = _state.value as? ProfileScreenState.PinEntry ?: return
        if (s.pin.isEmpty()) return
        loginAndSave(s.profile, s.pin, onSourceAdded)
    }

    private fun loginAndSave(profile: AhcUserProfile, pin: String, onSourceAdded: (MediaSource) -> Unit) {
        val allProfiles = loadedProfiles.ifEmpty { listOf(profile) }
        _state.value = ProfileScreenState.Saving
        viewModelScope.launch {
            try {
                // Authenticate the selected profile
                repo.loginWithProfile(currentHost, currentPort, profile.name, pin)

                // Save ALL profiles from this device (skip if already exists)
                allProfiles.forEach { p ->
                    val existing = db.sourceDao().getByHostAndUsername(currentHost, p.name)
                    if (existing == null) {
                        db.sourceDao().insert(SourceEntity(
                            name = "${p.name}'s AHC",
                            host = currentHost,
                            share = "media",
                            port = currentPort,
                            sourceType = "AHC",
                            username = p.name,
                            hasPin = p.hasPin
                        ))
                    }
                }

                // Return the selected profile's source
                val entity = db.sourceDao().getByHostAndUsername(currentHost, profile.name)!!
                onSourceAdded(MediaSource(
                    id = entity.id,
                    name = entity.name,
                    host = currentHost,
                    share = "media",
                    port = currentPort,
                    sourceType = SourceType.AHC,
                    username = profile.name,
                    hasPin = profile.hasPin
                ))
            } catch (e: Exception) {
                if (pin.isNotEmpty()) {
                    _state.value = ProfileScreenState.PinEntry(
                        profile = profile,
                        pin = pin,
                        error = "Wrong PIN"
                    )
                } else {
                    _state.value = ProfileScreenState.Errored(e.message ?: "Login failed")
                }
            }
        }
    }

    fun retryProfiles() {
        loadProfiles(currentHost, currentPort)
    }
}
