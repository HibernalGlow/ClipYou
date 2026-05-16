package com.lanrhyme.clipypse

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import com.lanrhyme.clipypse.theme.PaletteStyle

enum class PlatformType {
    Android,
    Desktop
}

interface Platform {
    val name: String
    val type: PlatformType
    val ipAddress: String
    val ipAddresses: List<String>
    val deviceName: String
}

expect fun getPlatform(): Platform

expect fun getAppVersion(): String

expect fun openUrl(url: String)

expect suspend fun isPortAllowed(port: Int, protocol: String): Boolean

expect suspend fun addFirewallRule(port: Int, protocol: String): Result<Unit>

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

object Logger {
    private var loggerImpl: LoggerImpl? = null

    fun init(impl: LoggerImpl) {
        loggerImpl = impl
    }

    fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun w(tag: String, message: String) = log(LogLevel.WARN, tag, message)
    fun e(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.ERROR, tag, message, throwable)

    private fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        loggerImpl?.log(level, tag, message, throwable)
        if (level == LogLevel.ERROR) {
            println("[$level][$tag] $message")
            throwable?.printStackTrace()
        }
    }

    fun getLogFilePath(): String? = loggerImpl?.getLogFilePath()
}

interface LoggerImpl {
    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null)
    fun getLogFilePath(): String?
}

@Composable
expect fun getDynamicColorScheme(isDark: Boolean, paletteStyle: PaletteStyle): ColorScheme?

expect fun isDynamicColorSupported(): Boolean

expect fun getDynamicSeedColor(): Long?

expect fun isWindowsPlatform(): Boolean

expect fun isMacOSPlatform(): Boolean

@Composable
expect fun QrCodeImage(content: String, modifier: androidx.compose.ui.Modifier, sizeDp: Int)
