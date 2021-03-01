package com.github.slavebluetoothsample.ui.controller

import android.bluetooth.BluetoothHeadsetClientCall
import android.content.Context
import android.view.View
import android.widget.LinearLayout
import com.github.slavebluetooth.controller.hfpclient.InCallHelper
import com.github.slavebluetoothsample.R
import kotlinx.android.synthetic.main.layout_dial_num.view.*
import kotlinx.android.synthetic.main.layout_in_call.view.*

class InCallController(private val context: Context) {

    val content: View by lazy { View.inflate(context, R.layout.layout_in_call, null) }
    private val inCallHelper = InCallHelper()

    init {
        content.visibility = View.GONE
        inCallHelper.setDialpadView(content.etInput, content.tvNum0, content.tvNum1, content.tvNum2,
                content.tvNum3, content.tvNum4, content.tvNum5, content.tvNum6, content.tvNum7,
                content.tvNum8, content.tvNum9, content.tvSymbolAsterisk, content.tvSymbolPoundSign)
                .setControlView(content.ivAnswer, content.ivHangUp)
                .setAudioChangeView(content.ivAudioChange,
                        context.getDrawable(R.drawable.bg_audio_on_bt_selector)!!,
                        context.getDrawable(R.drawable.bg_audio_on_phone_selector)!!)
                .setOnCallNameGetListener {
                    content.tvCallInfo.text = it
                }
                .setOnCallTimeUpdateListener {
                    content.tvCallState.text = it
                }
                .setOnCallStateChangeListener { state, _, _ ->
                    when (state) {
                        BluetoothHeadsetClientCall.CALL_STATE_ACTIVE -> {
                            content.visibility = View.VISIBLE
                            content.ivAnswer.visibility = View.GONE
                            content.ivHangUp.visibility = View.VISIBLE
                            content.ivAudioChange.visibility = View.VISIBLE
                            content.ivShowDial.visibility = View.VISIBLE

                            val ivAudioChangeParams = content.ivAudioChange.layoutParams as LinearLayout.LayoutParams
                            ivAudioChangeParams.marginStart = 55
                            content.ivAudioChange.layoutParams = ivAudioChangeParams

                            val ivShowDialParams = content.ivShowDial.layoutParams as LinearLayout.LayoutParams
                            ivShowDialParams.marginStart = 55
                            content.ivAudioChange.layoutParams = ivShowDialParams
                        }
                        BluetoothHeadsetClientCall.CALL_STATE_ALERTING -> {
                            content.visibility = View.VISIBLE
                            content.tvCallState.setText(R.string.call_state_dialing)
                            content.ivAnswer.visibility = View.GONE
                            content.ivHangUp.visibility = View.VISIBLE
                            content.ivAudioChange.visibility = View.VISIBLE
                            content.ivShowDial.visibility = View.GONE

                            val ivAudioChangeParams = content.ivAudioChange.layoutParams as LinearLayout.LayoutParams
                            ivAudioChangeParams.marginStart = 80
                            content.ivAudioChange.layoutParams = ivAudioChangeParams
                        }
                        BluetoothHeadsetClientCall.CALL_STATE_INCOMING -> {
                            content.visibility = View.VISIBLE
                            content.tvCallState.setText(R.string.call_state_incoming)
                            content.ivAnswer.visibility = View.VISIBLE
                            content.ivHangUp.visibility = View.VISIBLE
                            content.ivAudioChange.visibility = View.GONE
                            content.ivShowDial.visibility = View.GONE

                            val ivHangUpParams = content.ivHangUp.layoutParams as LinearLayout.LayoutParams
                            ivHangUpParams.marginStart = 80
                            content.ivHangUp.layoutParams = ivHangUpParams
                        }
                        BluetoothHeadsetClientCall.CALL_STATE_TERMINATED -> {
                            content.visibility = View.GONE
                            content.llCallInfo.visibility = View.VISIBLE
                            content.layoutDialNum.visibility = View.GONE
                        }
                    }
                }

        content.ivShowDial.setOnClickListener {
            if (content.layoutDialNum.visibility == View.GONE) {
                content.llCallInfo.visibility = View.GONE
                content.layoutDialNum.visibility = View.VISIBLE
            } else {
                content.llCallInfo.visibility = View.VISIBLE
                content.layoutDialNum.visibility = View.GONE
            }
        }
    }

    fun onStart() {
        inCallHelper.onStart()
    }

    fun onPause(isFinishing: Boolean) {
        inCallHelper.onPause(isFinishing)
    }

    fun onStop() {
        inCallHelper.onStop()
    }

    fun release() {
        inCallHelper.release()
    }
}