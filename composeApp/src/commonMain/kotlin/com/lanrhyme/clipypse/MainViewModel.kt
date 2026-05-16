package com.lanrhyme.clipypse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BluetoothDeviceInfo(
    val name: String,
    val address: String,
    val bonded: Boolean = false
)

data class AppUiState(
    val syncState: SyncState = SyncState.Idle,
    val mode: ConnectionMode = ConnectionMode.Wifi,
    val isServer: Boolean = true,
    val ipAddress: String = "",
    val port: String = Constants.DEFAULT_TCP_PORT.toString(),
    val isConnected: Boolean = false,
    val remoteDevice: DeviceInfoMessage? = null,
    val clipboardHistory: List<ClipboardItem> = emptyList(),
    val autoSync: Boolean = true,
    val maxHistorySize: Int = Constants.MAX_HISTORY_SIZE,
    val errorMessage: String? = null,
    val lastSyncTime: Long = 0,
    val bluetoothDevices: List<BluetoothDeviceInfo> = emptyList(),
    val isBluetoothDiscovering: Boolean = false
)

class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val clipboardEngine = ClipboardEngine()

    init {
        viewModelScope.launch {
            clipboardEngine.syncState.collect { state ->
                _uiState.update { it.copy(syncState = state) }
            }
        }

        viewModelScope.launch {
            clipboardEngine.isConnected.collect { connected ->
                _uiState.update { it.copy(isConnected = connected) }
            }
        }

        viewModelScope.launch {
            clipboardEngine.remoteDevice.collect { device ->
                _uiState.update { it.copy(remoteDevice = device) }
            }
        }

        viewModelScope.launch {
            clipboardEngine.clipboardHistory.collect { history ->
                _uiState.update { it.copy(clipboardHistory = history) }
            }
        }

        viewModelScope.launch {
            clipboardEngine.lastError.collect { error ->
                _uiState.update { it.copy(errorMessage = error) }
            }
        }

        val platform = getPlatform()
        _uiState.update { 
            it.copy(
                ipAddress = platform.ipAddress,
                port = Constants.DEFAULT_TCP_PORT.toString()
            )
        }
    }

    fun setMode(mode: ConnectionMode) {
        _uiState.update { it.copy(mode = mode) }
    }

    fun setServerMode(isServer: Boolean) {
        _uiState.update { it.copy(isServer = isServer) }
    }

    fun setIpAddress(ip: String) {
        _uiState.update { it.copy(ipAddress = ip) }
    }

    fun setPort(port: String) {
        _uiState.update { it.copy(port = port) }
    }

    fun setAutoSync(enabled: Boolean) {
        _uiState.update { it.copy(autoSync = enabled) }
        clipboardEngine.setAutoSync(enabled)
    }

    fun startSync() {
        viewModelScope.launch {
            val state = _uiState.value
            val port = state.port.toIntOrNull() ?: Constants.DEFAULT_TCP_PORT

            clipboardEngine.start(
                ip = state.ipAddress,
                port = port,
                mode = state.mode,
                isServer = state.isServer
            )
        }
    }

    fun stopSync() {
        clipboardEngine.stop()
    }

    fun sendClipboardItem(item: ClipboardItem) {
        viewModelScope.launch {
            clipboardEngine.sendClipboardItem(item)
            _uiState.update { it.copy(lastSyncTime = System.currentTimeMillis()) }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            clipboardEngine.clearHistory()
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun startBluetoothDiscovery() {
        startBluetoothDiscoveryPlatform()
    }

    fun stopBluetoothDiscovery() {
        stopBluetoothDiscoveryPlatform()
    }

    fun selectBluetoothDevice(device: BluetoothDeviceInfo) {
        _uiState.update { it.copy(ipAddress = device.address) }
    }

    fun updateBluetoothDevices(devices: List<BluetoothDeviceInfo>) {
        _uiState.update { it.copy(bluetoothDevices = devices) }
    }

    fun updateBluetoothDiscovering(isDiscovering: Boolean) {
        _uiState.update { it.copy(isBluetoothDiscovering = isDiscovering) }
    }

    fun attachBluetoothManager(manager: Any) {
        attachBluetoothManagerPlatform(manager)
    }

    private fun startBluetoothDiscoveryPlatform() {
        startBluetoothDiscoveryImpl()
    }

    private fun stopBluetoothDiscoveryPlatform() {
        stopBluetoothDiscoveryImpl()
    }

    private fun attachBluetoothManagerPlatform(manager: Any) {
        attachBluetoothManagerImpl(manager)
    }

    fun attachBluetoothManagerInternal(manager: Any) {
        attachBluetoothManagerPlatform(manager)
    }

    override fun onCleared() {
        super.onCleared()
        clipboardEngine.stop()
    }
}

internal expect fun MainViewModel.startBluetoothDiscoveryImpl()
internal expect fun MainViewModel.stopBluetoothDiscoveryImpl()
internal expect fun MainViewModel.attachBluetoothManagerImpl(manager: Any)
