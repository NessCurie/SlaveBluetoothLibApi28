package com.github.slavebluetooth.controller.hfpclient

import android.app.Activity
import android.app.ActivityManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadsetClient
import android.bluetooth.BluetoothHeadsetClientCall
import android.bluetooth.BluetoothProfile
import android.content.*
import android.media.AudioManager
import android.media.AudioSystem
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import com.android.settingslib.bluetooth.HfpClientProfile
import com.github.slavebluetooth.R
import com.github.slavebluetooth.controller.CoreController
import com.github.slavebluetooth.controller.pbapclient.PbapClientController
import com.github.slavebluetooth.controller.set.SetControlHelper
import com.github.slavebluetooth.model.BluetoothCall
import com.github.slavebluetooth.model.BluetoothCallInfo
import com.github.slavebluetooth.model.BluetoothPhonebook
import com.github.slavebluetooth.service.HfpClientMediaBrowserService
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

/**
 * BluetoothHeadsetClientCall.CALL_STATE_ACTIVE -> {    //通话中
 * BluetoothHeadsetClientCall.CALL_STATE_HELD -> {      //保持通话
 * BluetoothHeadsetClientCall.CALL_STATE_DIALING -> {
 * BluetoothHeadsetClientCall.CALL_STATE_ALERTING -> {  //呼出
 * BluetoothHeadsetClientCall.CALL_STATE_INCOMING -> {  //呼入 会一直调用
 * BluetoothHeadsetClientCall.CALL_STATE_WAITING -> {   //通话中有呼入
 * BluetoothHeadsetClientCall.CALL_STATE_HELD_BY_RESPONSE_AND_HOLD -> {
 * BluetoothHeadsetClientCall.CALL_STATE_TERMINATED -> {  //挂断
 */
object InCallPresenter : AudioManager.OnAudioFocusChangeListener {

    const val INTENT_EXTRA_CALL_NAME_KEY = "INTENT_EXTRA_CALL_NAME_KEY"
    const val INTENT_EXTRA_CALL_NUMBER_KEY = "INTENT_EXTRA_CALL_NUMBER_KEY"
    const val INTENT_EXTRA_CALL_STATE_KEY = "INTENT_EXTRA_CALL_STATE_KEY"
    const val INTENT_EXTRA_CALL_TIME_KEY = "INTENT_EXTRA_CALL_TIME_KEY"
    const val DEFAULT_VOLUME_ON_CALL_PERCENT = 40

    private lateinit var context: Context

