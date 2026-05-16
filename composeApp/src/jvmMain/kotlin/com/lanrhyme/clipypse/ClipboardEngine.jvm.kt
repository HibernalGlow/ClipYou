package com.lanrhyme.clipypse

import com.lanrhyme.clipypse.network.ClipboardServer
import com.lanrhyme.clipypse.network.ClipboardClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.awt.Toolkit
import java.awt.datatransfer.*
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import java.io.ByteArrayOutputStream
import java.io.File

actual class ClipboardEngine actual constructor() {
    private val _syncState = MutableStateFlow(SyncState.Idle)
    actual val syncState: Flow<SyncState> = _syncState.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    actual val lastError: Flow<String?> = _lastError.asStateFlow()

    private val _clipboardHistory = MutableStateFlow<List<ClipboardItem>>(emptyList())
    actual val clipboardHistory: Flow<List<ClipboardItem>> = _clipboardHistory.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    actual val isConnected: Flow<Boolean> = _isConnected.asStateFlow()

    private val _remoteDevice = MutableStateFlow<DeviceInfoMessage?>(null)
    actual val remoteDevice: Flow<DeviceInfoMessage?> = _remoteDevice.asStateFlow()

    private var server: ClipboardServer? = null
    private var client: ClipboardClient? = null
    private var monitorJob: Job? = null
    private var syncJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var autoSync: Boolean = true

    @Volatile
    private var maxHistorySize: Int = Constants.MAX_HISTORY_SIZE

    @Volatile
    private var lastClipboardHash: Int = 0

    @Volatile
    private var isServer: Boolean = false

    @Volatile
    private var isRunning: Boolean = false

    private val clipboard: Clipboard
        get() = Toolkit.getDefaultToolkit().systemClipboard

    actual suspend fun start(
        ip: String,
        port: Int,
        mode: ConnectionMode,
        isServer: Boolean
    ) {
        if (isRunning) {
            Logger.w("ClipboardEngine", "Already running")
            return
        }

        this.isServer = isServer
        isRunning = true
        _syncState.value = SyncState.Connecting
        _lastError.value = null

        try {
            if (isServer) {
                startServer(port)
            } else {
                startClient(ip, port, mode)
            }

            startClipboardMonitor()
            _syncState.value = SyncState.Syncing
            _isConnected.value = true
        } catch (e: Exception) {
            Logger.e("ClipboardEngine", "Failed to start", e)
            _syncState.value = SyncState.Error
            _lastError.value = e.message
            isRunning = false
        }
    }

    private suspend fun startServer(port: Int) {
        server = ClipboardServer(
            port = port,
            onClipboardReceived = { packet ->
                handleReceivedClipboard(packet)
            },
            onDeviceConnected = { deviceInfo ->
                _remoteDevice.value = deviceInfo
            },
            onDeviceDisconnected = {
                _remoteDevice.value = null
                _isConnected.value = false
            },
            onError = { error ->
                _lastError.value = error
            }
        )
        server?.start()
        Logger.i("ClipboardEngine", "Server started on port $port")
    }

    private suspend fun startClient(ip: String, port: Int, mode: ConnectionMode) {
        val targetIp = if (mode == ConnectionMode.Usb) "127.0.0.1" else ip
        client = ClipboardClient(
            host = targetIp,
            port = port,
            onClipboardReceived = { packet ->
                handleReceivedClipboard(packet)
            },
            onConnected = {
                _isConnected.value = true
            },
            onDisconnected = {
                _isConnected.value = false
                _remoteDevice.value = null
            },
            onError = { error ->
                _lastError.value = error
            }
        )
        client?.connect()
        Logger.i("ClipboardEngine", "Client connected to $targetIp:$port")
    }

    private fun startClipboardMonitor() {
        monitorJob = scope.launch {
            while (isActive) {
                try {
                    checkClipboard()
                } catch (e: Exception) {
                    Logger.e("ClipboardEngine", "Error checking clipboard", e)
                }
                delay(Constants.CLIPBOARD_CHECK_INTERVAL_MS)
            }
        }
    }

    private suspend fun checkClipboard() {
        if (!autoSync) return

        try {
            val item = getCurrentClipboardItem() ?: return
            val hash = item.data.contentHashCode() + item.type.hashCode()

            if (hash != lastClipboardHash) {
                lastClipboardHash = hash
                addToHistory(item)
                sendClipboardItemInternal(item)
            }
        } catch (e: Exception) {
            Logger.e("ClipboardEngine", "Error reading clipboard", e)
        }
    }

    private fun getCurrentClipboardItem(): ClipboardItem? {
        val clipboard = this.clipboard

        if (clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
            val image = clipboard.getData(DataFlavor.imageFlavor) as? BufferedImage ?: return null
            val baos = ByteArrayOutputStream()
            ImageIO.write(image, "PNG", baos)
            return ClipboardItem(
                type = ClipboardType.Image,
                data = baos.toByteArray(),
                mimeType = Constants.MimeTypes.IMAGE_PNG
            )
        }

        if (clipboard.isDataFlavorAvailable(DataFlavor.javaFileListFlavor)) {
            @Suppress("UNCHECKED_CAST")
            val files = clipboard.getData(DataFlavor.javaFileListFlavor) as? List<File> ?: return null
            if (files.isEmpty()) return null

            val file = files.first()
            if (file.length() > Constants.MAX_FILE_SIZE) {
                Logger.w("ClipboardEngine", "File too large: ${file.length()} bytes")
                return null
            }

            return ClipboardItem(
                type = ClipboardType.File,
                data = file.absolutePath.toByteArray(Charsets.UTF_8),
                mimeType = Constants.MimeTypes.APPLICATION_OCTET_STREAM,
                fileName = file.name,
                fileSize = file.length()
            )
        }

        if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
            val text = clipboard.getData(DataFlavor.stringFlavor) as? String ?: return null
            if (text.isBlank()) return null

            return ClipboardItem(
                type = ClipboardType.Text,
                data = text.toByteArray(Charsets.UTF_8),
                mimeType = Constants.MimeTypes.TEXT_PLAIN
            )
        }

        return null
    }

    private fun handleReceivedClipboard(packet: ClipboardPacketMessage) {
        val item = packet.toClipboardItem()

        scope.launch(Dispatchers.Main) {
            try {
                setSystemClipboard(item)
                addToHistory(item)
                lastClipboardHash = item.data.contentHashCode() + item.type.hashCode()
                Logger.i("ClipboardEngine", "Clipboard synced from ${packet.sourceDevice}")
            } catch (e: Exception) {
                Logger.e("ClipboardEngine", "Error setting clipboard", e)
            }
        }
    }

    private fun setSystemClipboard(item: ClipboardItem) {
        when (item.type) {
            ClipboardType.Text -> {
                val text = String(item.data, Charsets.UTF_8)
                clipboard.setContents(StringSelection(text), null)
            }
            ClipboardType.Image -> {
                val image = ImageIO.read(item.data.inputStream())
                clipboard.setContents(ImageTransferable(image), null)
            }
            ClipboardType.File -> {
                val path = String(item.data, Charsets.UTF_8)
                val file = File(path)
                if (file.exists()) {
                    clipboard.setContents(FileTransferable(listOf(file)), null)
                }
            }
            ClipboardType.Uri -> {
                val uri = String(item.data, Charsets.UTF_8)
                clipboard.setContents(StringSelection(uri), null)
            }
        }
    }

    private fun addToHistory(item: ClipboardItem) {
        val currentHistory = _clipboardHistory.value.toMutableList()
        currentHistory.add(0, item)
        while (currentHistory.size > maxHistorySize) {
            currentHistory.removeAt(currentHistory.size - 1)
        }
        _clipboardHistory.value = currentHistory
    }

    actual fun stop() {
        isRunning = false
        monitorJob?.cancel()
        syncJob?.cancel()

        runBlocking {
            server?.stop()
            client?.disconnect()
        }

        server = null
        client = null
        _syncState.value = SyncState.Idle
        _isConnected.value = false
        _remoteDevice.value = null
        Logger.i("ClipboardEngine", "Stopped")
    }

    actual suspend fun sendClipboardItem(item: ClipboardItem) {
        sendClipboardItemInternal(item)
    }

    private suspend fun sendClipboardItemInternal(item: ClipboardItem) {
        val packet = item.toPacketMessage(getPlatform().deviceName)

        if (isServer) {
            server?.sendClipboard(packet)
        } else {
            client?.sendClipboard(packet)
        }
    }

    actual suspend fun clearHistory() {
        _clipboardHistory.value = emptyList()
    }

    actual fun setMaxHistorySize(size: Int) {
        maxHistorySize = size
    }

    actual fun setAutoSync(enabled: Boolean) {
        autoSync = enabled
    }
}

private class ImageTransferable(private val image: BufferedImage) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)
    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.imageFlavor
    override fun getTransferData(flavor: DataFlavor): Any = image
}

private class FileTransferable(private val files: List<File>) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.javaFileListFlavor)
    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.javaFileListFlavor
    @Suppress("UNCHECKED_CAST")
    override fun getTransferData(flavor: DataFlavor): Any = files
}
