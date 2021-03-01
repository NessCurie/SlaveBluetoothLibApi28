package com.github.slavebluetooth

import android.app.Activity
import android.content.Context
import android.view.View
import com.github.slavebluetooth.controller.CoreController
import com.github.slavebluetooth.controller.hfpclient.InCallFloatShowHelper
import com.github.slavebluetooth.controller.hfpclient.InCallPresenter
import com.github.slavebluetooth.controller.pbapclient.PbapClientController
import com.github.slavebluetooth.model.BluetoothCallInfo
import com.github.slavebluetooth.utils.CrashHandler
import java.lang.RuntimeException


object BluetoothIniter {

    private var setInCallFloatShowContentView = false
    private var setInCallFloatShowControlView = false
    private var setInCallFloatShowOnCallNameGetListener = false
    private var setInCallFloatShowOnCallTimeUpdateListener = false
    private var setInCallFloatShowOnCallStateChangeListener = false
    private var setInCallShowActivityClass: Boolean = false

    fun setInCallFloatShowContentView(inCallFloatShowView: View,
                                      showX: Int = InCallFloatShowHelper.DEFAULT_SHOW_POSITION,
                                      showY: Int = InCallFloatShowHelper.DEFAULT_SHOW_POSITION): BluetoothIniter {
        InCallFloatShowHelper.setContentView(inCallFloatShowView, showX, showY)
        setInCallFloatShowContentView = true
        return this
    }

    fun setInCallFloatShowControlView(inCallFloatShowAnswerView: View,
                                      inCallFloatShowTerminateView: View): BluetoothIniter {
        InCallFloatShowHelper.setControlView(inCallFloatShowAnswerView, inCallFloatShowTerminateView)
        setInCallFloatShowControlView = true
        return this
    }

    fun setInCallFloatShowOnCallNameGetListener(onCallNameGetListener: ((name: String) -> Unit)): BluetoothIniter {
        InCallFloatShowHelper.setOnCallNameGetListener(onCallNameGetListener)
        setInCallFloatShowOnCallNameGetListener = true
        return this
    }

    fun setInCallFloatShowOnCallTimeUpdateListener(onCallTimeUpdateListener: ((time: String) -> Unit)): BluetoothIniter {
        InCallFloatShowHelper.setOnCallTimeUpdateListener(onCallTimeUpdateListener)
        setInCallFloatShowOnCallTimeUpdateListener = true
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
    fun setInCallFloatShowOnCallStateChangeListener(onCallStateChangeListener:
                                                    ((state: Int, callInfo: BluetoothCallInfo,
                                                      callTime: String) -> Unit)): BluetoothIniter {
        InCallFloatShowHelper.setOnCallStateChangeListener(onCallStateChangeListener)
        setInCallFloatShowOnCallStateChangeListener = true
        return this
    }

    /**
     * @param inCallActivityClass activity的launchMode需要是singleInstance 否则会一直打开
     */
    fun setInCallShowActivityClass(inCallActivityClass: Class<out Activity>): BluetoothIniter {
        InCallPresenter.setInCallActivityClass(inCallActivityClass)
        setInCallShowActivityClass = true
        return this
    }

    /**
     * 包含蓝牙电话和蓝牙音乐和通讯录的所有初始化,来电话时不在inCallActivity时显示inCallFloatShowView
     */
    fun initAllProfile(context: Context) {
        if (!setInCallShowActivityClass) {
            throw RuntimeException("all profile must set inCallActivityClass")
        }
        val applicationContext = context.applicationContext
        CrashHandler.setContext(applicationContext)
        if (setInCallFloatShowContentView && setInCallFloatShowControlView
                && setInCallFloatShowOnCallNameGetListener
                && setInCallFloatShowOnCallTimeUpdateListener
                && setInCallFloatShowOnCallStateChangeListener) {
            InCallFloatShowHelper.init(applicationContext)
        }
        CoreController.init(applicationContext)
        InCallPresenter.init(applicationContext)
        PbapClientController.init(applicationContext)
    }

    /**
     * 仅蓝牙音乐
     */
    fun initA2dpSinkProfile(context: Context) {
        val applicationContext = context.applicationContext
        CrashHandler.setContext(applicationContext)
        CoreController.init(applicationContext)
    }
}

