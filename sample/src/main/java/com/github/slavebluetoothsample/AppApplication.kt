package com.github.slavebluetoothsample

import android.app.Application
import android.bluetooth.BluetoothHeadsetClientCall
import android.view.View
import android.widget.RelativeLayout
import com.github.slavebluetooth.BluetoothIniter
import com.github.slavebluetoothsample.ui.activity.MainActivity
import kotlinx.android.synthetic.main.layout_in_call_float.view.*

class AppApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val inCallFloatView = View.inflate(applicationContext, R.layout.layout_in_call_float, null)
        BluetoothIniter.setInCallShowActivityClass(MainActivity::class.java)
                .setInCallFloatShowContentView(inCallFloatView)
                .setInCallFloatShowControlView(inCallFloatView.ivAnswer, inCallFloatView.ivHangUp)
                .setInCallFloatShowOnCallNameGetListener {
                    inCallFloatView.tvCallInfo.text = it
                }
                .setInCallFloatShowOnCallTimeUpdateListener {
                    inCallFloatView.tvCallTime.text = it
                }
                .setInCallFloatShowOnCallStateChangeListener { state, _, callTime ->
                    when (state) {
                        BluetoothHeadsetClientCall.CALL_STATE_ACTIVE -> {
                            inCallFloatView.tvCallTime.visibility = View.VISIBLE
                            inCallFloatView.tvCallTime.text = callTime
                            inCallFloatView.tvCallState.setText(R.string.call_state_active)
                            inCallFloatView.ivAnswer.visibility = View.GONE
                            val ivHangUpParams = inCallFloatView.ivHangUp.layoutParams as RelativeLayout.LayoutParams
                            ivHangUpParams.removeRule(RelativeLayout.END_OF)
                            ivHangUpParams.addRule(RelativeLayout.ALIGN_PARENT_END)
                            inCallFloatView.ivHangUp.layoutParams = ivHangUpParams
                        }
                        BluetoothHeadsetClientCall.CALL_STATE_ALERTING -> {
                            inCallFloatView.tvCallTime.visibility = View.GONE
                            inCallFloatView.tvCallState.setText(R.string.call_state_dialing)
                            inCallFloatView.ivAnswer.visibility = View.GONE
                            val ivHangUpParams = inCallFloatView.ivHangUp.layoutParams as RelativeLayout.LayoutParams
                            ivHangUpParams.removeRule(RelativeLayout.END_OF)
                            ivHangUpParams.addRule(RelativeLayout.ALIGN_PARENT_END)
                            inCallFloatView.ivHangUp.layoutParams = ivHangUpParams
                        }
                        BluetoothHeadsetClientCall.CALL_STATE_INCOMING -> {
                            inCallFloatView.tvCallTime.visibility = View.GONE
                            inCallFloatView.tvCallState.setText(R.string.call_state_incoming)
                            inCallFloatView.ivAnswer.visibility = View.VISIBLE
                            val ivHangUpParams = inCallFloatView.ivHangUp.layoutParams as RelativeLayout.LayoutParams
                            ivHangUpParams.removeRule(RelativeLayout.ALIGN_PARENT_END)
                            ivHangUpParams.addRule(RelativeLayout.END_OF, R.id.ivAnswer)
                            inCallFloatView.ivHangUp.layoutParams = ivHangUpParams
                        }
                    }
                }
                .initAllProfile(applicationContext)
    }
}