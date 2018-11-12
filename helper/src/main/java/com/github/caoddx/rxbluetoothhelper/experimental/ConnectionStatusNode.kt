package com.github.caoddx.rxbluetoothhelper.experimental

import android.bluetooth.BluetoothDevice

data class ConnectionStatusNode(val tag: String,
                                val lastStatus: ConnectionStatus = ConnectionStatus.NoConnect,
                                val currentStatus: ConnectionStatus = ConnectionStatus.NoConnect,
                                val device: BluetoothDevice? = null)