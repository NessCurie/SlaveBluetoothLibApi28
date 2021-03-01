package com.github.slavebluetooth.controller.set

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.view.View
import android.widget.CompoundButton
import android.widget.TextView
import com.android.settingslib.bluetooth.BluetoothCallback
import com.android.settingslib.bluetooth.CachedBluetoothDevice
import com.android.settingslib.bluetooth.LocalBluetoothAdapter
import com.android.settingslib.bluetooth.LocalBluetoothManager
import com.github.slavebluetooth.controller.CoreController
import com.github.slavebluetooth.controller.pbapclient.PbapClientController

/**
 * 提供了一套比较通用的蓝牙设置的逻辑实现,需要将界面控件传递进来,如果界面控件不符合传递的方法需要模仿内容进行自己的实现
 */
class SetControlHelper(private val context: Context) : BluetoothCallback {

    companion object {
        const val SP_AUTO_PULL_PHONE_BOOK_KEY = "SP_AUTO_PULL_PHONE_BOOK_KEY"
        const val SP_AUTO_ACCEPT_IN_COMING_CALL_KEY = "SP_AUTO_ACCEPT_IN_COMING_CALL_KEY"
        const val SP_AUTO_CONNECT_KEY = "SP_AUTO_ACCEPT_IN_COMING_CALL_KEY"
    }

