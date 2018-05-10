package com.github.caoddx.rxbluetoothhelper

enum class ConnectionStatus(val explanation: String) {
    NO_CONNECT("未连接"),
    CONNECTING("正在连接"),
    CONNECTED("已连接"),
    CONNECT_FAILED("连接失败"),
    LOST("连接丢失")
}