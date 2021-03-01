package com.github.slavebluetooth.controller.set

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Dialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.android.settingslib.bluetooth.*
import com.github.recyclerviewutils.HFRefreshLayout
import com.github.recyclerviewutils.MViewHolder
import com.github.recyclerviewutils.SimpleAdapter
import com.github.slavebluetooth.R
import com.github.slavebluetooth.controller.CoreController

/**
 * 不显示已配对设备列表,把已配对设备列表和可用设备列表合一的设备列表
 */
class DeviceSingleListHelper(private val context: Context) : BluetoothCallback {

    private val localManager: LocalBluetoothManager by lazy { CoreController.localManager }
    private val localAdapter: LocalBluetoothAdapter by lazy { CoreController.localAdapter }
    private val deviceList: ArrayList<CachedBluetoothDevice> = ArrayList()
    private val handler = Handler()
    private var initialScanStarted = false

    private var dialogView: View? = null
    private var tvHint1: TextView? = null
    private var tvHint2: TextView? = null
    private var inputView: EditText? = null
    private var confirmView: View? = null

    private val dialogLazy = lazy {
        val dialog = Dialog(context, R.style.Dialog)
        dialog.setContentView(dialogView!!)
        @Suppress("DEPRECATION")
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
        dialog
    }
    private val dialog: Dialog by dialogLazy
    private var onPairingNeedInputDialogShowListener: ((dialog: Dialog) -> Unit)? = null

    private var deviceItemLayoutId = 0
    private var itemNameShowViewId = 0
    private var itemStateShowViewId = 0
    private var connectedDrawable: Drawable? = null
    private var disconnectDrawable: Drawable? = null
    private var connectedColor = 0
    private var disconnectedColor = 0
    private var deviceItemLineId = -1
    private var deviceItemLineDrawable: Drawable? = null

    private var refreshView: View? = null
    private var noFindDeviceHintView: View? = null
    private val animator: ObjectAnimator by lazy {
        @SuppressLint("ObjectAnimatorBinding")
        val rotationAnimator = ObjectAnimator.ofFloat(refreshView, "rotation", 0f, 360f)
        rotationAnimator.duration = 2000
        rotationAnimator.interpolator = LinearInterpolator()
        rotationAnimator.repeatCount = ValueAnimator.INFINITE
        rotationAnimator
    }

    private var refreshLayout: HFRefreshLayout? = null

    private var devicesRecyclerView: RecyclerView? = null
    private val deviceAdapter: SimpleAdapter<CachedBluetoothDevice> by lazy {
        object : SimpleAdapter<CachedBluetoothDevice>(context, deviceItemLayoutId, deviceList) {
            override fun setItemData(holder: MViewHolder, device: CachedBluetoothDevice, position: Int) {
                refreshItem(holder, device)
                device.registerCallback {
                    if (device.isConnected || device.isConnecting) {
                        if (deviceList.indexOf(device) != 0) {
                            deviceList.remove(device)
                            deviceList.add(0, device)
                            deviceAdapter.notifyDataSetChanged()
                        }
                    }
                    refreshItem(holder, device)
                }
            }
        }
    }

    //断开a2dpSink有延迟,需要在回调中处理,这样下一个设备才能立即连接上a2dpSink,这样可以防止遍历中移除的问题.
    private var needConnectDevices: CachedBluetoothDevice? = null
    private val onA2dpSinkConnectStateChangeListener: (Int, BluetoothDevice?) -> Unit = { state, device ->
        if (state == BluetoothProfile.STATE_DISCONNECTED) {
            needConnectDevices?.connect(false)
            needConnectDevices = null
        }
    }

    /**
     * 源码中处理了正常pin进行连接的情况,不需要进行pin输入,但是源码中显示的有额外几种情况可能需要进行输入
     * 但是目前还未有遇到出现过,也没有办法主动触发这些内容,但是也需要进行处理
     * dialog需要至少2个提示显示view和一个editText输入框,确定按钮.
     * 不设置出现这些情况就不弹框,可能造成无法配对
     */
    fun setOnPairingNeedInputDialogView(dialogView: View, tvHint1: TextView, tvHint2: TextView,
                                        inputView: EditText, confirmView: View): DeviceSingleListHelper {
        this.dialogView = dialogView
        this.tvHint1 = tvHint1
        this.tvHint2 = tvHint2
        this.inputView = inputView
        this.confirmView = confirmView
        return this
    }