    private val localManager: LocalBluetoothManager by lazy { CoreController.localManager }
    private val localAdapter: LocalBluetoothAdapter by lazy { CoreController.localAdapter }
    private val sp: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(context) }

    private var btNameSetView: View? = null
    private var openDeviceListInterfaceView: View? = null

    private var btSwitch: CompoundButton? = null
    private var switchAutoSyncPhone: CompoundButton? = null
    private var switchAutoAnswer: CompoundButton? = null
    private var switchAutoConnect: CompoundButton? = null

    private var onBluetoothStateChangeListener: ((bluetoothState: Int) -> Unit)? = null

    /**
     * @param btNameSetView 只是控制isEnabled ,点击事件比如弹框还是需要自己设置
     */
    fun setNameView(btNameShowView: TextView, btNameSetView: View): SetControlHelper {
        this.btNameSetView = btNameSetView
        btNameShowView.text = localAdapter.name
        CoreController.setOnLocalNameChangeListener {
            btNameShowView.text = localAdapter.name
        }
        return this
    }

    fun setBluetoothName(name: String) {
        localAdapter.name = name
    }

    fun setOpenDeviceListInterfaceView(openDeviceListInterfaceView: View): SetControlHelper {
        this.openDeviceListInterfaceView = openDeviceListInterfaceView
        return this
    }

    fun setBtSwitchView(switchBt: CompoundButton): SetControlHelper {
        this.btSwitch = switchBt
        switchBt.isChecked = localAdapter.isEnabled
        switchBt.setOnCheckedChangeListener { buttonView, isChecked ->
            if (!buttonView.isPressed) return@setOnCheckedChangeListener
            val status = localAdapter.setBluetoothEnabled(isChecked)
            if (isChecked && !status) {
                buttonView.isChecked = false
                buttonView.isEnabled = true
                return@setOnCheckedChangeListener
            } else if (isChecked) {
                onBluetoothStateChangeListener?.invoke(BluetoothAdapter.STATE_TURNING_ON)
            }
            buttonView.isEnabled = false
        }
        return this
    }

    fun setBtAutoSyncPhone(switchAutoSyncPhone: CompoundButton): SetControlHelper {
        this.switchAutoSyncPhone = switchAutoSyncPhone
        switchAutoSyncPhone.isChecked = sp.getBoolean(SP_AUTO_PULL_PHONE_BOOK_KEY, false)
        switchAutoSyncPhone.setOnCheckedChangeListener { buttonView, isChecked ->
            if (!buttonView.isPressed) return@setOnCheckedChangeListener
            sp.edit().putBoolean(SP_AUTO_PULL_PHONE_BOOK_KEY, isChecked).apply()
            if (isChecked) {
                val device = CoreController.getHfpClientConnectedDevice()
                device?.run {
                    if (PbapClientController.getPhonebook(device).isEmpty()
                            && PbapClientController.getCallLog(device).isEmpty()) {
                        PbapClientController.pullAll(device)
                    }
                }
            }
        }
        return this
    }

    fun setBtAutoAnswer(switchAutoAnswer: CompoundButton): SetControlHelper {
        this.switchAutoAnswer = switchAutoAnswer
        switchAutoAnswer.isChecked = sp.getBoolean(SP_AUTO_ACCEPT_IN_COMING_CALL_KEY, false)
        switchAutoAnswer.setOnCheckedChangeListener { buttonView, isChecked ->
            if (!buttonView.isPressed) return@setOnCheckedChangeListener
            sp.edit().putBoolean(SP_AUTO_ACCEPT_IN_COMING_CALL_KEY, isChecked).apply()
        }
        return this
    }

    fun setBtAutoConnectView(switchAutoConnect: CompoundButton): SetControlHelper {
        this.switchAutoConnect = switchAutoConnect
        switchAutoConnect.isChecked = sp.getBoolean(SP_AUTO_CONNECT_KEY, false)
        switchAutoConnect.setOnCheckedChangeListener { _, isChecked ->
            sp.edit().putBoolean(SP_AUTO_CONNECT_KEY, isChecked).apply()
            //TODO 接口还未找到
        }
        return this
    }

    fun setOnBluetoothStateChangedListener(listener: ((bluetoothState: Int) -> Unit)?): SetControlHelper {
        onBluetoothStateChangeListener = listener
        return this
    }

    fun init() {
        onBluetoothStateChanged(localAdapter.bluetoothState)
        localManager.eventManager.registerCallback(this)
    }

    private fun onBluetoothOn() {
        switchAutoSyncPhone?.isEnabled = true
        switchAutoAnswer?.isEnabled = true
        switchAutoConnect?.isEnabled = true
        btNameSetView?.isEnabled = true
        openDeviceListInterfaceView?.isEnabled = true
    }

    private fun onBluetoothOff() {
        switchAutoSyncPhone?.isEnabled = false
        switchAutoAnswer?.isEnabled = false
        switchAutoConnect?.isEnabled = false
        btNameSetView?.isEnabled = false
        openDeviceListInterfaceView?.isEnabled = false
    }

    override fun onBluetoothStateChanged(bluetoothState: Int) {
        onBluetoothStateChangeListener?.invoke(bluetoothState)
        when (bluetoothState) {
            BluetoothAdapter.STATE_ON -> {
                btSwitch?.run {
                    isEnabled = true
                    isChecked = true
                }
                localAdapter.scanMode = BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE
                onBluetoothOn()
                return
            }
            BluetoothAdapter.STATE_TURNING_ON -> {
                btSwitch?.run {
                    isEnabled = false
                    isChecked = true
                }
            }
            BluetoothAdapter.STATE_TURNING_OFF -> {
                btSwitch?.run {
                    isEnabled = false
                    isChecked = false
                }
                onBluetoothOff()
            }
            BluetoothAdapter.STATE_OFF -> {
                btSwitch?.run {
                    isEnabled = true
                    isChecked = false
                }
                onBluetoothOff()
            }
        }
        localAdapter.stopScanning()
    }

    override fun onScanningStateChanged(started: Boolean) = Unit

    override fun onDeviceDeleted(cachedDevice: CachedBluetoothDevice?) = Unit

    override fun onDeviceAdded(cachedDevice: CachedBluetoothDevice?) = Unit

    override fun onPairingRequestNeedInput(cachedDevice: CachedBluetoothDevice?, type: Int) = Unit

    override fun onPairingCancel(cachedDevice: CachedBluetoothDevice?) = Unit

    override fun onConnectionStateChanged(cachedDevice: CachedBluetoothDevice?, state: Int) = Unit

    override fun onActiveDeviceChanged(activeDevice: CachedBluetoothDevice?, bluetoothProfile: Int) = Unit
    override fun onAudioModeChanged() = Unit

    override fun onDeviceBondStateChanged(cachedDevice: CachedBluetoothDevice?, bondState: Int) = Unit

    fun release() {
        CoreController.setOnLocalNameChangeListener(null)
        localManager.eventManager.unregisterCallback(this)
    }
}