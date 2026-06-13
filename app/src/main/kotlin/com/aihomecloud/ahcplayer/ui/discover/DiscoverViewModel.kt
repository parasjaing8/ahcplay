package com.aihomecloud.ahcplayer.ui.discover

import android.app.Application
import android.net.wifi.WifiManager
import android.text.format.Formatter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aihomecloud.ahcplayer.data.ahc.AhcDeviceInfo
import com.aihomecloud.ahcplayer.data.ahc.AhcRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class DiscoverViewModel(app: Application) : AndroidViewModel(app) {

    private val _devices = MutableStateFlow<List<AhcDeviceInfo>>(emptyList())
    val devices: StateFlow<List<AhcDeviceInfo>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _subnet = MutableStateFlow("")
    val subnet: StateFlow<String> = _subnet.asStateFlow()

    private val repo = AhcRepository(app)
    private var scanJob: Job? = null

    fun startScan() {
        if (_isScanning.value) return
        _devices.value = emptyList()

        val wifiManager = getApplication<Application>().applicationContext
            .getSystemService(android.content.Context.WIFI_SERVICE) as? WifiManager
        val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
        if (ipInt == 0) return

        @Suppress("DEPRECATION")
        val myIp = Formatter.formatIpAddress(ipInt)
        val subnet = myIp.substringBeforeLast(".")
        _subnet.value = subnet
        _isScanning.value = true

        scanJob = viewModelScope.launch {
            coroutineScope {
                val sem = Semaphore(30)
                (1..254).map { i ->
                    async {
                        sem.withPermit {
                            val device = repo.probeHost("$subnet.$i")
                            if (device != null) _devices.update { it + device }
                        }
                    }
                }.awaitAll()
            }
            _isScanning.value = false
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        _isScanning.value = false
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
    }
}