    /**
     * 和setOnPairingNeedInputDialogView呼应
     */
    fun setOnPairingNeedInputDialogShowListener(listener: ((dialog: Dialog) -> Unit)?): DeviceSingleListHelper {
        onPairingNeedInputDialogShowListener = listener
        return this
    }

    /**
     * 下拉刷新式的刷新
     */
    fun setRefreshView(refreshLayout: HFRefreshLayout, listEmptyView: View,
                       refreshNormalStateHintRes: Int = 0,
                       refreshNormalStateReadyRes: Int = 0,
                       refreshNormalStateRefreshingRes: Int = 0,
                       refreshNormalStateSuccessRes: Int = 0): DeviceSingleListHelper {
        this.refreshLayout = refreshLayout
        refreshLayout.run {
            setPullLoadEnable(false)
            emptyView = listEmptyView
            if (refreshNormalStateHintRes != 0) {
                setRefreshOnStateNormalHint(context.getString(refreshNormalStateHintRes))
            }
            if (refreshNormalStateReadyRes != 0) {
                setRefreshOnStateReadyHint(context.getString(refreshNormalStateReadyRes))
            }
            if (refreshNormalStateRefreshingRes != 0) {
                setRefreshOnStateRefreshingHint(context.getString(refreshNormalStateRefreshingRes))
            }
            if (refreshNormalStateSuccessRes != 0) {
                setRefreshOnStateSuccessHint(context.getString(refreshNormalStateSuccessRes))
            }
            setOnRefreshListener {
                startScanning()
            }
        }
        return this
    }

    /**
     * 点击按钮刷新式的刷新
     */
    fun setRefreshView(view: View, noFindDeviceHintView: View): DeviceSingleListHelper {
        this.refreshView = view
        this.noFindDeviceHintView = noFindDeviceHintView
        view.setOnClickListener {
            if (!localAdapter.isDiscovering && localAdapter.bluetoothState == BluetoothAdapter.STATE_ON
                    && !CoreController.isOnBonding) {
                startScanning()
                view.isClickable = false
            }
        }
        return this
    }

    fun setDevicesListView(devicesRecyclerView: RecyclerView, deviceItemLayoutId: Int): DeviceSingleListHelper {
        this.devicesRecyclerView = devicesRecyclerView
        this.deviceItemLayoutId = deviceItemLayoutId
        devicesRecyclerView.run {
            layoutManager = LinearLayoutManager(context)
            adapter = deviceAdapter
        }
        deviceAdapter.setOnItemClickListener { _, _, position ->
            localAdapter.stopScanning()
            val device = deviceList[position]
            //有些模块蓝牙连接中断开偶尔会出现只连上音乐过会连上电话
            //还有些模块基本100%出现连接中发起断开会继续连上音乐再过很久连上电话
            if (device.isConnecting || device.isDisConnecting) {
                Toast.makeText(context, R.string.device_busy_hint, Toast.LENGTH_SHORT).show()
                return@setOnItemClickListener
            }
            if (device.isConnected) {
                device.disconnect()
            } else {
                //有些模块其他在连接和断开中不让操作其他设备,否则会异常
                /*for (cachedBluetoothDevice in deviceList) {
                    if (cachedBluetoothDevice.isConnecting || cachedBluetoothDevice.isDisConnecting) {
                        Toast.makeText(context, R.string.goodcom_device_busy_hint, Toast.LENGTH_SHORT).show()
                        return@setOnItemClickListener
                    }
                }*/
                when (device.bondState) {
                    BluetoothDevice.BOND_BONDED -> {
                        disconnectOtherDevice(device)
                    }
                    BluetoothDevice.BOND_NONE -> {
                        device.startPairing()
                        refreshLayout?.setPullRefreshEnable(false)
                    }
                    BluetoothDevice.BOND_BONDING -> {
                        device.unpair()
                    }
                }
            }
        }
        return this
    }

