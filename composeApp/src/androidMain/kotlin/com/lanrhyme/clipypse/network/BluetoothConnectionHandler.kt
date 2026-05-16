package com.lanrhyme.clipypse.network

import android.bluetooth.BluetoothSocket
import com.lanrhyme.clipypse.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.IOException

class BluetoothConnectionHandler(
    private val socket: BluetoothSocket,
    private val onClipboardReceived: suspend (ClipboardPacketMessage) -> Unit,
    private val onDeviceInfoReceived: (DeviceInfoMessage) -> Unit,
    private val onError: (String) -> Unit
) {
    private val CHECK_1 = "ClipYouCheck1"
    private val CHECK_2 = "ClipYouCheck2"

    @OptIn(ExperimentalSerializationApi::class)
    private val proto = ProtoBuf {}

    private var sendChannel: Channel<MessageWrapper>? = null
    private var writerJob: Job? = null
    private var pingJob: Job? = null

    @Volatile
    private var rtt: Long = 0L

    suspend fun run() {
        try {
            val input = socket.inputStream
            val output = socket.outputStream

            if (!performHandshake(input, output)) {
                onError("Handshake failed")
                return
            }

            sendChannel = Channel(Constants.MESSAGE_CHANNEL_CAPACITY)

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

                processReceiveLoop(input)
            }
        } catch (e: Exception) {
            if (!isNormalDisconnect(e)) {
                Logger.e("BluetoothConnectionHandler", "Connection error: ${e.message}", e)
                onError(e.message ?: "Connection error")
            }
        } finally {
            cleanup()
        }
    }

    private suspend fun performHandshake(input: java.io.InputStream, output: java.io.OutputStream): Boolean {
        return try {
            output.write(CHECK_1.toByteArray())
            output.flush()

            val responseBuffer = ByteArray(CHECK_2.length)
            input.read(responseBuffer)

            responseBuffer.decodeToString() == CHECK_2
        } catch (e: Exception) {
            Logger.e("BluetoothConnectionHandler", "Handshake error", e)
            false
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun processSendQueue(output: java.io.OutputStream) {
        val channel = sendChannel ?: return
        val dataOutputStream = java.io.DataOutputStream(output)
        
        for (msg in channel) {
            try {
                val packetBytes = proto.encodeToByteArray(MessageWrapper.serializer(), msg)
                dataOutputStream.writeInt(PACKET_MAGIC)
                dataOutputStream.writeInt(packetBytes.size)
                dataOutputStream.write(packetBytes)
                dataOutputStream.flush()
            } catch (e: Exception) {
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
                    Logger.w("BluetoothConnectionHandler", "Invalid packet size: $length")
                    continue
                }

                val packetBytes = ByteArray(length)
                dataInputStream.readFully(packetBytes)

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
                    Logger.e("BluetoothConnectionHandler", "Error decoding packet", e)
                }
            } catch (e: IOException) {
                break
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
            Logger.e("BluetoothConnectionHandler", "Error sending clipboard", e)
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
        if (e is IOException) {
            val msg = e.message ?: ""
            if (msg.contains("Socket closed", ignoreCase = true)) return true
            if (msg.contains("Connection reset", ignoreCase = true)) return true
            if (msg.contains("Broken pipe", ignoreCase = true)) return true
        }
        return false
    }
}
