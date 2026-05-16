package com.lanrhyme.clipypse

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lanrhyme.clipypse.theme.PaletteStyle
import java.net.Inet4Address
import java.net.NetworkInterface

actual fun getPlatform(): Platform = AndroidPlatform

actual fun getAppVersion(): String {
    return try {
        val context = AndroidContext.context ?: return "1.0.0"
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.versionName ?: "1.0.0"
    } catch (e: Exception) {
        "1.0.0"
    }
}

actual fun openUrl(url: String) {
    try {
        val context = AndroidContext.context ?: return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: Exception) {
        Logger.e("Platform", "Failed to open URL: $url", e)
    }
}

actual suspend fun isPortAllowed(port: Int, protocol: String): Boolean = true

actual suspend fun addFirewallRule(port: Int, protocol: String): Result<Unit> = Result.success(Unit)

actual fun isDynamicColorSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

actual fun getDynamicSeedColor(): Long? = null

actual fun isWindowsPlatform(): Boolean = false

actual fun isMacOSPlatform(): Boolean = false

@Composable
actual fun getDynamicColorScheme(isDark: Boolean, paletteStyle: PaletteStyle): ColorScheme? = null

@Composable
actual fun QrCodeImage(content: String, modifier: Modifier, sizeDp: Int) {
}

object AndroidPlatform : Platform {
    override val name: String = "Android"
    override val type: PlatformType = PlatformType.Android
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
        get() = try {
            val context = AndroidContext.context ?: return "Android"
            val manufacturer = Build.MANUFACTURER
            val model = Build.MODEL
            "$manufacturer $model"
        } catch (e: Exception) {
            "Android"
        }
}
