package com.github.caoddx.rxbluetoothhelper

import android.bluetooth.BluetoothDevice

data class ConnectionStatusNode(val lastStatus: ConnectionStatus = ConnectionStatus.NO_CONNECT,
                                val currentStatus: ConnectionStatus = ConnectionStatus.NO_CONNECT,
                                val device: BluetoothDevice? = null) {

    internal fun changeTo(status: ConnectionStatus) = copy(lastStatus = currentStatus, currentStatus = status, device = device)

    internal fun changeDevice(device: BluetoothDevice?) = copy(lastStatus = lastStatus, currentStatus = currentStatus, device = device)
}