    private fun disconnectOtherDevice(device: CachedBluetoothDevice) {
        var isHaveConnectDevice = false
        for (cachedBluetoothDevice in deviceList) {
            isHaveConnectDevice = cachedBluetoothDevice.isConnected || cachedBluetoothDevice.isConnecting
            if (isHaveConnectDevice && device != cachedBluetoothDevice) {
                needConnectDevices = device
                cachedBluetoothDevice.disconnect()
            }
        }
        if (!isHaveConnectDevice) {
            device.connect(false)
        }
    }

    fun setDevicesListItem(itemNameShowViewId: Int, itemStateShowViewId: Int,
                           connectedDrawable: Drawable?, disconnectDrawable: Drawable?,
                           connectedColor: Int, disconnectedColor: Int,
                           lineId: Int = -1, lineDrawable: Drawable? = null): DeviceSingleListHelper {
        this.itemNameShowViewId = itemNameShowViewId
        this.itemStateShowViewId = itemStateShowViewId
        this.connectedDrawable = connectedDrawable
        this.disconnectDrawable = disconnectDrawable
        if (connectedDrawable != null && disconnectDrawable != null) {
            connectedDrawable.setBounds(0, 0, connectedDrawable.minimumWidth, connectedDrawable.minimumHeight)
            disconnectDrawable.setBounds(0, 0, disconnectDrawable.minimumWidth, disconnectDrawable.minimumHeight)
        }
        this.connectedColor = connectedColor
        this.disconnectedColor = disconnectedColor
        this.deviceItemLineId = lineId
        this.deviceItemLineDrawable = lineDrawable
        return this
    }

    fun init() {
        /*deviceAdapter.setOnItemLongClickListener { _, _, position ->
            localAdapter.stopScanning()
            val device = deviceList[position]
            device.unpair()
            true
        }*/
        if (localAdapter.isDiscovering) {
            localAdapter.stopScanning()
        }
        localManager.eventManager.registerCallback(this)
        val refreshLayout = refreshLayout
        if (refreshLayout != null) {
            refreshLayout.run {
                if (isInitialized) {
                    onBluetoothStateChanged(localAdapter.bluetoothState)
                } else {
                    setOnInitializedListener {
                        onBluetoothStateChanged(localAdapter.bluetoothState)
                    }
                }
            }
        } else {
            onBluetoothStateChanged(localAdapter.bluetoothState)
        }
        CoreController.addOnA2dpSinkConnectStateChangeListener(onA2dpSinkConnectStateChangeListener)
    }

    fun onRestart() {
        if (localAdapter.isDiscovering) {
            localAdapter.stopScanning()
        }
        CoreController.addOnA2dpSinkConnectStateChangeListener(onA2dpSinkConnectStateChangeListener)
        localManager.eventManager.registerCallback(this)
        onBluetoothStateChanged(localAdapter.bluetoothState)
    }

    fun onStop() {
        release()
    }

    private fun refreshItem(holder: MViewHolder, device: CachedBluetoothDevice) {
        val tvState = holder.getView<TextView>(itemStateShowViewId)
        val tvName = holder.getView<TextView>(itemNameShowViewId)
        tvName.text = device.name
        tvState.text = device.carConnectionSummary
        if (deviceItemLineId != -1) {
            holder.setBackground(deviceItemLineId, deviceItemLineDrawable)
        }

        if (device.a2dpOrHfpHasConnected) {
            tvName.setTextColor(connectedColor)
            tvState.setTextColor(connectedColor)
            tvState.setCompoundDrawables(null, null, connectedDrawable, null)
        } else {
            tvName.setTextColor(disconnectedColor)
            tvState.setTextColor(disconnectedColor)
            tvState.setCompoundDrawables(null, null, disconnectDrawable, null)
        }
    }

