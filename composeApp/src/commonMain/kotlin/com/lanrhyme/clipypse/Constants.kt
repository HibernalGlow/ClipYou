package com.lanrhyme.clipypse

object Constants {
    const val DEFAULT_TCP_PORT = 28695
    const val MAX_PACKET_SIZE = 16 * 1024 * 1024
    const val MESSAGE_CHANNEL_CAPACITY = 64
    const val SERVER_STOP_TIMEOUT_MS = 5000L
    const val HEARTBEAT_INTERVAL_MS = 5000L
    const val CONNECTION_TIMEOUT_MS = 10000L
    const val CLIPBOARD_CHECK_INTERVAL_MS = 500L
    const val MAX_HISTORY_SIZE = 50
    const val MAX_FILE_SIZE = 10 * 1024 * 1024
    
    object MimeTypes {
        const val TEXT_PLAIN = "text/plain"
        const val TEXT_HTML = "text/html"
        const val IMAGE_PNG = "image/png"
        const val IMAGE_JPEG = "image/jpeg"
        const val IMAGE_GIF = "image/gif"
        const val IMAGE_WEBP = "image/webP"
        const val APPLICATION_OCTET_STREAM = "application/octet-stream"
    }
}
