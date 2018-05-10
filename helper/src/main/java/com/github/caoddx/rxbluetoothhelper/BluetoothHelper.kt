package com.github.caoddx.rxbluetoothhelper

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit


/**
 * Created by caodd on 2017/3/17.
 */
@SuppressLint("MissingPermission")
class BluetoothHelper {

    companion object {

        private val SSP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        private val helpers by lazy {
            mutableMapOf<String, BluetoothHelper>()
        }

        val defaultHelper: BluetoothHelper
            get() {
                return getHelper("default")
            }

        fun getHelper(tag: String): BluetoothHelper {
            var helper = helpers[tag]
            if (helper == null) {
                synchronized(this) {
                    helper = helpers[tag]
                    if (helper == null) {
                        helper = BluetoothHelper()
                        helpers[tag] = helper!!
                    }
                }
            }
            return helper!!
        }
    }

    private val mAdapter: BluetoothAdapter by lazy {
        BluetoothAdapter.getDefaultAdapter()
    }

    private var mSocket: BluetoothSocket? = null

    private var inputStreamObservable: Observable<Byte>? = null

    private val connectionSubject = BehaviorSubject.createDefault<ConnectionStatusNode>(ConnectionStatusNode()).toSerialized()

    private var node = ConnectionStatusNode()

    val isEnabled: Boolean get() = mAdapter.isEnabled

    val isConnected: Boolean get() = node.currentStatus == ConnectionStatus.CONNECTED

    val currentConnectedDevice get() = node.device

    fun connectionChange(): Observable<ConnectionStatusNode> = connectionSubject.hide()

    @Synchronized
    private fun nextStatus(status: ConnectionStatus) {
        //// Timber.d(ConnectionStatus.getStatusString(status) +" "+ this.status.toString());
        node = node.changeTo(status)
        connectionSubject.onNext(node)
    }

    @Synchronized
    private fun nextDevice(device: BluetoothDevice?) {
        node = node.changeDevice(device)
    }

    fun connect(mac: String) {

        val device = mAdapter.getRemoteDevice(mac)

        connectionSubject.filter {
            when (it.currentStatus) {
                ConnectionStatus.NO_CONNECT -> true
                ConnectionStatus.CONNECTING -> false
                ConnectionStatus.CONNECTED -> {
                    disconnect()
                    false
                }
                ConnectionStatus.CONNECT_FAILED -> true
                ConnectionStatus.LOST -> true
            }
        }
                .take(1)
                .doOnNext {
                    nextDevice(device)
                    nextStatus(ConnectionStatus.CONNECTING)
                    // Timber.d("connecting")
                }
                .observeOn(Schedulers.io())
                .map {
                    var result = true
                    try {
                        val socket = device.createRfcommSocketToServiceRecord(SSP_UUID)

                        if (socket == null) {
                            result = false
                            // Timber.e("蓝牙连接失败 socket == null name : ${device.name} address : ${device.address}")
                        } else {
                            socket.connect()
                            mSocket = socket
                            // Timber.d("connected " + device.name)
                        }
                    } catch (e: IOException) {
                        // Timber.i("蓝牙连接失败 " + device.name)
                        mSocket = null
                        result = false
                    }
                    result
                }
                .subscribe { success ->
                    if (success) {
                        //startReceive
                        inputStreamObservable = RxInputStream.from(mSocket!!.inputStream)
                                .publish().autoConnect()

                        inputStreamObservable!!
                                .doFinally { nextStatus(ConnectionStatus.LOST) }
                                .subscribe()

                        nextStatus(ConnectionStatus.CONNECTED)
                    } else {
                        nextStatus(ConnectionStatus.CONNECT_FAILED)
                    }
                }
    }

    fun send(data: List<Byte>) {
        send(data.toByteArray())
    }

    fun send(data: ByteArray) {
        check(isConnected) { "蓝牙未连接\nsend should be called after connect" }

        // Timber.i("send data: (len = %d)[%s]", data.size, data.toHexString())
        Observable.just(1).observeOn(Schedulers.io())
                .subscribe {
                    try {
                        mSocket!!.outputStream.write(data)
                    } catch (e: IOException) {
                        // Timber.w(e, "数据发送失败")
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
    fun receiveFrames(byteTimeoutInMilliseconds: Long = 100): Observable<List<Byte>> {
        return receive().buffer(receive().debounce(byteTimeoutInMilliseconds, TimeUnit.MILLISECONDS))
                .doOnNext {
                    // Timber.i("receive data: (len = %d)[%s]", it.size, it.toHexString())
                }
    }

    /**
     * 在 frameTimeout 和 unit 指定的超时时间内，发射一包数据。空list代表超时
     */
    fun receiveFrame(byteTimeoutInMilliseconds: Long = 100, frameTimeoutInMilliseconds: Long): Single<List<Byte>> {
        return receiveFrames(byteTimeoutInMilliseconds)
                .timeout(frameTimeoutInMilliseconds, TimeUnit.MILLISECONDS, Observable.just(emptyList())) // 超时发送一个空包，空包即代表“超时”
                .take(1).singleOrError()
    }

    fun disconnect() {
        // Timber.d("call disconnect")
        if (mSocket != null) {
            try {
                mSocket!!.close()
            } catch (e: IOException) {
                // Timber.w(e)
            }

            mSocket = null
        }
    }

}