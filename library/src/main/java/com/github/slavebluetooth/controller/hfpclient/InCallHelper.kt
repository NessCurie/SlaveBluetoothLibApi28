package com.github.slavebluetooth.controller.hfpclient

import android.bluetooth.BluetoothHeadsetClient
import android.bluetooth.BluetoothHeadsetClientCall
import android.graphics.drawable.Drawable
import android.view.KeyEvent
import android.view.View
import com.android.settingslib.bluetooth.HfpClientProfile
import com.github.slavebluetooth.controller.CoreController
import com.github.slavebluetooth.model.BluetoothCallInfo
import com.github.slavebluetooth.view.DigitsEditText

class InCallHelper : InCallCallBack {

    private val hfpClientProfile: HfpClientProfile by lazy { CoreController.hfpClientProfile }
    private val dtmfMap: HashMap<Int, Char> by lazy {
        val map = HashMap<Int, Char>()
        map[KeyEvent.KEYCODE_0] = '0'
        map[KeyEvent.KEYCODE_1] = '1'
        map[KeyEvent.KEYCODE_2] = '2'
        map[KeyEvent.KEYCODE_3] = '3'
        map[KeyEvent.KEYCODE_4] = '4'
        map[KeyEvent.KEYCODE_5] = '5'
        map[KeyEvent.KEYCODE_6] = '6'
        map[KeyEvent.KEYCODE_7] = '7'
        map[KeyEvent.KEYCODE_8] = '8'
        map[KeyEvent.KEYCODE_9] = '9'
        map[KeyEvent.KEYCODE_STAR] = '*'
        map[KeyEvent.KEYCODE_POUND] = '#'
        map
    }

    private var etInput: DigitsEditText? = null

    private var onCallNameGetListener: ((name: String) -> Unit)? = null
    private var onCallTimeUpdateListener: ((time: String) -> Unit)? = null
    private var onCallStateChangeListener: ((state: Int, callInfo: BluetoothCallInfo, callTime: String) -> Unit)? = null

    init {
        InCallPresenter.addInCallListener(this)
    }

    fun onStart() {
        InCallPresenter.hideInCallFloatShow()
        if (InCallPresenter.hasCall()) {
            InCallPresenter.startInCallActivity()
        }
    }

    fun onPause(isFinishing: Boolean) {
        if (isFinishing) {
            release()
        }
    }

    fun onStop() {
        InCallPresenter.showInCallFloatShowIfHasCall()
    }

    fun release() {
        onCallNameGetListener = null
        onCallTimeUpdateListener = null
        onCallStateChangeListener = null
        InCallPresenter.removeInCallListener(this)
        InCallPresenter.setOnCallAudioStateChangedListener(null)
    }

    fun setDialpadView(etInput: DigitsEditText, num0: View, num1: View, num2: View, num3: View, num4: View,
                       num5: View, num6: View, num7: View, num8: View, num9: View, star: View,
                       pound: View): InCallHelper {
        this.etInput = etInput
        etInput.setOnClickListener {}
        etInput.isClickable = false
        etInput.isLongClickable = false
        etInput.isFocusableInTouchMode = false
        etInput.isCursorVisible = false

        num0.setOnClickListener { keyPressed(KeyEvent.KEYCODE_0) }
        num1.setOnClickListener { keyPressed(KeyEvent.KEYCODE_1) }
        num2.setOnClickListener { keyPressed(KeyEvent.KEYCODE_2) }
        num3.setOnClickListener { keyPressed(KeyEvent.KEYCODE_3) }
        num4.setOnClickListener { keyPressed(KeyEvent.KEYCODE_4) }
        num5.setOnClickListener { keyPressed(KeyEvent.KEYCODE_5) }
        num6.setOnClickListener { keyPressed(KeyEvent.KEYCODE_6) }
        num7.setOnClickListener { keyPressed(KeyEvent.KEYCODE_7) }
        num8.setOnClickListener { keyPressed(KeyEvent.KEYCODE_8) }
        num9.setOnClickListener { keyPressed(KeyEvent.KEYCODE_9) }
        star.setOnClickListener { keyPressed(KeyEvent.KEYCODE_STAR) }
        pound.setOnClickListener { keyPressed(KeyEvent.KEYCODE_POUND) }
        return this
    }

    fun setControlView(answer: View, hangUp: View): InCallHelper {
        answer.setOnClickListener { hfpClientProfile.acceptCall() }
        hangUp.setOnClickListener { InCallPresenter.onTerminateCall(hfpClientProfile.terminateCall()) }
        return this
    }

    fun setAudioChangeView(audioChange: View, audioOnBtDrawable: Drawable,
                           audioOnPhoneDrawable: Drawable): InCallHelper {
        audioChange.setOnClickListener { hfpClientProfile.setAudioRouteReverse() }
        InCallPresenter.setOnCallAudioStateChangedListener {
            if (it == BluetoothHeadsetClient.STATE_AUDIO_CONNECTED) {
                audioChange.background = audioOnBtDrawable
            } else {
                audioChange.background = audioOnPhoneDrawable
            }
        }
        return this
    }

    /**
     * 只会在通话状态发生改变时调用
     * state已经进行了简化,只剩下4种状态
     * BluetoothHeadsetClientCall.CALL_STATE_ACTIVE     //通话中
     * BluetoothHeadsetClientCall.CALL_STATE_ALERTING   //呼出
     * BluetoothHeadsetClientCall.CALL_STATE_INCOMING   //呼入
     * BluetoothHeadsetClientCall.CALL_STATE_TERMINATED //挂断
     */
    fun setOnCallStateChangeListener(listener: ((state: Int, callInfo: BluetoothCallInfo,
                                                 callTime: String) -> Unit)?): InCallHelper {
        onCallStateChangeListener = listener
        return this
    }

    /**
     * 只会在通讯录同步完成之后进行一次查询,如果找到后会进行回调
     */
    fun setOnCallNameGetListener(listener: ((name: String) -> Unit)?): InCallHelper {
        onCallNameGetListener = listener
        return this
    }

    /**
     * 只会在通话中通话时间改变时进行回调
     */
    fun setOnCallTimeUpdateListener(listener: ((time: String) -> Unit)?): InCallHelper {
        onCallTimeUpdateListener = listener
        return this
    }

    private fun keyPressed(keyCode: Int) {
        etInput?.onKeyDown(keyCode, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        hfpClientProfile.sendDTMF(dtmfMap[keyCode]!!.toByte())
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
    }

    override fun onCallClear() {
        etInput?.setText("")
        onCallStateChangeListener?.invoke(
                BluetoothHeadsetClientCall.CALL_STATE_TERMINATED, BluetoothCallInfo("", ""), "")
    }

}