    private fun release() {
        CoreController.removeOnA2dpSinkConnectStateChangeListener(onA2dpSinkConnectStateChangeListener)
        onPairingNeedInputDialogShowListener = null
        localAdapter.stopScanning()
        initialScanStarted = false
        for (cachedBluetoothDevice in deviceList) {
            cachedBluetoothDevice.clearCallback()
        }
        localManager.eventManager.unregisterCallback(this)
        if (dialogLazy.isInitialized() && dialog.isShowing) {
            dialog.dismiss()
        }
    }

    private fun startScanning() {
        localManager.cachedDeviceManager.clearNotConnectDevices()
        for (cachedBluetoothDevice in deviceList) {
            cachedBluetoothDevice.clearCallback()
        }
        deviceList.clear()
        for (cachedBluetoothDevice in localManager.cachedDeviceManager.cachedDevicesCopy) {
            if ((cachedBluetoothDevice.isConnected || cachedBluetoothDevice.isConnecting
                            || cachedBluetoothDevice.bondState == BluetoothDevice.BOND_BONDING)
                    && !deviceList.contains(cachedBluetoothDevice)) {
                deviceList.add(0, cachedBluetoothDevice)
                deviceAdapter.notifyDataSetChanged()
                break
            }
        }
        deviceAdapter.notifyDataSetChanged()
        localAdapter.startScanning(true)
        initialScanStarted = true
    }

    override fun onBluetoothStateChanged(bluetoothState: Int) {
        when (bluetoothState) {
            BluetoothAdapter.STATE_ON -> {
                if (initialScanStarted) {
                    for (cachedBluetoothDevice in deviceList) {
                        cachedBluetoothDevice.clearCallback()
                    }
                    deviceList.clear()
                    deviceAdapter.notifyDataSetChanged()
                }
                if (!initialScanStarted && !CoreController.isOnBonding) {
                    if (refreshLayout != null) {
                        refreshLayout?.isRefreshing = true
                    } else {
                        startScanning()
                    }
                } else {
                    val cachedDevices = localManager.cachedDeviceManager.cachedDevicesCopy
                    for (cachedDevice in cachedDevices) {
                        onDeviceAdded(cachedDevice)
                    }
                }
                return
            }
            BluetoothAdapter.STATE_TURNING_ON, BluetoothAdapter.STATE_TURNING_OFF,
            BluetoothAdapter.STATE_OFF -> {
                initialScanStarted = false
            }
        }
        localAdapter.stopScanning()
        for (cachedBluetoothDevice in deviceList) {
            cachedBluetoothDevice.clearCallback()
        }
        deviceList.clear()
        deviceAdapter.notifyDataSetChanged()
    }

    override fun onScanningStateChanged(started: Boolean) {
        initialScanStarted = started
        if (started) {
            refreshView?.run {
                animator.start()
                isClickable = false
            }
            noFindDeviceHintView?.visibility = View.GONE
            devicesRecyclerView?.visibility = View.VISIBLE
        } else {
            refreshView?.run {
                animator.cancel()
                isClickable = true
            }
            refreshLayout?.onRefreshFinished(1000)
            if (deviceList.isEmpty()) {
                refreshLayout?.showEmptyView()
                noFindDeviceHintView?.visibility = View.VISIBLE
                devicesRecyclerView?.visibility = View.GONE
            }
        }
    }

    override fun onDeviceAdded(cachedDevice: CachedBluetoothDevice) {
        if (localAdapter.bluetoothState != BluetoothAdapter.STATE_ON) return
        if (!cachedDevice.btClass.doesClassMatch(BluetoothClass.PROFILE_A2DP_SINK)) return
        if (!deviceList.contains(cachedDevice)) {
            deviceList.add(cachedDevice)
            deviceList.sort()
            refreshLayout?.hideEmptyView()
            deviceAdapter.notifyDataSetChanged()
        }
    }

    override fun onDeviceDeleted(cachedDevice: CachedBluetoothDevice) {
        if (deviceList.remove(cachedDevice)) {
            cachedDevice.clearCallback()
            deviceAdapter.notifyDataSetChanged()
        }
    }

    override fun onConnectionStateChanged(cachedDevice: CachedBluetoothDevice?, state: Int) = Unit
    override fun onActiveDeviceChanged(activeDevice: CachedBluetoothDevice?, bluetoothProfile: Int) = Unit
    override fun onAudioModeChanged() = Unit

