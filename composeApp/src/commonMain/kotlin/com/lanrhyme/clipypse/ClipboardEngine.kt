package com.lanrhyme.clipypse

import kotlinx.coroutines.flow.Flow

expect class ClipboardEngine() {
    val syncState: Flow<SyncState>
    val lastError: Flow<String?>
    val clipboardHistory: Flow<List<ClipboardItem>>
    val isConnected: Flow<Boolean>
    val remoteDevice: Flow<DeviceInfoMessage?>

    suspend fun start(
        ip: String,
        port: Int,
        mode: ConnectionMode,
        isServer: Boolean
    )

    fun stop()

    suspend fun sendClipboardItem(item: ClipboardItem)

    suspend fun clearHistory()

    fun setMaxHistorySize(size: Int)

    fun setAutoSync(enabled: Boolean)
}

enum class SyncState {
    Idle,
    Connecting,
    Syncing,
    Error
}

enum class ConnectionMode {
    Wifi,
    Usb,
    Bluetooth
}

data class SyncConfig(
    val autoSync: Boolean = true,
    val maxHistorySize: Int = 50,
    val syncText: Boolean = true,
    val syncImage: Boolean = true,
    val syncFile: Boolean = true,
    val maxFileSize: Long = 10 * 1024 * 1024
)

interface ClipboardListener {
    fun onClipboardChanged(item: ClipboardItem)
    fun onError(error: String)
}

interface ClipboardProvider {
    fun getClipboardItem(): ClipboardItem?
    fun setClipboardItem(item: ClipboardItem): Boolean
    fun hasClipboardContent(): Boolean
}
