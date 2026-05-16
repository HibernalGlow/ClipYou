package com.lanrhyme.clipypse.network

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.lanrhyme.clipypse.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.IOException
import java.util.*

class BluetoothClient(
    private val context: Context,
    private val deviceAddress: String,
    private val onClipboardReceived: suspend (ClipboardPacketMessage) -> Unit,
    private val onConnected: () -> Unit,
    private val onDisconnected: () -> Unit,
    private val onError: (String) -> Unit
) {
    private val _state = MutableStateFlow(SyncState.Idle)
    val state = _state.asStateFlow()

    private val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var clientJob: Job? = null
    private var socket: BluetoothSocket? = null
    private var sendChannel: Channel<MessageWrapper>? = null
    private var writerJob: Job? = null
    private var pingJob: Job? = null

    @OptIn(ExperimentalSerializationApi::class)
    private val proto = ProtoBuf {}

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
    }

    suspend fun connect() {
        clientJob?.takeIf { it.isActive }?.let {
            Logger.w("BluetoothClient", "Client already connected")
            return
        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            onError("Bluetooth is not available or not enabled")
            return
        }

        _state.value = SyncState.Connecting

        clientJob = clientScope.launch {
            try {
                runClient()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e("BluetoothClient", "Client error", e)
                _state.value = SyncState.Error
                onError(e.message ?: "Unknown error")
            } finally {
                cleanup()
                onDisconnected()
                if (_state.value != SyncState.Error) {
                    _state.value = SyncState.Idle
                }
            }
        }
    }

    suspend fun disconnect() {
        clientJob?.cancel()
        withTimeoutOrNull(Constants.SERVER_STOP_TIMEOUT_MS) {
            clientJob?.join()
        }
        clientJob = null
        cleanup()
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun sendClipboard(packet: ClipboardPacketMessage) {
        try {
            sendChannel?.send(MessageWrapper(clipboardPacket = packet))
        } catch (e: Exception) {
            Logger.e("BluetoothClient", "Error sending clipboard", e)
        }
    }

    private suspend fun runClient() {
        try {
            val device: BluetoothDevice = bluetoothAdapter!!.getRemoteDevice(deviceAddress)
            
            Logger.i("BluetoothClient", "Connecting to ${device.name ?: device.address}")

            socket = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
            socket?.connect()

            Logger.i("BluetoothClient", "Connected to ${device.name}")

            val input = socket!!.inputStream
            val output = socket!!.outputStream

            sendChannel = Channel(Constants.MESSAGE_CHANNEL_CAPACITY)

            _state.value = SyncState.Syncing
            onConnected()

            coroutineScope {
                writerJob = launch(Dispatchers.IO) {
                    processSendQueue(output)
                }

                pingJob = launch {
                    while (isActive) {
                        sendPing()
                        delay(Constants.HEARTBEAT_INTERVAL_MS)
                    }
                }

                sendDeviceInfo()

                processReceiveLoop(input)
            }
        } catch (e: IOException) {
            Logger.e("BluetoothClient", "Connection error", e)
            throw e
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun processSendQueue(output: java.io.OutputStream) {
        val channel = sendChannel ?: return
        for (msg in channel) {
            try {
                val packetBytes = proto.encodeToByteArray(MessageWrapper.serializer(), msg)
                val dataOutputStream = java.io.DataOutputStream(output)
                dataOutputStream.writeInt(PACKET_MAGIC)
                dataOutputStream.writeInt(packetBytes.size)
                dataOutputStream.write(packetBytes)
                dataOutputStream.flush()
            } catch (e: Exception) {
                Logger.e("BluetoothClient", "Error sending message", e)
                break
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun processReceiveLoop(input: java.io.InputStream) {
        val dataInputStream = java.io.DataInputStream(input)
        
        while (currentCoroutineContext().isActive) {
            try {
                val magic = dataInputStream.readInt()
                if (magic != PACKET_MAGIC) {
                    continue
                }

                val length = dataInputStream.readInt()

                if (length > Constants.MAX_PACKET_SIZE || length <= 0) {
                    Logger.w("BluetoothClient", "Invalid packet size: $length")
                    continue
                }

                val packetBytes = ByteArray(length)
                dataInputStream.readFully(packetBytes)

                try {
                    val wrapper = proto.decodeFromByteArray(MessageWrapper.serializer(), packetBytes)

                    wrapper.clipboardPacket?.let { packet ->
                        onClipboardReceived(packet)
                    }
                } catch (e: Exception) {
                    Logger.e("BluetoothClient", "Error decoding packet", e)
                }
            } catch (e: IOException) {
                Logger.e("BluetoothClient", "Receive loop error", e)
                break
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun sendDeviceInfo() {
        val platform = getPlatform()
        val deviceInfo = DeviceInfoMessage(
            deviceName = platform.deviceName,
            platform = platform.name,
            version = getAppVersion()
        )
        sendChannel?.send(MessageWrapper(deviceInfo = deviceInfo))
    }

    private suspend fun sendPing() {
        try {
            sendChannel?.send(MessageWrapper(ping = PingMessage(System.currentTimeMillis())))
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun cleanup() {
        try {
            writerJob?.cancel()
            pingJob?.cancel()
            sendChannel?.close()
            sendChannel = null
            socket?.close()
            socket = null
        } catch (e: Exception) {
            Logger.e("BluetoothClient", "Error during cleanup", e)
        }
    }
}