    private val hidePairingRequestInput = Runnable {
        if (dialogLazy.isInitialized() && dialog.isShowing) dialog.dismiss()
    }

    override fun onDeviceBondStateChanged(cachedDevice: CachedBluetoothDevice, bondState: Int) {
        when (bondState) {
            BluetoothDevice.BOND_BONDING -> {
                if (deviceList.contains(cachedDevice)) {
                    deviceList.remove(cachedDevice)
                }
                deviceList.add(0, cachedDevice)
                deviceAdapter.notifyDataSetChanged()
            }
            BluetoothDevice.BOND_BONDED -> {
                if (deviceList.indexOf(cachedDevice) != 0) {
                    deviceList.remove(cachedDevice)
                    deviceList.add(0, cachedDevice)
                }
                disconnectOtherDevice(cachedDevice)
                deviceAdapter.notifyDataSetChanged()
            }
            BluetoothDevice.BOND_NONE -> {
            }
        }
        if (bondState == BluetoothDevice.BOND_BONDED || bondState == BluetoothDevice.BOND_NONE) {
            refreshLayout?.setPullRefreshEnable(true)
            handler.post(hidePairingRequestInput)
        }
    }

    override fun onPairingRequestNeedInput(cachedDevice: CachedBluetoothDevice, type: Int) {
        val messageId: Int
        var messageIdHint = R.string.bluetooth_pin_values_hint
        val maxLength: Int
        when (type) {
            BluetoothDevice.PAIRING_VARIANT_PIN, BluetoothDevice.PAIRING_VARIANT_PIN_16_DIGITS -> {
                if (type == BluetoothDevice.PAIRING_VARIANT_PIN_16_DIGITS) {
                    messageIdHint = R.string.bluetooth_pin_values_hint_16_digits
                }
                messageId = R.string.bluetooth_enter_pin_other_device
                // Maximum of 16 characters in a PIN
                maxLength = CoreController.BLUETOOTH_PIN_MAX_LENGTH
            }
            BluetoothDevice.PAIRING_VARIANT_PASSKEY -> {
                messageId = R.string.bluetooth_enter_passkey_other_device
                maxLength = CoreController.BLUETOOTH_PASSKEY_MAX_LENGTH
            }
            else -> return
        }
        handler.removeCallbacks(hidePairingRequestInput)
        dialogView?.run {
            tvHint1?.setText(messageIdHint)
            if (tvHint2 is EditText) {
                tvHint2?.setHint(messageId)
            } else {
                tvHint2?.setText(messageId)
            }
            inputView?.run {
                inputType = InputType.TYPE_CLASS_NUMBER
                filters = arrayOf<InputFilter>(InputFilter.LengthFilter(maxLength))
            }
            confirmView?.setOnClickListener tvConfirmClick@{
                val text = inputView?.text.toString().trim()
                if (TextUtils.isEmpty(text)) {
                    Toast.makeText(context, R.string.bt_pin_input_hint, Toast.LENGTH_SHORT).show()
                    return@tvConfirmClick
                }
                when (type) {
                    BluetoothDevice.PAIRING_VARIANT_PIN, BluetoothDevice.PAIRING_VARIANT_PIN_16_DIGITS -> {
                        val pinBytes = BluetoothEventManager.convertPinToBytes(text)
                        if (pinBytes != null) {
                            cachedDevice.device.setPin(pinBytes)
                        }
                    }
                    BluetoothDevice.PAIRING_VARIANT_PASSKEY -> {
                        BluetoothEventManager.setPasskey(cachedDevice.device, text.toInt())
                    }
                }
                dialog.dismiss()
            }
            onPairingNeedInputDialogShowListener?.invoke(dialog)
            dialog.show()
        }
        handler.postDelayed(hidePairingRequestInput, CoreController.PAIRING_POPUP_TIMEOUT)
    }

    override fun onPairingCancel(cachedDevice: CachedBluetoothDevice) {
        handler.post(hidePairingRequestInput)
    }
}