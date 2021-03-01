package com.github.slavebluetooth.model

import android.bluetooth.BluetoothHeadsetClientCall
import android.os.Handler

/**
 * @param name name在没有时会为number
 */
data class BluetoothCallInfo(val number: String, val name: String)

/**
 * 用于InCallPresenter
 */
internal data class BluetoothCall(val number: String, var state: Int, var uuid: String) {

    constructor(call: BluetoothHeadsetClientCall) : this(call.number, call.state, call.uuid.toString())

    private var activeTime = -1L
    private val handler: Handler by lazy { Handler() }
    private var onActiveTimeChangeListener: ((Long) -> Unit)? = null
    private val updateActiveTime = object : Runnable {
        override fun run() {
            activeTime++
            if (state != BluetoothHeadsetClientCall.CALL_STATE_HELD) {
                onActiveTimeChangeListener?.invoke(activeTime * 1000)
            }
            handler.postDelayed(this, 1000)
        }
    }

    init {
        if (state == BluetoothHeadsetClientCall.CALL_STATE_ACTIVE) {
            activeTime = -1
            handler.post(updateActiveTime)
        }
    }

    fun setOnActiveTimeChangeListener(listener: ((Long) -> Unit)?) {
        this.onActiveTimeChangeListener = listener
    }

    fun updateCall(call: BluetoothHeadsetClientCall) {
        if (call.number == number) {
            this.uuid = call.uuid.toString()
            if (call.state == BluetoothHeadsetClientCall.CALL_STATE_ACTIVE) {
                if (state != BluetoothHeadsetClientCall.CALL_STATE_ACTIVE
                        && state != BluetoothHeadsetClientCall.CALL_STATE_HELD) {
                    handler.post(updateActiveTime)
                }
            } else {
                if (call.state == BluetoothHeadsetClientCall.CALL_STATE_TERMINATED) {
                    release()
                }
            }
            this.state = call.state
        }
    }

    fun release() {
        onActiveTimeChangeListener = null
        handler.removeCallbacks(null)
        activeTime = 0
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BluetoothCall

        if (number != other.number) return false
        if (uuid.isEmpty() || other.uuid.isEmpty()) return true
        if (uuid != other.uuid) return false

        return true
    }

    override fun hashCode(): Int {
        var result = number.hashCode()
        result = 31 * result + uuid.hashCode()
        return result
    }


}