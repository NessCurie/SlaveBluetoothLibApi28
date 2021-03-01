package com.github.slavebluetooth.controller.hfpclient

import com.github.slavebluetooth.model.BluetoothCallInfo


interface InCallCallBack {
    fun updateName(name: String)
    fun updateCallTime(time: String)
    fun updateAll(callInfo: BluetoothCallInfo, state: Int, callTime: String)
    fun onCallClear()
}