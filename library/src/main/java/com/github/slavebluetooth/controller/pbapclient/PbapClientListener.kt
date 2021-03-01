package com.github.slavebluetooth.controller.pbapclient

import android.bluetooth.BluetoothDevice
import com.github.slavebluetooth.model.BluetoothCallLog
import com.github.slavebluetooth.model.BluetoothPhonebook

interface OnPhonebookPullStateChangeListener {
    fun onPhonebookPullStart(device: BluetoothDevice)

    fun onPhonebookPullComplete(device: BluetoothDevice, list: List<BluetoothPhonebook>)
}

interface OnCallLogPullStateChangeListener {
    fun onCallLogPullStart(device: BluetoothDevice)

    fun onCallLogPullComplete(device: BluetoothDevice, list: List<BluetoothCallLog>)
}