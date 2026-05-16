package com.lanrhyme.clipypse.network

import com.lanrhyme.clipypse.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf

class ClipboardClient(
    private val host: String,
    private val port: Int,
    private val onClipboardReceived: suspend (ClipboardPacketMessage) -> Unit,
    private val onConnected: () -> Unit,
    private val onDisconnected: () -> Unit,
    private val onError: (String) -> Unit
) {
    private val _state = MutableStateFlow(SyncState.Idle)
    val state = _state.asStateFlow()

    private val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var clientJob: Job? = null
    private var selectorManager: SelectorManager? = null
    private var socket: Socket? = null
    private var sendChannel: Channel<MessageWrapper>? = null
    private var writerJob: Job? = null
    private var pingJob: Job? = null

    @OptIn(ExperimentalSerializationApi::class)
    private val proto = ProtoBuf {}

    private val CHECK_1 = "ClipYouCheck1"
    private val CHECK_2 = "ClipYouCheck2"

    @Volatile
    private var rtt: Long = 0L

    suspend fun connect() {
        clientJob?.takeIf { it.isActive }?.let {
            Logger.w("ClipboardClient", "Client already connected")
            return
        }

        _state.value = SyncState.Connecting

        clientJob = clientScope.launch {
            try {
                runClient()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e("ClipboardClient", "Client error", e)
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
            Logger.e("ClipboardClient", "Error sending clipboard", e)
        }
    }

    private suspend fun runClient() {
        try {
            val manager = SelectorManager(Dispatchers.IO)
            selectorManager = manager

            Logger.i("ClipboardClient", "Connecting to $host:$port")

            socket = aSocket(manager).tcp().connect(host, port) {
                keepAlive = true
                socketTimeout = Constants.CONNECTION_TIMEOUT_MS
                noDelay = true
            }

            Logger.i("ClipboardClient", "Connected to $host:$port")

            val input = socket!!.openReadChannel()
            val output = socket!!.openWriteChannel(autoFlush = true)

            if (!performHandshake(input, output)) {
                onError("Handshake failed")
                return
            }

            Logger.i("ClipboardClient", "Handshake successful")

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
        } catch (e: Exception) {
            Logger.e("ClipboardClient", "Connection error", e)
            throw e
        }
    }

    private suspend fun performHandshake(input: ByteReadChannel, output: ByteWriteChannel): Boolean {
        return try {
            output.writeFully(CHECK_1.encodeToByteArray())
            output.flush()

            val responseBuffer = ByteArray(CHECK_2.length)
            input.readFully(responseBuffer, 0, responseBuffer.size)

            responseBuffer.decodeToString() == CHECK_2
        } catch (e: Exception) {
            Logger.e("ClipboardClient", "Handshake error", e)
            false
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun processSendQueue(output: ByteWriteChannel) {
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
                Logger.e("ClipboardClient", "Error sending message", e)
                break
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun processReceiveLoop(input: ByteReadChannel) {
        while (currentCoroutineContext().isActive) {
            try {
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
                    Logger.w("ClipboardClient", "Invalid packet size: $length")
                    continue
                }

                val packetBytes = ByteArray(length)
                input.readFully(packetBytes)

                try {
                    val wrapper = proto.decodeFromByteArray(MessageWrapper.serializer(), packetBytes)

                    wrapper.clipboardPacket?.let { packet ->
                        onClipboardReceived(packet)
                    }

                    wrapper.pong?.let { pong ->
                        rtt = System.currentTimeMillis() - pong.timestamp
                    }
                } catch (e: Exception) {
                    Logger.e("ClipboardClient", "Error decoding packet", e)
                }
            } catch (e: Exception) {
                if (!isNormalDisconnect(e)) {
                    Logger.e("ClipboardClient", "Receive loop error", e)
                }
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

    fun getRtt(): Long = rtt

    private fun cleanup() {
        try {
            writerJob?.cancel()
            pingJob?.cancel()
            sendChannel?.close()
            sendChannel = null
            socket?.close()
            socket = null
            selectorManager?.close()
            selectorManager = null
        } catch (e: Exception) {
            Logger.e("ClipboardClient", "Error during cleanup", e)
        }
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
