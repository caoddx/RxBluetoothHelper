package com.github.caoddx.rxbluetoothhelper

import io.reactivex.Emitter
import io.reactivex.Observable
import io.reactivex.functions.BiFunction
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Callable

/**
 * Created by caodd on 2017/8/14.
 */
object RxInputStream {

    fun from(inputStream: InputStream): Observable<Byte> {

        return Observable.generate(Callable<InputStream> { inputStream }, BiFunction<InputStream, Emitter<Byte>, InputStream> { input, emitter ->
            try {
                val data = input.read()
                if (data == -1) {
                    emitter.onComplete()
                } else {
                    emitter.onNext(data.toByte())
                }
            } catch (e: IOException) {
                emitter.onComplete()
            }
            input
        }, Consumer { input -> input.close() })
                .subscribeOn(Schedulers.io())
    }

    fun InputStream.toObservable(): Observable<Byte> {
        return from(this)
    }
}
