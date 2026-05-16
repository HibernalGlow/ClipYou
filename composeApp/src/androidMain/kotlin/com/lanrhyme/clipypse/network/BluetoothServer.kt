package com.lanrhyme.clipypse.network

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.lanrhyme.clipypse.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.IOException
import java.util.*

class BluetoothServer(
    private val context: Context,
    private val onClipboardReceived: suspend (ClipboardPacketMessage) -> Unit,
    private val onDeviceConnected: (DeviceInfoMessage) -> Unit,
    private val onDeviceDisconnected: () -> Unit,
    private val onError: (String) -> Unit
) {
    private val _state = MutableStateFlow(SyncState.Idle)
    val state = _state.asStateFlow()

    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverJob: Job? = null
    private var serverSocket: BluetoothServerSocket? = null
    private var activeSocket: BluetoothSocket? = null
    private var activeHandler: BluetoothConnectionHandler? = null

    @OptIn(ExperimentalSerializationApi::class)
    private val proto = ProtoBuf {}

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
        const val SERVICE_NAME = "ClipYou"
    }

    suspend fun start() {
        serverJob?.takeIf { it.isActive }?.let {
            Logger.w("BluetoothServer", "Server already running")
            return
        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            onError("Bluetooth is not available or not enabled")
            return
        }

        _state.value = SyncState.Connecting

        serverJob = serverScope.launch {
            try {
                runServer()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e("BluetoothServer", "Server fatal error", e)
                _state.value = SyncState.Error
                onError(e.message ?: "Unknown error")
            } finally {
                cleanup()
                if (_state.value != SyncState.Error) {
                    _state.value = SyncState.Idle
                }
            }
        }
    }

    suspend fun stop() {
        serverJob?.cancel()
        withTimeoutOrNull(Constants.SERVER_STOP_TIMEOUT_MS) {
            serverJob?.join()
        }
        serverJob = null
        cleanup()
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun sendClipboard(packet: ClipboardPacketMessage) {
        activeHandler?.sendClipboard(packet)
    }

    private suspend fun runServer() {
        try {
            serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
            Logger.i("BluetoothServer", "Listening for Bluetooth connections")

            _state.value = SyncState.Syncing

            while (currentCoroutineContext().isActive) {
                val socket = serverSocket?.accept() ?: break
                activeSocket = socket
                Logger.i("BluetoothServer", "Accepted connection from ${socket.remoteDevice?.name}")

                handleConnection(socket)
            }
        } catch (e: IOException) {
            Logger.e("BluetoothServer", "Server error", e)
            _state.value = SyncState.Error
            onError(e.message ?: "Bluetooth server error")
        }
    }

    private suspend fun handleConnection(socket: BluetoothSocket) {
        val handler = BluetoothConnectionHandler(
            socket = socket,
            onClipboardReceived = onClipboardReceived,
            onDeviceInfoReceived = { deviceInfo ->
                onDeviceConnected(deviceInfo)
            },
            onError = { error ->
                onError(error)
            }
        )
        activeHandler = handler

        try {
            handler.run()
        } finally {
            activeHandler = null
            socket.close()
            activeSocket = null
            onDeviceDisconnected()
            Logger.i("BluetoothServer", "Connection closed")
            _state.value = SyncState.Syncing
        }
    }

    private fun cleanup() {
        try {
            activeSocket?.close()
            activeSocket = null
            serverSocket?.close()
            serverSocket = null
        } catch (e: Exception) {
            Logger.e("BluetoothServer", "Error during cleanup", e)
        }
    }
}
