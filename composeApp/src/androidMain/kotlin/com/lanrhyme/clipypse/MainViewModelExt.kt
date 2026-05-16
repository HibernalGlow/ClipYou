package com.lanrhyme.clipypse

import com.lanrhyme.clipypse.network.BluetoothDeviceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val bluetoothScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

internal actual fun MainViewModel.startBluetoothDiscoveryImpl() {
    BluetoothManagerHolder.manager?.startDiscovery()
}

internal actual fun MainViewModel.stopBluetoothDiscoveryImpl() {
    BluetoothManagerHolder.manager?.stopDiscovery()
}

internal actual fun MainViewModel.attachBluetoothManagerImpl(manager: Any) {
    if (manager is BluetoothDeviceManager) {
        bluetoothScope.launch {
            manager.discoveredDevices.collect { devices ->
                updateBluetoothDevices(devices.map { device ->
                    BluetoothDeviceInfo(
                        name = device.name,
                        address = device.address,
                        bonded = device.bonded
                    )
                })
            }
        }

        bluetoothScope.launch {
            manager.isDiscovering.collect { isDiscovering ->
                updateBluetoothDiscovering(isDiscovering)
            }
        }
    }
}