    private val handler: Handler by lazy { Handler(Looper.getMainLooper()) }
    private val sp: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(context) }
    private val activityManager: ActivityManager by lazy {
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }
    private val hfpClientProfile: HfpClientProfile by lazy { CoreController.hfpClientProfile }
    private val hfpClient: BluetoothHeadsetClient by lazy { hfpClientProfile.service }

    private val phonebookList = ArrayList<BluetoothPhonebook>()
    private val onBluetoothPhonebookPullStartListener: ((BluetoothDevice) -> Unit) = {
        phonebookList.clear()
    }
    private val onBluetoothPhonebookPullFinishListener:
            ((BluetoothDevice, List<BluetoothPhonebook>) -> Unit) = { _, list ->
        phonebookList.clear()
        phonebookList.addAll(list)
        if (calls.isNotEmpty()) {
            val call = analyzeCallInfoAndState()
            if (call.second != BluetoothHeadsetClientCall.CALL_STATE_TERMINATED) {
                val name = getCallName(call.first.number)
                for (inCallListener in inCallListeners) {
                    inCallListener.updateName(name)
                }
            }
        }
    }

    private lateinit var inCallActivityClass: Class<out Activity>
    private val calls = ArrayList<BluetoothCall>()
    private val inCallListeners = ArrayList<InCallCallBack>()
    private var onCallAudioStateChanged: ((Int) -> Unit)? = null
    private var preVolume = 0
    private val audioManager: AudioManager by lazy {
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    private val sdf: SimpleDateFormat by lazy { SimpleDateFormat("mm:ss", Locale.ENGLISH) }
    private var callTime = 0L
    private var callTimeShow = ""
    private var connectedCheckCount = 0
    private var terminateCall: BluetoothHeadsetClientCall? = null
    private val onHfpClientConnectStateChangeListener: (Int, BluetoothDevice?) -> Unit by lazy {
        object : (Int, BluetoothDevice?) -> Unit {
            override fun invoke(state: Int, device: BluetoothDevice?) {
                if (state == BluetoothProfile.STATE_CONNECTED) {
                    if (device != null) {
                        handler.post(object : Runnable {   //会有延迟
                            override fun run() {
                                if (connectedCheckCount < 5 && calls.isEmpty()) {
                                    connectedCheckCount++
                                    checkCall(device)
                                    handler.postDelayed(this, 100)
                                } else {
                                    connectedCheckCount = 0
                                }
                            }
                        })
                    }
                } else {
                    for (call in calls) {
                        call.release()
                    }
                    calls.clear()
                    handleCall()
                }
            }
        }
    }

    private val hfpClientOtherStateChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothHeadsetClient.ACTION_CALL_CHANGED -> {
                    val receiverCall = intent.getParcelableExtra<BluetoothHeadsetClientCall>(
                            BluetoothHeadsetClient.EXTRA_CALL)
                    if (receiverCall != null) {
                        dealWith(receiverCall)
                    }
                }
                //每次播出或呼入时会自动先设置为STATE_AUDIO_CONNECTED,电话断开时会设置为STATE_AUDIO_DISCONNECTED
                BluetoothHeadsetClient.ACTION_AUDIO_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE,
                            BluetoothHeadsetClient.STATE_AUDIO_DISCONNECTED)
                    onCallAudioStateChanged?.invoke(state)
                    if (state == BluetoothHeadsetClient.STATE_AUDIO_CONNECTED) {
                        preVolume = audioManager.getStreamVolume(AudioSystem.STREAM_BLUETOOTH_SCO)
                        handler.post(checkBtCallVolume)
                    } else {
                        handler.removeCallbacks(checkBtCallVolume)
                        if (audioManager.getStreamVolume(AudioSystem.STREAM_BLUETOOTH_SCO) != preVolume) {
                            audioManager.setStreamVolume(AudioSystem.STREAM_BLUETOOTH_SCO,
                                    preVolume, AudioManager.FLAG_PLAY_SOUND)
                        }
                    }
                }
            }
        }
    }

    private val checkBtCallVolume: Runnable by lazy {
        object : Runnable {
            override fun run() {
                val streamMaxVolume = audioManager.getStreamMaxVolume(AudioSystem.STREAM_BLUETOOTH_SCO)
                val shouldVolume = streamMaxVolume * DEFAULT_VOLUME_ON_CALL_PERCENT / 100
                if (preVolume != shouldVolume) {
                    audioManager.setStreamVolume(AudioSystem.STREAM_BLUETOOTH_SCO,
                            shouldVolume, AudioManager.FLAG_PLAY_SOUND)
                }
                handler.postDelayed(this, 500)
            }
        }
    }

    /**
     * @param inCallActivityClass activity的launchMode需要是singleInstance 否则会一直打开
     */
    fun setInCallActivityClass(inCallActivityClass: Class<out Activity>) {
        InCallPresenter.inCallActivityClass = inCallActivityClass
    }

    fun init(context: Context) {
        InCallPresenter.context = context
        context.startService(Intent(context, HfpClientMediaBrowserService::class.java))

        CoreController.addOnHfpClientConnectStateChangeListener(onHfpClientConnectStateChangeListener)

        val filter = IntentFilter()
        filter.addAction(BluetoothHeadsetClient.ACTION_AUDIO_STATE_CHANGED)
        filter.addAction(BluetoothHeadsetClient.ACTION_CALL_CHANGED)
        context.registerReceiver(hfpClientOtherStateChangedReceiver, filter)

        if (CoreController.isHfpClientConnected()) {
            val device = CoreController.getHfpClientConnectedDevice()
            if (device != null) {
                if (!PbapClientController.isPhonebookPulling(device)) {
                    phonebookList.addAll(PbapClientController.getPhonebook(device))
                }
                checkCall(device)
            }
        }
        PbapClientController.addOnPhonebookPullStartListener(onBluetoothPhonebookPullStartListener)
        PbapClientController.addOnPhonebookPullFinishListener(onBluetoothPhonebookPullFinishListener)
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        inCallListeners.clear()
        context.stopService(Intent(context, HfpClientMediaBrowserService::class.java))
        CoreController.removeOnHfpClientConnectStateChangeListener(onHfpClientConnectStateChangeListener)
        context.unregisterReceiver(hfpClientOtherStateChangedReceiver)
        PbapClientController.removeOnPhonebookPullStartListener(onBluetoothPhonebookPullStartListener)
        PbapClientController.removeOnPhonebookPullFinishListener(onBluetoothPhonebookPullFinishListener)
    }

    @Suppress("DEPRECATION")
    private fun handleCall() {
        if (calls.isEmpty()) {
            callTime = 0
            callTimeShow = ""
            for (inCallListener in inCallListeners) {
                inCallListener.onCallClear()
            }
            audioManager.abandonAudioFocus(this)
            handler.removeCallbacks(checkBtCallVolume)
        } else {
            audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            val info = analyzeCallInfoAndState()
            if (activityManager.getRunningTasks(1)[0].topActivity.className == inCallActivityClass.name) {
                startInCallActivity()
            } else {
                showInCallFloatShowIfHasCall()
            }
            for (inCallListener in inCallListeners) {
                inCallListener.updateAll(info.first, info.second, callTimeShow)
            }
        }
    }

    fun addInCallListener(inCallCallBack: InCallCallBack) {
        if (!inCallListeners.contains(inCallCallBack)) {
            inCallListeners.add(inCallCallBack)
        }
    }

    fun removeInCallListener(inCallCallBack: InCallCallBack) {
        inCallListeners.remove(inCallCallBack)
    }

    fun setOnCallAudioStateChangedListener(listener: ((Int) -> Unit)?) {
        onCallAudioStateChanged = listener
    }

    fun showInCallFloatShowIfHasCall() {
        if (calls.isNotEmpty()) {
            if (InCallFloatShowHelper.isInit()) {
                InCallFloatShowHelper.onStart()
                val info = analyzeCallInfoAndState()
                for (inCallListener in inCallListeners) {
                    inCallListener.updateAll(info.first, info.second, callTimeShow)
                }
            } else {
                startInCallActivity()
            }
        }
    }

    fun hideInCallFloatShow() {
        if (InCallFloatShowHelper.isInit()) {
            InCallFloatShowHelper.dismiss()
        }
    }

    fun startInCallActivity() {
        val info = analyzeCallInfoAndState()
        startInCallActivity(info.first, info.second)
    }

    private fun startInCallActivity(callInfo: BluetoothCallInfo, state: Int) {
        val intent = Intent(context, inCallActivityClass)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.putExtra(INTENT_EXTRA_CALL_NAME_KEY, callInfo.name)
        intent.putExtra(INTENT_EXTRA_CALL_NUMBER_KEY, callInfo.number)
        intent.putExtra(INTENT_EXTRA_CALL_STATE_KEY, state)
        intent.putExtra(INTENT_EXTRA_CALL_TIME_KEY, callTimeShow)
        context.startActivity(intent)
    }

    fun dial(number: String): Boolean {
        val call = hfpClientProfile.dial(number)
        return if (call != null) {
            calls.add(BluetoothCall(number, BluetoothHeadsetClientCall.CALL_STATE_DIALING, ""))
            handleCall()
            true
        } else {
            false
        }
    }

    fun onTerminateCall(terminateCall: BluetoothHeadsetClientCall?) {
        if (terminateCall != null) {
            if (calls.size == 1) {
                InCallPresenter.terminateCall = terminateCall
                calls[0].release()
                calls.clear()
            } else {
                calls.remove(BluetoothCall(terminateCall))
            }
            handleCall()
        }
    }

    private fun getCallName(number: String): String {
        for (phoneBook in phonebookList) {
            if (phoneBook.number == number) {
                return phoneBook.name
            }
        }
        return number
    }

    private fun getCallInfo(call: BluetoothCall): BluetoothCallInfo {
        return BluetoothCallInfo(call.number, getCallName(call.number))
    }

    private fun getCallInfo(number: String): BluetoothCallInfo {
        return BluetoothCallInfo(number, getCallName(number))
    }

    private fun checkCall(device: BluetoothDevice) {
        val receiverCalls = hfpClient.getCurrentCalls(device)
        if (receiverCalls != null) {
            for (receiverCall in receiverCalls) {
                dealWith(receiverCall)
            }
        }
    }

    private fun dealWith(receiverCall: BluetoothHeadsetClientCall) {
        //挂断的时候界面操作很即时移除了列表,但是广播有延迟,偶尔会发送的还不是挂断
        terminateCall?.run {
            if (number == receiverCall.number) {
                if (receiverCall.state != BluetoothHeadsetClientCall.CALL_STATE_TERMINATED) {
                    return
                } else {
                    terminateCall = null
                }
            }
        }
        val call = BluetoothCall(receiverCall)
        val index = calls.indexOf(call)
        if (receiverCall.state == BluetoothHeadsetClientCall.CALL_STATE_TERMINATED) {
            if (index >= 0) {
                calls[index].updateCall(receiverCall)
                calls.removeAt(index)
            }
        } else {
            if (index >= 0) {
                calls[index].updateCall(receiverCall)
            } else {
                calls.add(call)
            }
        }
        handleCall()
    }

    private fun analyzeCallInfoAndState(): Pair<BluetoothCallInfo, Int> {
        var activeCount = 0
        val activeCalls = ArrayList<BluetoothCall>()
        for (call in calls) {
            //inComing的只会有一个电话时出现,正在通话中有呼入是waiting,处理了waiting,另外的要么挂断要么held
            if (call.state == BluetoothHeadsetClientCall.CALL_STATE_INCOMING) {
                val autoAccept = sp.getBoolean(SetControlHelper.SP_AUTO_ACCEPT_IN_COMING_CALL_KEY, false)
                if (autoAccept) {
                    hfpClientProfile.acceptCall()
                }
            }
            if (call.state == BluetoothHeadsetClientCall.CALL_STATE_ACTIVE) {
                activeCount++
                activeCalls.add(call)
            }
        }
        var callInfo = BluetoothCallInfo("", "")
        var state = BluetoothHeadsetClientCall.CALL_STATE_TERMINATED
        when {
            activeCalls.size > 1 -> {
                val callsInfoShow = String.format(context.getString(R.string.multi_call), activeCalls.size)
                callInfo = BluetoothCallInfo(callsInfoShow, callsInfoShow)
                state = BluetoothHeadsetClientCall.CALL_STATE_ACTIVE
                for (activeCall in activeCalls) {
                    activeCall.setOnActiveTimeChangeListener {
                        if (it >= callTime) {
                            callTime = it
                            callTimeShow = getShowTime(it)
                            for (inCallListener in inCallListeners) {
                                inCallListener.updateCallTime(callTimeShow)
                            }
                        }
                    }
                }
            }
            activeCalls.size == 1 -> {
                val call = activeCalls[0]
                call.setOnActiveTimeChangeListener {
                    callTime = it
                    callTimeShow = getShowTime(it)
                    for (inCallListener in inCallListeners) {
                        inCallListener.updateCallTime(callTimeShow)
                    }
                }
                callInfo = getCallInfo(call)
                state = BluetoothHeadsetClientCall.CALL_STATE_ACTIVE
            }
            else -> {
                //没有active时,一般一个inComing或者alerting 或者held马上要active waiting马上要inComing
                //或者有个held加alerting,通话中自己呼出
                if (calls.size == 1) {
                    val call = calls[0]
                    callInfo = getCallInfo(call)
                    state = call.state
                    callTimeShow = ""
                } else {
                    for (call in calls) {
                        if (call.state == BluetoothHeadsetClientCall.CALL_STATE_ALERTING
                                || call.state == BluetoothHeadsetClientCall.CALL_STATE_DIALING) {
                            callInfo = getCallInfo(call)
                            state = call.state
                            callTimeShow = ""
                            break
                        }
                    }
                }
            }
        }
        return Pair(callInfo, state)
    }

    fun getCallState() = analyzeCallInfoAndState().second

    fun hasCall() = calls.isNotEmpty()

    private fun getShowTime(time: Long): String {
        val timeMS = sdf.format(time)
        return if (time < 3600000) {
            timeMS
        } else {
            val hours = time / 3600000
            if (hours < 10) {
                "0$hours:$timeMS"
            } else {
                "$hours:$timeMS"
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onAudioFocusChange(focusChange: Int) {
        audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
    }
}