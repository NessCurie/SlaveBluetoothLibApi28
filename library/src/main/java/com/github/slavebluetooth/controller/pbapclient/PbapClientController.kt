package com.github.slavebluetooth.controller.pbapclient

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.*
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import com.android.bluetooth.pbapclient.PbapClientStateMachine
import com.github.slavebluetooth.controller.CoreController
import com.github.slavebluetooth.controller.set.SetControlHelper
import com.github.slavebluetooth.model.BluetoothCallLog
import com.github.slavebluetooth.model.BluetoothPhonebook
import java.util.concurrent.ConcurrentHashMap

object PbapClientController {

    // MAXIMUM_DEVICES set to 10 to prevent an excessive number of simultaneous devices.
    private const val MAXIMUM_DEVICES = 10

    private lateinit var context: Context
    private val handler = Handler(Looper.getMainLooper())
    private val sp: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(context) }
    private val defaultAuto = true
    private val autoPull: Boolean
        get() = sp.getBoolean(SetControlHelper.SP_AUTO_PULL_PHONE_BOOK_KEY, defaultAuto)

    private val pbapClientStateMachineMap: ConcurrentHashMap<BluetoothDevice, PbapClientStateMachine> = ConcurrentHashMap()
    private val pbapUserUnlockedReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (Intent.ACTION_USER_UNLOCKED == intent.action) {
                if (autoPull) {
                    for (stateMachine in pbapClientStateMachineMap.values) {
                        stateMachine.resumeDownload()
                    }
                }
            }
        }
    }

    private val onPhonebookPullStartListeners = ArrayList<((BluetoothDevice) -> Unit)>()
    private val onPhonebookPullFinishListeners = ArrayList<((BluetoothDevice, List<BluetoothPhonebook>) -> Unit)>()
    private val onCallLogPullStartListeners = ArrayList<((BluetoothDevice) -> Unit)>()
    private val onCallLogPullFinishListeners = ArrayList<((BluetoothDevice, List<BluetoothCallLog>) -> Unit)>()

    private val onHfpClientConnectStateChangeListener: (Int, BluetoothDevice?) -> Unit by lazy {
        object : (Int, BluetoothDevice?) -> Unit {
            override fun invoke(state: Int, device: BluetoothDevice?) {
                if (device != null) {
                    if (state == BluetoothProfile.STATE_CONNECTED) {
                        connect(device)
                    } else {
                        disconnect(device)
                    }
                }
            }
        }
    }

    private val onPhonebookPullStateChangeListener: OnPhonebookPullStateChangeListener by lazy {
        object : OnPhonebookPullStateChangeListener {
            override fun onPhonebookPullStart(device: BluetoothDevice) {
                handler.post {
                    for (onPhonebookPullStartListener in onPhonebookPullStartListeners) {
                        onPhonebookPullStartListener.invoke(device)
                    }
                }
            }

            override fun onPhonebookPullComplete(device: BluetoothDevice, list: List<BluetoothPhonebook>) {
                handler.post {
                    for (onPhonebookPullFinishListener in onPhonebookPullFinishListeners) {
                        onPhonebookPullFinishListener.invoke(device, list)
                    }
                }
            }
        }
    }

    private val onCallLogPullStateChangeListener: OnCallLogPullStateChangeListener by lazy {
        object : OnCallLogPullStateChangeListener {
            override fun onCallLogPullStart(device: BluetoothDevice) {
                handler.post {
                    for (onCallLogPullStartListener in onCallLogPullStartListeners) {
                        onCallLogPullStartListener.invoke(device)
                    }
                }
            }

            override fun onCallLogPullComplete(device: BluetoothDevice, list: List<BluetoothCallLog>) {
                handler.post {
                    for (onCallLogPullFinishListener in onCallLogPullFinishListeners) {
                        onCallLogPullFinishListener.invoke(device, list)
                    }
                }
            }
        }
    }

    fun init(context: Context) {
        if (CoreController.isHfpClientConnected()) {
            val device = CoreController.getHfpClientConnectedDevice()
            if (device != null) {
                connect(device)
            }
        }
        CoreController.addOnHfpClientConnectStateChangeListener(onHfpClientConnectStateChangeListener)
        PbapClientController.context = context
        val filter = IntentFilter(Intent.ACTION_USER_UNLOCKED)
        try {
            context.registerReceiver(pbapUserUnlockedReceiver, filter)
        } catch (ignored: Exception) {
        }
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        CoreController.removeOnHfpClientConnectStateChangeListener(onHfpClientConnectStateChangeListener)
        onPhonebookPullStartListeners.clear()
        onPhonebookPullFinishListeners.clear()
        onCallLogPullStartListeners.clear()
        onCallLogPullFinishListeners.clear()
        try {
            context.unregisterReceiver(pbapUserUnlockedReceiver)
        } catch (ignored: Exception) {
        }
        for (mutableEntry in pbapClientStateMachineMap) {
            mutableEntry.value.disconnect(mutableEntry.key)
        }
        pbapClientStateMachineMap.clear()
    }

    fun addOnPhonebookPullStartListener(listener: ((BluetoothDevice) -> Unit)) {
        onPhonebookPullStartListeners.add(listener)
    }

    fun removeOnPhonebookPullStartListener(listener: ((BluetoothDevice) -> Unit)) {
        onPhonebookPullStartListeners.remove(listener)
    }

    fun addOnPhonebookPullFinishListener(listener: ((BluetoothDevice, List<BluetoothPhonebook>) -> Unit)) {
        onPhonebookPullFinishListeners.add(listener)
    }

    fun removeOnPhonebookPullFinishListener(listener: ((BluetoothDevice, List<BluetoothPhonebook>) -> Unit)) {
        onPhonebookPullFinishListeners.remove(listener)
    }

    fun addOnCallLogPullStartListener(listener: ((BluetoothDevice) -> Unit)) {
        onCallLogPullStartListeners.add(listener)
    }

    fun removeOnCallLogPullStartListener(listener: ((BluetoothDevice) -> Unit)) {
        onCallLogPullStartListeners.remove(listener)
    }

    fun addOnCallLogPullFinishListener(listener: ((BluetoothDevice, List<BluetoothCallLog>) -> Unit)) {
        onCallLogPullFinishListeners.add(listener)
    }

    fun removeOnCallLogPullFinishListener(listener: ((BluetoothDevice, List<BluetoothCallLog>) -> Unit)) {
        onCallLogPullFinishListeners.remove(listener)
    }

    fun getCallLog(device: BluetoothDevice): List<BluetoothCallLog> {
        return pbapClientStateMachineMap[device]?.callLogList ?: ArrayList(0)
    }

    fun getPhonebook(device: BluetoothDevice): List<BluetoothPhonebook> {
        return pbapClientStateMachineMap[device]?.phoneBookList ?: ArrayList(0)
    }

    fun isCallLogPulling(device: BluetoothDevice): Boolean {
        return pbapClientStateMachineMap[device]?.isCallLogPulling ?: false
    }

    fun isPhonebookPulling(device: BluetoothDevice): Boolean {
        return pbapClientStateMachineMap[device]?.isPhonebookPulling ?: false
    }

    fun pullAll(device: BluetoothDevice) {
        pbapClientStateMachineMap[device]?.pullAll()
    }

    fun pullPhonebook(device: BluetoothDevice) {
        handler.post {
            for (onPhonebookPullStartListener in onPhonebookPullStartListeners) {
                onPhonebookPullStartListener.invoke(device)
            }
        }
        pbapClientStateMachineMap[device]?.pullPhonebook()
    }

    fun pullCallLog(device: BluetoothDevice) {
        handler.post {
            for (onCallLogPullStartListener in onCallLogPullStartListeners) {
                onCallLogPullStartListener.invoke(device)
            }
        }
        pbapClientStateMachineMap[device]?.pullCallLog()
    }

    fun connect(device: BluetoothDevice, autoPull: Boolean = PbapClientController.autoPull): Boolean {
        synchronized(pbapClientStateMachineMap) {
            val get = pbapClientStateMachineMap[device]
            return if (get == null && pbapClientStateMachineMap.size < MAXIMUM_DEVICES) {
                val pbapClientStateMachine = PbapClientStateMachine(context, device,
                        onPhonebookPullStateChangeListener, onCallLogPullStateChangeListener)
                pbapClientStateMachine.setAutoPull(autoPull)
                if (autoPull) {
                    pbapClientStateMachine.start()
                    pbapClientStateMachine.isStart = true
                }
                pbapClientStateMachineMap[device] = pbapClientStateMachine
                true
            } else {
                false
            }
        }
    }

    fun cleanupDevice(device: BluetoothDevice) {
        synchronized(pbapClientStateMachineMap) {
            val pbapClientStateMachine = pbapClientStateMachineMap[device]
            if (pbapClientStateMachine != null) {
                pbapClientStateMachineMap.remove(device)
            }
        }
    }

    fun disconnect(device: BluetoothDevice): Boolean {
        val pbapClientStateMachine = pbapClientStateMachineMap[device]
        return if (pbapClientStateMachine != null) {
            if (pbapClientStateMachine.isStart) {
                pbapClientStateMachine.disconnect(device)
            } else {
                pbapClientStateMachineMap.remove(device)
            }
            for (onPhonebookPullFinishListener in onPhonebookPullFinishListeners) {
                onPhonebookPullFinishListener.invoke(device, ArrayList(0))
            }
            for (onCallLogPullFinishListener in onCallLogPullFinishListeners) {
                onCallLogPullFinishListener.invoke(device, ArrayList(0))
            }
            true
        } else {
            false
        }
    }
}