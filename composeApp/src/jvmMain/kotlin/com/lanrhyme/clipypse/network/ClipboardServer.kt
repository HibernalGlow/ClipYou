package com.lanrhyme.clipypse.network

import com.lanrhyme.clipypse.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import java.net.BindException

class ClipboardServer(
    private val port: Int,
    private val onClipboardReceived: suspend (ClipboardPacketMessage) -> Unit,
    private val onDeviceConnected: (DeviceInfoMessage) -> Unit,
    private val onDeviceDisconnected: () -> Unit,
    private val onError: (String) -> Unit
) {
    private val _state = MutableStateFlow(SyncState.Idle)
    val state = _state.asStateFlow()

    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverJob: Job? = null
    private var selectorManager: SelectorManager? = null
    private var serverSocket: ServerSocket? = null
    private var activeSocket: Socket? = null
    private var activeHandler: ConnectionHandler? = null

    @OptIn(ExperimentalSerializationApi::class)
    private val proto = ProtoBuf {}

    suspend fun start() {
        serverJob?.takeIf { it.isActive }?.let {
            Logger.w("ClipboardServer", "Server already running")
            return
        }

        _state.value = SyncState.Connecting

        serverJob = serverScope.launch {
            try {
                runServer()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e("ClipboardServer", "Server fatal error", e)
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
            val manager = SelectorManager(Dispatchers.IO)
            selectorManager = manager
            serverSocket = aSocket(manager).tcp().bind("0.0.0.0", port)
            Logger.i("ClipboardServer", "Listening on port $port")

            _state.value = SyncState.Syncing

            while (currentCoroutineContext().isActive) {
                val socket = serverSocket?.accept() ?: break
                activeSocket = socket
                Logger.i("ClipboardServer", "Accepted connection from ${socket.remoteAddress}")

                handleConnection(
                    input = socket.openReadChannel(),
                    output = socket.openWriteChannel(autoFlush = true),
                    closeAction = {
                        socket.close()
                        activeSocket = null
                    }
                )
            }
        } catch (e: BindException) {
            val msg = "Port $port is already in use"
            Logger.e("ClipboardServer", msg, e)
            _state.value = SyncState.Error
            onError(msg)
        } catch (e: Exception) {
            Logger.e("ClipboardServer", "Server error", e)
            _state.value = SyncState.Error
            onError(e.message ?: "Unknown error")
        }
    }

    private suspend fun handleConnection(
        input: ByteReadChannel,
        output: ByteWriteChannel,
        closeAction: suspend () -> Unit
    ) {
        val handler = ConnectionHandler(
            input = input,
            output = output,
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
            closeAction()
            onDeviceDisconnected()
            Logger.i("ClipboardServer", "Connection closed")
            _state.value = SyncState.Syncing
        }
    }

    private fun cleanup() {
        try {
            activeSocket?.close()
            activeSocket = null
            serverSocket?.close()
            serverSocket = null
            selectorManager?.close()
            selectorManager = null
        } catch (e: Exception) {
            Logger.e("ClipboardServer", "Error during cleanup", e)
        }
    }
}

class ConnectionHandler(
    private val input: ByteReadChannel,
    private val output: ByteWriteChannel,
    private val onClipboardReceived: suspend (ClipboardPacketMessage) -> Unit,
    private val onDeviceInfoReceived: (DeviceInfoMessage) -> Unit,
    private val onError: (String) -> Unit
) {
    private val CHECK_1 = "ClipYouCheck1"
    private val CHECK_2 = "ClipYouCheck2"

    @OptIn(ExperimentalSerializationApi::class)
    private val proto = ProtoBuf {}

    private var sendChannel: kotlinx.coroutines.channels.Channel<MessageWrapper>? = null
    private var writerJob: Job? = null
    private var pingJob: Job? = null

    @Volatile
    private var rtt: Long = 0L

    suspend fun run() {
        try {
            if (!performHandshake()) {
                onError("Handshake failed")
                return
            }

            sendChannel = kotlinx.coroutines.channels.Channel(Constants.MESSAGE_CHANNEL_CAPACITY)

            coroutineScope {
                writerJob = launch(Dispatchers.IO) {
                    processSendQueue()
                }

                pingJob = launch {
                    while (isActive) {
                        sendPing()
                        delay(Constants.HEARTBEAT_INTERVAL_MS)
                    }
                }

                processReceiveLoop()
            }
        } catch (e: Exception) {
            if (!isNormalDisconnect(e)) {
                Logger.e("ConnectionHandler", "Connection error: ${e.message}", e)
                onError(e.message ?: "Connection error")
            }
        } finally {
            cleanup()
        }
    }

    private suspend fun performHandshake(): Boolean {
        return try {
            val check1Packet = input.readPacket(CHECK_1.length)
            val check1String = check1Packet.readText()

            if (check1String != CHECK_1) {
                Logger.e("ConnectionHandler", "Handshake failed: received $check1String")
                return false
            }

            output.writeFully(CHECK_2.encodeToByteArray())
            output.flush()
            true
        } catch (e: Exception) {
            Logger.e("ConnectionHandler", "Handshake error", e)
            false
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun processSendQueue() {
        val channel = sendChannel ?: return
        for (msg in channel) {
            try {
                val packetBytes = proto.encodeToByteArray(MessageWrapper.serializer(), msg)
                val length = packetBytes.size
                output.writeInt(PACKET_MAGIC)
                output.writeInt(length)
                output.writeFully(packetBytes)
                output.flush()
            } catch (e: Exception) {
                break
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun processReceiveLoop() {
        while (currentCoroutineContext().isActive) {
            val magic = input.readInt()
            if (magic != PACKET_MAGIC) {
                var resyncMagic = magic
                while (currentCoroutineContext().isActive) {
                    val byte = input.readByte().toInt() and 0xFF
                    resyncMagic = (resyncMagic shl 8) or byte
                    if (resyncMagic == PACKET_MAGIC) break
                }
            }

            val length = input.readInt()

            if (length > Constants.MAX_PACKET_SIZE || length <= 0) {
                Logger.w("ConnectionHandler", "Invalid packet size: $length")
                continue
            }

            val packetBytes = ByteArray(length)
            input.readFully(packetBytes)

            try {
                val wrapper = proto.decodeFromByteArray(MessageWrapper.serializer(), packetBytes)

                wrapper.clipboardPacket?.let { packet ->
                    onClipboardReceived(packet)
                }

                wrapper.deviceInfo?.let { deviceInfo ->
                    onDeviceInfoReceived(deviceInfo)
                }

                wrapper.pong?.let { pong ->
                    rtt = System.currentTimeMillis() - pong.timestamp
                }
            } catch (e: Exception) {
                Logger.e("ConnectionHandler", "Error decoding packet", e)
            }
        }
    }

    private suspend fun sendPing() {
        try {
            sendChannel?.send(MessageWrapper(ping = PingMessage(System.currentTimeMillis())))
        } catch (e: Exception) {
            // Ignore
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun sendClipboard(packet: ClipboardPacketMessage) {
        try {
            sendChannel?.send(MessageWrapper(clipboardPacket = packet))
        } catch (e: Exception) {
            Logger.e("ConnectionHandler", "Error sending clipboard", e)
        }
    }

    fun getRtt(): Long = rtt

    private fun cleanup() {
        writerJob?.cancel()
        sendChannel?.close()
        sendChannel = null
    }

    private fun isNormalDisconnect(e: Throwable): Boolean {
        if (e is kotlinx.coroutines.CancellationException) return true
        if (e is java.io.EOFException) return true
        if (e is kotlinx.coroutines.channels.ClosedReceiveChannelException) return true
        if (e is java.io.IOException) {
            val msg = e.message ?: ""
            if (msg.contains("Socket closed", ignoreCase = true)) return true
            if (msg.contains("Connection reset", ignoreCase = true)) return true
            if (msg.contains("Broken pipe", ignoreCase = true)) return true
        }
        return false
    }
}
