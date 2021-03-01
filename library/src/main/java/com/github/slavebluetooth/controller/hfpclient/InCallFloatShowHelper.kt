package com.github.slavebluetooth.controller.hfpclient

import android.bluetooth.BluetoothHeadsetClientCall
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.android.settingslib.bluetooth.HfpClientProfile
import com.github.slavebluetooth.controller.CoreController
import com.github.slavebluetooth.model.BluetoothCallInfo

object InCallFloatShowHelper : InCallCallBack {

    private lateinit var context: Context

    fun isInit() = this::context.isInitialized

    fun init(context: Context) {
        InCallFloatShowHelper.context = context
        windowManager
    }

    private const val HIDE_LENGTH = 1
    private const val HIDE_POSITION = -1
    const val DEFAULT_SHOW_POSITION = 0
    private val hfpClientProfile: HfpClientProfile by lazy { CoreController.hfpClientProfile }

    private var content: View? = null
    private var showX = DEFAULT_SHOW_POSITION
    private var showY = DEFAULT_SHOW_POSITION

    private var onCallNameGetListener: ((name: String) -> Unit)? = null
    private var onCallTimeUpdateListener: ((time: String) -> Unit)? = null
    private var onCallStateChangeListener: ((state: Int, callInfo: BluetoothCallInfo, callTime: String) -> Unit)? = null

    private val params: WindowManager.LayoutParams by lazy {
        val params = WindowManager.LayoutParams()
        @Suppress("DEPRECATION") //只有SYSTEM_ERROR才能覆盖到systemUI之上
        params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ERROR
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        params.format = PixelFormat.TRANSLUCENT
        params.width = HIDE_LENGTH
        params.height = HIDE_LENGTH
        params.x = HIDE_POSITION
        params.y = HIDE_POSITION
        params.gravity = Gravity.START or Gravity.TOP
        params
    }
    private val windowManager: WindowManager by lazy {
        val windowManager = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.addView(content, params)
        windowManager
    }
    private var isShowing = false

    fun setContentView(view: View, showX: Int, showY: Int): InCallFloatShowHelper {
        content = view
        InCallFloatShowHelper.showX = showX
        InCallFloatShowHelper.showY = showY
        view.setOnClickListener {
            InCallPresenter.startInCallActivity()
            dismiss()
        }
        return this
    }

    fun setControlView(answer: View, terminate: View): InCallFloatShowHelper {
        answer.setOnClickListener { hfpClientProfile.acceptCall() }
        terminate.setOnClickListener { InCallPresenter.onTerminateCall(hfpClientProfile.terminateCall()) }
        return this
    }

    fun setOnCallStateChangeListener(listener: ((state: Int, callInfo: BluetoothCallInfo,
                                                 callTime: String) -> Unit)?): InCallFloatShowHelper {
        onCallStateChangeListener = listener
        return this
    }

    /**
     * 只会在通讯录同步完成之后进行一次查询,如果找到后会进行回调
     */
    fun setOnCallNameGetListener(listener: ((name: String) -> Unit)?): InCallFloatShowHelper {
        onCallNameGetListener = listener
        return this
    }

    /**
     * 只会在通话中通话时间改变时进行回调
     */
    fun setOnCallTimeUpdateListener(listener: ((time: String) -> Unit)?): InCallFloatShowHelper {
        onCallTimeUpdateListener = listener
        return this
    }

    internal fun onStart() {
        InCallPresenter.addInCallListener(this)
    }

    override fun updateName(name: String) {
        onCallNameGetListener?.invoke(name)
    }

    override fun updateCallTime(time: String) {
        onCallTimeUpdateListener?.invoke(time)
    }

    override fun updateAll(callInfo: BluetoothCallInfo, state: Int, callTime: String) {
        onCallNameGetListener?.invoke(callInfo.name)
        onCallTimeUpdateListener?.invoke(callTime)
        when (state) {
            BluetoothHeadsetClientCall.CALL_STATE_HELD,    //保持通话
            BluetoothHeadsetClientCall.CALL_STATE_ACTIVE -> {    //通话中
                onCallStateChangeListener?.invoke(BluetoothHeadsetClientCall.CALL_STATE_ACTIVE, callInfo, callTime)
            }
            BluetoothHeadsetClientCall.CALL_STATE_DIALING,
            BluetoothHeadsetClientCall.CALL_STATE_ALERTING -> {  //呼出
                onCallStateChangeListener?.invoke(BluetoothHeadsetClientCall.CALL_STATE_ALERTING, callInfo, callTime)
            }
            BluetoothHeadsetClientCall.CALL_STATE_INCOMING,     //呼入 会一直调用
            BluetoothHeadsetClientCall.CALL_STATE_WAITING -> {  //通话中有呼入
                onCallStateChangeListener?.invoke(BluetoothHeadsetClientCall.CALL_STATE_INCOMING, callInfo, callTime)
            }
            BluetoothHeadsetClientCall.CALL_STATE_HELD_BY_RESPONSE_AND_HOLD -> {
            }
            BluetoothHeadsetClientCall.CALL_STATE_TERMINATED -> {  //挂断
                onCallClear()
            }
        }
        if (!isShowing) {
            isShowing = true
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.x = showX
            params.y = -48
            windowManager.updateViewLayout(content, params)
        }
    }

    override fun onCallClear() {
        dismiss()
        onCallStateChangeListener?.invoke(
                BluetoothHeadsetClientCall.CALL_STATE_TERMINATED, BluetoothCallInfo("", ""), "")
    }

    fun dismiss() {
        InCallPresenter.removeInCallListener(this)
        if (isShowing) {
            isShowing = false
            params.width = HIDE_LENGTH
            params.height = HIDE_LENGTH
            params.x = HIDE_POSITION
            params.y = HIDE_POSITION
            windowManager.updateViewLayout(content, params)
        }
    }

}