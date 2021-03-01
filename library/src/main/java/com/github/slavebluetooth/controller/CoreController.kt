package com.github.slavebluetooth.controller

import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import com.android.settingslib.bluetooth.*

object CoreController : BluetoothCallback {

    /**
     * 蓝牙的Profile发生连接状态变化后发送整体是否还有profile相连
     */
    const val ACTION_BLUETOOTH_PROFILE_STATE_CHANGE = "android.bluetooth.profile.action.CONNECTION_STATE_CHANGED"
    const val BLUETOOTH_PIN_MAX_LENGTH = 16
    const val BLUETOOTH_PASSKEY_MAX_LENGTH = 6
    const val PAIRING_POPUP_TIMEOUT = 60000L

    private lateinit var context: Context

    private val localManagerLazy = lazy { LocalBluetoothManager.getInstance(context, null) }
    val localManager: LocalBluetoothManager by localManagerLazy
    val localAdapter: LocalBluetoothAdapter by lazy { localManager.bluetoothAdapter }
    val cachedDeviceManager: CachedBluetoothDeviceManager by lazy { localManager.cachedDeviceManager }
    private val profileManager: LocalBluetoothProfileManager by lazy { localManager.profileManager }

    private val a2dpSinkProfileLazy = lazy { profileManager.a2dpSinkProfile }
    private val hfpClientProfileLazy = lazy { profileManager.hfpClientProfile }
    private val a2dpSinkProfile: A2dpSinkProfile by a2dpSinkProfileLazy
    val hfpClientProfile: HfpClientProfile by hfpClientProfileLazy

    private var onLocalNameChangeListener: (() -> Unit)? = null
    private val onHfpClientProfileConnectStateChangeListeners = ArrayList<(Int, BluetoothDevice?) -> Unit>()
    private val onA2dpSinkProfileConnectStateChangeListeners = ArrayList<(Int, BluetoothDevice?) -> Unit>()
    var isOnBonding = false

    var bluetoothState = BluetoothAdapter.STATE_OFF

