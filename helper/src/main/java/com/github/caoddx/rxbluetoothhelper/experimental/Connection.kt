package com.github.caoddx.rxbluetoothhelper.experimental

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.github.caoddx.rxbluetoothhelper.experimental.ConnectionStatus.*
import com.github.caoddx.rxbluetoothhelper.RxInputStream
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit

@SuppressLint("MissingPermission")
class Connection(val tag: String) {

    private var mSocket: BluetoothSocket? = null

    private var inputStreamObservable: Observable<Byte>? = null

    @Volatile
    private var node: ConnectionStatusNode = ConnectionStatusNode(tag)

    private val connectionSubject = BehaviorSubject.createDefault<ConnectionStatusNode>(ConnectionStatusNode(tag)).toSerialized()

    fun connectionChange(): Observable<ConnectionStatusNode> = connectionSubject.hide()

    val isConnected: Boolean
        get() = node.currentStatus == Connected

    val isConnecting: Boolean
        get() = node.currentStatus == Connecting

    private fun nextStatus(status: ConnectionStatus) {
        synchronized(this) {
            node = node.copy(lastStatus = node.currentStatus, currentStatus = status)
        }
        connectionSubject.onNext(node)
        BluetoothHelper.connectionSubject.onNext(node)
    }

    private fun setDevice(device: BluetoothDevice?) {
        synchronized(this) {
            node = node.copy(device = device)
        }
    }

    fun connect(address: String): Single<Boolean> {
        val device = BluetoothHelper.adapter.getRemoteDevice(address)
        return connectionChange()
                .doOnNext {
                    when (it.currentStatus) {
                        NoConnect -> {
                        }
                        Connecting -> {
                            // 等待连接过程结束，会进入到Connected或ConnectFailed状态
                        }
                        Connected -> {
                            // 断开上一次的连接
                            disconnect()
                        }
                        ConnectFailed -> {
                            // 等待状态自动切换到NoConnect
                        }
                        Lost -> {
                            // 等待状态自动切换到NoConnect
                        }
                    }
                }
                .filter { it.currentStatus == NoConnect }
                .filter {
                    // 多次调用connect时，按照调用的顺序依次进入此处
                    // 第一个进入的device为空，然后setDevice，导致后续的device不为空，等待下一次NoConnect
                    if (node.device == null) {
                        setDevice(device)
                        true
                    } else {
                        false
                    }
                }
                .firstOrError()
                .observeOn(Schedulers.io())
                .map {
                    nextStatus(Connecting)

                    var result = true
                    mSocket = null
                    try {
                        val socket = device.createRfcommSocketToServiceRecord(SSP_UUID)

                        if (socket == null) {
                            result = false
                            //Timber.e("蓝牙连接失败 socket == null name : ${device.name} address : ${device.address}")
                        } else {
                            socket.connect()
                            mSocket = socket
                            //Timber.d("connected " + device.name)
                        }
                    } catch (e: IOException) {
                        //Timber.i("蓝牙连接失败 " + device.name)
                        result = false
                    }
                    if (result) {
                        //startReceive
                        inputStreamObservable = RxInputStream.from(mSocket!!.inputStream)
                                .publish().autoConnect()

                        inputStreamObservable!!
                                .ignoreElements()
                                .doFinally {
                                    nextStatus(Lost)
                                }
                                .delay(1, TimeUnit.SECONDS)
                                .doFinally {
                                    setDevice(null)
                                    nextStatus(NoConnect)
                                }
                                .subscribe()
                        nextStatus(Connected)
                    } else {
                        nextStatus(ConnectFailed)
                        Single.timer(1, TimeUnit.SECONDS)
                                .subscribe { _ ->
                                    setDevice(null)
                                    nextStatus(NoConnect)
                                }
                    }
                    result
                }
    }

    fun send(data: List<Byte>) {
        send(data.toByteArray())
    }

    @SuppressLint("CheckResult")
    fun send(data: ByteArray) {
        check(isConnected) { "蓝牙未连接\nsend should be called after connect" }

        //Timber.i("send data: (len = %d)[%s]", data.size, data.toHexString())
        Single.just(data).observeOn(Schedulers.io())
                .subscribe { d ->
                    try {
                        mSocket!!.outputStream.write(d)
                    } catch (e: IOException) {
                        //Timber.w(e, "数据发送失败")
                    }
                }
    }

    /**
     * 按字节发射蓝牙接收到的数据
     */
    fun receive(): Observable<Byte> {
        check(isConnected) { "蓝牙未连接\nreceive should be called after connect" }

        return inputStreamObservable!!
    }

    /**
     * 根据时间间隔对数据进行分包，连续的数据视为一包，时间超过100ms即视为分包。
     * 此方法会发射包数据，直到取消订阅。
     */
    fun receiveFrames(): Observable<List<Byte>> {
        return receive().buffer(receive().debounce(100, TimeUnit.MILLISECONDS))
        //.doOnNext { Timber.i("receive data: (len = %d)[%s]", it.size, it.toHexString()) }
    }

    /**
     * 在 frameTimeout 和 unit 指定的超时时间内，发射一包数据。空list代表超时
     */
    fun receiveFrame(frameTimeout: Long, unit: TimeUnit): Single<List<Byte>> {
        return receiveFrames()
                .timeout(frameTimeout, unit, Observable.just(emptyList())) // 超时发送一个空包，空包即代表“超时”
                .take(1).singleOrError()
    }

    fun disconnect() {
        //Timber.d("call disconnect")
        if (mSocket != null) {
            try {
                mSocket!!.close()
            } catch (e: IOException) {
                //Timber.w(e)
            }

            mSocket = null
        }
    }

    companion object {
        private val SSP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

}