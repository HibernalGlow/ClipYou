package com.lanrhyme.clipypse

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.lanrhyme.clipypse.network.BluetoothDeviceManager

class MainActivity : ComponentActivity() {
    private var bluetoothDeviceManager: BluetoothDeviceManager? = null

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            initBluetoothManager()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        AndroidContext.context = applicationContext
        Logger.init(AndroidLogger())

        enableEdgeToEdge()

        checkBluetoothPermissions()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    App()
                }
            }
        }
    }

    private fun checkBluetoothPermissions() {
        val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }

        val needsRequest = permissions.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needsRequest) {
            bluetoothPermissionLauncher.launch(permissions)
        } else {
            initBluetoothManager()
        }
    }

    private fun initBluetoothManager() {
        bluetoothDeviceManager = BluetoothDeviceManager(applicationContext)
        BluetoothManagerHolder.manager = bluetoothDeviceManager
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothDeviceManager?.cleanup()
    }
}

object BluetoothManagerHolder {
    var manager: BluetoothDeviceManager? = null
}
