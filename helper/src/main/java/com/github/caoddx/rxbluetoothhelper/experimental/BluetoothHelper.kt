package com.github.caoddx.rxbluetoothhelper.experimental

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.ConcurrentHashMap


@SuppressLint("MissingPermission")
object BluetoothHelper {

    internal val adapter: BluetoothAdapter by lazy {
        BluetoothAdapter.getDefaultAdapter()
    }

    private val connections = ConcurrentHashMap<String, Connection>()

    internal val connectionSubject = PublishSubject.create<ConnectionStatusNode>().toSerialized()

    fun connectionChange(): Observable<ConnectionStatusNode> = connectionSubject.hide()

    fun getConnection(tag: String): Connection {
        val c = connections[tag]
        if (c == null) {
            connections.putIfAbsent(tag, Connection(tag))
            return connections[tag]!!
        }
        return c
    }

    val defaultConnection: Connection
        get() {
            return getConnection("default")
        }
}