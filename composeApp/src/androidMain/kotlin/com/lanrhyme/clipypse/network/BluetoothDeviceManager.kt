package com.lanrhyme.clipypse.network

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.lanrhyme.clipypse.AndroidContext
import com.lanrhyme.clipypse.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BluetoothDeviceInfo(
    val name: String,
    val address: String,
    val bonded: Boolean = false
)

class BluetoothDeviceManager(
    private val context: Context
) {
    private val _discoveredDevices = MutableStateFlow<List<BluetoothDeviceInfo>>(emptyList())
    val discoveredDevices = _discoveredDevices.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering = _isDiscovering.asStateFlow()

    private val _isBluetoothEnabled = MutableStateFlow(false)
    val isBluetoothEnabled = _isBluetoothEnabled.asStateFlow()

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var discoveryJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    
                    device?.let { addDevice(it) }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _isDiscovering.value = false
                    Logger.i("BluetoothDeviceManager", "Discovery finished")
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    _isBluetoothEnabled.value = (state == BluetoothAdapter.STATE_ON)
                    Logger.i("BluetoothDeviceManager", "Bluetooth state changed: $state")
                }
            }
        }
    }

    init {
        _isBluetoothEnabled.value = bluetoothAdapter?.isEnabled == true
        loadBondedDevices()
        registerReceivers()
    }

    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(discoveryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(discoveryReceiver, filter)
        }
    }

    fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun loadBondedDevices() {
        if (!hasBluetoothPermission()) {
            Logger.w("BluetoothDeviceManager", "No Bluetooth permission")
            return
        }

        try {
            val bondedDevices = bluetoothAdapter?.bondedDevices?.map { device ->
                BluetoothDeviceInfo(
                    name = device.name ?: device.address,
                    address = device.address,
                    bonded = true
                )
            } ?: emptyList()

            val currentList = _discoveredDevices.value.toMutableList()
            bondedDevices.forEach { bonded ->
                if (currentList.none { it.address == bonded.address }) {
                    currentList.add(bonded)
                }
            }
            _discoveredDevices.value = currentList.sortedByDescending { it.bonded }
            Logger.i("BluetoothDeviceManager", "Loaded ${bondedDevices.size} bonded devices")
        } catch (e: SecurityException) {
            Logger.e("BluetoothDeviceManager", "Error loading bonded devices", e)
        }
    }

    fun startDiscovery() {
        if (!hasBluetoothPermission()) {
            Logger.w("BluetoothDeviceManager", "No Bluetooth permission for discovery")
            return
        }

        if (_isDiscovering.value) {
            Logger.w("BluetoothDeviceManager", "Discovery already in progress")
            return
        }

        try {
            bluetoothAdapter?.startDiscovery()
            _isDiscovering.value = true
            Logger.i("BluetoothDeviceManager", "Discovery started")
        } catch (e: SecurityException) {
            Logger.e("BluetoothDeviceManager", "Error starting discovery", e)
        }
    }

    fun stopDiscovery() {
        try {
            bluetoothAdapter?.cancelDiscovery()
            _isDiscovering.value = false
            Logger.i("BluetoothDeviceManager", "Discovery stopped")
        } catch (e: SecurityException) {
            Logger.e("BluetoothDeviceManager", "Error stopping discovery", e)
        }
    }

    private fun addDevice(device: BluetoothDevice) {
        try {
            val deviceInfo = BluetoothDeviceInfo(
                name = device.name ?: device.address,
                address = device.address,
                bonded = false
            )

            val currentList = _discoveredDevices.value.toMutableList()
            if (currentList.none { it.address == deviceInfo.address }) {
                currentList.add(deviceInfo)
                _discoveredDevices.value = currentList.sortedByDescending { it.bonded }
                Logger.i("BluetoothDeviceManager", "Device found: ${deviceInfo.name}")
            }
        } catch (e: SecurityException) {
            Logger.e("BluetoothDeviceManager", "Error adding device", e)
        }
    }

    fun clearDevices() {
        _discoveredDevices.value = emptyList()
    }

    fun cleanup() {
        stopDiscovery()
        try {
            context.unregisterReceiver(discoveryReceiver)
        } catch (e: Exception) {
            Logger.e("BluetoothDeviceManager", "Error unregistering receiver", e)
        }
        discoveryJob?.cancel()
    }
}
