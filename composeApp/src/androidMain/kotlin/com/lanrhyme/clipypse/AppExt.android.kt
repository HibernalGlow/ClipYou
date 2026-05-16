package com.lanrhyme.clipypse

internal actual fun initBluetoothManager(viewModel: MainViewModel) {
    BluetoothManagerHolder.manager?.let { manager ->
        viewModel.attachBluetoothManager(manager)
    }
}
