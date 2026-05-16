package com.lanrhyme.clipypse

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lanrhyme.clipypse.theme.PaletteStyle
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URI
import java.util.*

actual fun getPlatform(): Platform = DesktopPlatform

actual fun getAppVersion(): String {
    return System.getProperty("app.version") ?: "1.0.0"
}

actual fun openUrl(url: String) {
    try {
        Desktop.getDesktop().browse(URI(url))
    } catch (e: Exception) {
        Logger.e("Platform", "Failed to open URL: $url", e)
    }
}

actual suspend fun isPortAllowed(port: Int, protocol: String): Boolean {
    return true
}

actual suspend fun addFirewallRule(port: Int, protocol: String): Result<Unit> {
    return Result.success(Unit)
}

actual fun isDynamicColorSupported(): Boolean = false

actual fun getDynamicSeedColor(): Long? = null

actual fun isWindowsPlatform(): Boolean = System.getProperty("os.name").lowercase().contains("windows")

actual fun isMacOSPlatform(): Boolean = System.getProperty("os.name").lowercase().contains("mac")

@Composable
actual fun getDynamicColorScheme(isDark: Boolean, paletteStyle: PaletteStyle): ColorScheme? = null

@Composable
actual fun QrCodeImage(content: String, modifier: Modifier, sizeDp: Int) {
}

object DesktopPlatform : Platform {
    override val name: String = "Desktop (${System.getProperty("os.name")})"
    override val type: PlatformType = PlatformType.Desktop
    override val ipAddress: String
        get() = ipAddresses.firstOrNull() ?: "127.0.0.1"

    override val ipAddresses: List<String>
        get() = try {
            NetworkInterface.getNetworkInterfaces()
                .toList()
                .filter { !it.isLoopback && it.isUp }
                .flatMap { it.inetAddresses.toList() }
                .filter { it is Inet4Address }
                .map { it.hostAddress }
                .filter { !it.isNullOrBlank() }
                .map { it!! }
        } catch (e: Exception) {
            Logger.e("Platform", "Failed to get IP addresses", e)
            listOf("127.0.0.1")
        }

    override val deviceName: String
        get() = System.getProperty("user.name") ?: "Desktop"
}

fun copyToClipboard(text: String) {
    try {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(text), null)
    } catch (e: Exception) {
        Logger.e("Platform", "Failed to copy to clipboard", e)
    }
}
