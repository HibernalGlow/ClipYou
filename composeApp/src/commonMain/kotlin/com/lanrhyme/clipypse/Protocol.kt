package com.lanrhyme.clipypse

import kotlinx.serialization.Serializable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoNumber

enum class ClipboardType {
    Text,
    Image,
    File,
    Uri
}

enum class SyncDirection {
    ToDesktop,
    ToMobile,
    Bidirectional
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ClipboardPacketMessage(
    @ProtoNumber(1)
    val type: Int,
    @ProtoNumber(2)
    val data: ByteArray,
    @ProtoNumber(3)
    val timestamp: Long = System.currentTimeMillis(),
    @ProtoNumber(4)
    val mimeType: String = "",
    @ProtoNumber(5)
    val fileName: String = "",
    @ProtoNumber(6)
    val fileSize: Long = 0,
    @ProtoNumber(7)
    val sourceDevice: String = "",
    @ProtoNumber(8)
    val checksum: String = ""
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as ClipboardPacketMessage
        if (type != other.type) return false
        if (!data.contentEquals(other.data)) return false
        if (timestamp != other.timestamp) return false
        if (mimeType != other.mimeType) return false
        if (fileName != other.fileName) return false
        if (fileSize != other.fileSize) return false
        if (sourceDevice != other.sourceDevice) return false
        if (checksum != other.checksum) return false
        return true
    }

    override fun hashCode(): Int {
        var result = type
        result = 31 * result + data.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + fileSize.hashCode()
        result = 31 * result + sourceDevice.hashCode()
        result = 31 * result + checksum.hashCode()
        return result
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ClipboardSyncRequest(
    @ProtoNumber(1)
    val requestId: String,
    @ProtoNumber(2)
    val direction: Int = SyncDirection.Bidirectional.ordinal
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ClipboardSyncResponse(
    @ProtoNumber(1)
    val requestId: String,
    @ProtoNumber(2)
    val success: Boolean,
    @ProtoNumber(3)
    val message: String = ""
)

@Serializable
class ConnectMessage

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class DeviceInfoMessage(
    @ProtoNumber(1)
    val deviceName: String,
    @ProtoNumber(2)
    val platform: String,
    @ProtoNumber(3)
    val version: String
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PingMessage(
    @ProtoNumber(1)
    val timestamp: Long
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PongMessage(
    @ProtoNumber(1)
    val timestamp: Long
)

const val PACKET_MAGIC = 0x436C6970 // "Clip" in ASCII

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class MessageWrapper(
    @ProtoNumber(1)
    val clipboardPacket: ClipboardPacketMessage? = null,
    @ProtoNumber(2)
    val connect: ConnectMessage? = null,
    @ProtoNumber(3)
    val syncRequest: ClipboardSyncRequest? = null,
    @ProtoNumber(4)
    val syncResponse: ClipboardSyncResponse? = null,
    @ProtoNumber(5)
    val deviceInfo: DeviceInfoMessage? = null,
    @ProtoNumber(6)
    val ping: PingMessage? = null,
    @ProtoNumber(7)
    val pong: PongMessage? = null
)

fun MessageWrapper.hasControlMessage(): Boolean {
    return connect != null || syncRequest != null || syncResponse != null || deviceInfo != null || ping != null || pong != null
}

data class ClipboardItem(
    val type: ClipboardType,
    val data: ByteArray,
    val mimeType: String = "",
    val fileName: String = "",
    val fileSize: Long = 0,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as ClipboardItem
        if (type != other.type) return false
        if (!data.contentEquals(other.data)) return false
        if (mimeType != other.mimeType) return false
        if (fileName != other.fileName) return false
        if (fileSize != other.fileSize) return false
        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + fileSize.hashCode()
        return result
    }
}

fun ClipboardItem.toPacketMessage(sourceDevice: String): ClipboardPacketMessage {
    return ClipboardPacketMessage(
        type = type.ordinal,
        data = data,
        timestamp = timestamp,
        mimeType = mimeType,
        fileName = fileName,
        fileSize = fileSize,
        sourceDevice = sourceDevice
    )
}

fun ClipboardPacketMessage.toClipboardItem(): ClipboardItem {
    return ClipboardItem(
        type = ClipboardType.entries.getOrElse(type) { ClipboardType.Text },
        data = data,
        mimeType = mimeType,
        fileName = fileName,
        fileSize = fileSize,
        timestamp = timestamp
    )
}