    private val localNameChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED == intent.action) {
                onLocalNameChangeListener?.invoke()
            }
        }
    }

    private var needConnectDevices: CachedBluetoothDevice? = null
    private val a2dpSinkStateChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (BluetoothA2dpSink.ACTION_CONNECTION_STATE_CHANGED == intent.action) {
                val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                if (state == BluetoothProfile.STATE_DISCONNECTED) {
                    needConnectDevices?.connect(false)
                    needConnectDevices = null
                }
                onA2dpSinkStateChange(state, device)
            }
        }
    }

    private val hfpClientStateChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            if (BluetoothHeadsetClient.ACTION_CONNECTION_STATE_CHANGED == intent.action) {
                val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                if (state == BluetoothProfile.STATE_CONNECTED) {
                    val connectedDevices = hfpClientProfile.connectedDevices
                    for (connectedDevice in connectedDevices) {
                        if (connectedDevice != device) {
                            val findDevice = cachedDeviceManager.findDevice(connectedDevice)
                            findDevice.disconnect()
                            needConnectDevices = cachedDeviceManager.findDevice(device)
                        }
                    }
                }
                onHfpClientStateChange(state, device)
            }
        }
    }

    private fun onA2dpSinkStateChange(state: Int, device: BluetoothDevice?) {
        if (state == BluetoothProfile.STATE_CONNECTED) {
            setA2dpSinkPreferred()
        }
        synchronized(onA2dpSinkProfileConnectStateChangeListeners) {
            for (onConnectStateChangeListener in onA2dpSinkProfileConnectStateChangeListeners) {
                onConnectStateChangeListener.invoke(state, device)
            }
        }
        sendAllProfileConnectStateChangeReceiver()
    }

    private fun onHfpClientStateChange(state: Int, device: BluetoothDevice?) {
        if (state == BluetoothProfile.STATE_CONNECTED) {
            setHfpClientPreferred()
        }
        synchronized(onHfpClientProfileConnectStateChangeListeners) {
            for (onConnectStateChangeListener in onHfpClientProfileConnectStateChangeListeners) {
                onConnectStateChangeListener.invoke(state, device)
            }
        }
        sendAllProfileConnectStateChangeReceiver()
    }

    fun init(context: Context) {
        CoreController.context = context
        Utils.setErrorListener { theContext, name, messageResId ->
            val message: String = theContext.getString(messageResId, name)
            Toast.makeText(theContext, message, Toast.LENGTH_SHORT).show()
        }

        val localNameChangeFilter = IntentFilter(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED)
        context.registerReceiver(localNameChangeReceiver, localNameChangeFilter)

        bluetoothState = localAdapter.state
        localManager.eventManager.registerCallback(this)

        if (profileManager.isA2dpSkinProfilesInitialized) {
            onA2dpSinkInitialized()
        } else {
            profileManager.setOnA2dpSinkProfileInitListener {
                onA2dpSinkInitialized()
            }
        }

        if (profileManager.isHfpClientProfilesInitialized) {
            onHfpClientInitialized()
        } else {
            profileManager.setOnHfpClientProfileInitListener {
                onHfpClientInitialized()
            }
        }
    }

    private fun onA2dpSinkInitialized() {
        if (a2dpSinkProfile.isProfileReady) {
            onA2dpSinkProfileReady()
        } else {
            a2dpSinkProfile.setOnA2dpSinkProfileReadyListener {
                onA2dpSinkProfileReady()
            }
        }
    }

    private fun onHfpClientInitialized() {
        if (hfpClientProfile.isProfileReady) {
            onHfpClientProfileReady()
        } else {
            hfpClientProfile.setOnHfpClientProfileReadyListener {
                onHfpClientProfileReady()
            }
        }
    }

    private var hasInit = false

    private fun onA2dpSinkProfileReady() {
        if (hfpClientProfileLazy.isInitialized() && !hasInit) {
            hasInit = true
            sendAllProfileConnectStateChangeReceiver()
            setHfpClientPreferred()
            setA2dpSinkPreferred()
        }
        val a2dpSinkStateFilter = IntentFilter()
        a2dpSinkStateFilter.addAction(BluetoothA2dpSink.ACTION_CONNECTION_STATE_CHANGED)
        context.registerReceiver(a2dpSinkStateChangeReceiver, a2dpSinkStateFilter)
        onA2dpSinkStateChange(a2dpSinkProfile.connectionStatus, a2dpSinkProfile.connectedDevice)
    }

    private fun onHfpClientProfileReady() {
        if (a2dpSinkProfileLazy.isInitialized() && !hasInit) {
            hasInit = true
            sendAllProfileConnectStateChangeReceiver()
            setHfpClientPreferred()
            setA2dpSinkPreferred()
        }
        val hfpClientStateFilter = IntentFilter()
        hfpClientStateFilter.addAction(BluetoothHeadsetClient.ACTION_CONNECTION_STATE_CHANGED)
        context.registerReceiver(hfpClientStateChangeReceiver, hfpClientStateFilter)
        onHfpClientStateChange(hfpClientProfile.connectionStatus, hfpClientProfile.connectedDevice)
    }

    private fun setHfpClientPreferred() {
        val connectedDevices = hfpClientProfile.connectedDevices
        if (connectedDevices.isNotEmpty()) {
            for (connectedDevice in connectedDevices) {
                if (!hfpClientProfile.isPreferred(connectedDevice)) {
                    hfpClientProfile.setPreferred(connectedDevice, true)
                }
            }
        }
    }

    private fun setA2dpSinkPreferred() {
        val connectedDevices = a2dpSinkProfile.connectedDevices
        if (connectedDevices.isNotEmpty()) {
            for (connectedDevice in connectedDevices) {
                if (!a2dpSinkProfile.isPreferred(connectedDevice)) {
                    a2dpSinkProfile.setPreferred(connectedDevice, true)
                }
            }
        }
    }

    private fun sendAllProfileConnectStateChangeReceiver() {
        val intent = Intent(ACTION_BLUETOOTH_PROFILE_STATE_CHANGE)
        if (isA2dpSinkConnected() || isHfpClientConnected()) {
            intent.putExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_CONNECTED)
        } else {
            intent.putExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
        }
        context.sendBroadcast(intent)
    }

    fun isA2dpSinkConnected() = localAdapter.isEnabled && a2dpSinkProfileLazy.isInitialized()
            && a2dpSinkProfile.isConnected

    fun isHfpClientConnected() = localAdapter.isEnabled && hfpClientProfileLazy.isInitialized()
            && hfpClientProfile.isConnected

    fun getA2dpSinkConnectedState() = if (localAdapter.isEnabled && a2dpSinkProfileLazy.isInitialized()) {
        a2dpSinkProfile.connectionStatus
    } else {
        BluetoothProfile.STATE_DISCONNECTED
    }

    fun getHfpClientConnectedState() = if (localAdapter.isEnabled && hfpClientProfileLazy.isInitialized()) {
        hfpClientProfile.connectionStatus
    } else {
        BluetoothProfile.STATE_DISCONNECTED
    }

    fun getHfpClientConnectedDevice(): BluetoothDevice? {
        return if (isHfpClientConnected()) {
            hfpClientProfile.connectedDevice
        } else {
            null
        }
    }

    fun addOnHfpClientConnectStateChangeListener(listener: (Int, BluetoothDevice?) -> Unit) {
        if (hfpClientProfileLazy.isInitialized()) {
            listener.invoke(hfpClientProfile.connectionStatus, hfpClientProfile.connectedDevice)
        }
        onHfpClientProfileConnectStateChangeListeners.add(listener)
    }

    fun removeOnHfpClientConnectStateChangeListener(listener: (Int, BluetoothDevice?) -> Unit) {
        onHfpClientProfileConnectStateChangeListeners.remove(listener)
    }

    fun addOnA2dpSinkConnectStateChangeListener(listener: (Int, BluetoothDevice?) -> Unit) {
        if (a2dpSinkProfileLazy.isInitialized()) {
            listener.invoke(a2dpSinkProfile.connectionStatus, a2dpSinkProfile.connectedDevice)
        }
        onA2dpSinkProfileConnectStateChangeListeners.add(listener)
    }

    fun removeOnA2dpSinkConnectStateChangeListener(listener: (Int, BluetoothDevice?) -> Unit) {
        onA2dpSinkProfileConnectStateChangeListeners.remove(listener)
    }

    fun setOnLocalNameChangeListener(listener: (() -> Unit)?) {
        onLocalNameChangeListener = listener
    }

    fun release() {
        Utils.setErrorListener(null)
        if (hfpClientProfileLazy.isInitialized()) {
            hfpClientProfile.setOnHfpClientProfileReadyListener(null)
            hfpClientProfile.finalize()
        }
        if (a2dpSinkProfileLazy.isInitialized()) {
            a2dpSinkProfile.setOnA2dpSinkProfileReadyListener(null)
            a2dpSinkProfile.finalize()
        }
        profileManager.setOnA2dpSinkProfileInitListener(null)
        profileManager.setOnHfpClientProfileInitListener(null)
        onA2dpSinkProfileConnectStateChangeListeners.clear()
        onHfpClientProfileConnectStateChangeListeners.clear()
        onLocalNameChangeListener = null
        if (localManagerLazy.isInitialized()) {
            localManager.eventManager.unregisterCallback(this)
        }
        context.unregisterReceiver(localNameChangeReceiver)
        context.unregisterReceiver(a2dpSinkStateChangeReceiver)
        context.unregisterReceiver(hfpClientStateChangeReceiver)
    }

    override fun onBluetoothStateChanged(bluetoothState: Int) {
        CoreController.bluetoothState = bluetoothState
        when (bluetoothState) {
            BluetoothAdapter.STATE_TURNING_OFF -> {
                onA2dpSinkStateChange(BluetoothProfile.STATE_DISCONNECTED, null)
                onHfpClientStateChange(BluetoothProfile.STATE_DISCONNECTED, null)
            }
        }
    }

    override fun onDeviceBondStateChanged(cachedDevice: CachedBluetoothDevice, bondState: Int) {
        isOnBonding = when (bondState) {
            BluetoothDevice.BOND_BONDING -> true
            BluetoothDevice.BOND_BONDED -> false
            BluetoothDevice.BOND_NONE -> false
            else -> false
        }
    }

    override fun onScanningStateChanged(started: Boolean) = Unit
    override fun onDeviceDeleted(cachedDevice: CachedBluetoothDevice) = Unit
    override fun onDeviceAdded(cachedDevice: CachedBluetoothDevice) = Unit
    override fun onPairingRequestNeedInput(cachedDevice: CachedBluetoothDevice, type: Int) = Unit
    override fun onPairingCancel(cachedDevice: CachedBluetoothDevice) = Unit
    override fun onActiveDeviceChanged(activeDevice: CachedBluetoothDevice?, bluetoothProfile: Int) = Unit
    override fun onAudioModeChanged() = Unit
    override fun onConnectionStateChanged(cachedDevice: CachedBluetoothDevice?, state: Int) = Unit

}