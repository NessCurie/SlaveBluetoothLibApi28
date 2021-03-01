package com.github.slavebluetoothsample.ui.activity

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadsetClientCall
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import com.github.slavebluetooth.controller.CoreController
import com.github.slavebluetooth.controller.hfpclient.InCallPresenter
import com.github.slavebluetoothsample.*
import com.github.slavebluetoothsample.ui.controller.InCallController
import com.github.slavebluetoothsample.ui.controller.MusicController
import com.github.slavebluetoothsample.ui.controller.PhoneController
import com.github.slavebluetoothsample.ui.controller.SetController
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private val set: SetController by lazy { SetController(applicationContext) }
    private val music: MusicController by lazy { MusicController(applicationContext) }
    private val phone: PhoneController by lazy { PhoneController(applicationContext) }

    private val inCall: InCallController by lazy { InCallController(applicationContext) }

    private val onAvrcpSinkConnectStateChangeListener: (Int, BluetoothDevice?) -> Unit by lazy {
        object : (Int, BluetoothDevice?) -> Unit {
            override fun invoke(state: Int, device: BluetoothDevice?) {
                if (state == BluetoothProfile.STATE_CONNECTED) {
                    if (rgMain.checkedRadioButtonId == R.id.rbMusic) {
                        llNotConnectedHint.visibility = View.GONE
                        flContent.removeAllViews()
                        flContent.addView(music.content)
                    }
                } else {
                    if (rgMain.checkedRadioButtonId == R.id.rbMusic) {
                        flContent.removeAllViews()
                        llNotConnectedHint.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private val onHfpClientConnectStateChangeListener: (Int, BluetoothDevice?) -> Unit by lazy {
        object : (Int, BluetoothDevice?) -> Unit {
            override fun invoke(state: Int, device: BluetoothDevice?) {
                if (state == BluetoothProfile.STATE_CONNECTED) {
                    if (rgMain.checkedRadioButtonId == R.id.rbPhone) {
                        llNotConnectedHint.visibility = View.GONE
                        flContent.removeAllViews()
                        flContent.addView(phone.content)
                    }
                } else {
                    if (rgMain.checkedRadioButtonId == R.id.rbPhone) {
                        flContent.removeAllViews()
                        llNotConnectedHint.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        CoreController.addOnA2dpSinkConnectStateChangeListener(onAvrcpSinkConnectStateChangeListener)
        CoreController.addOnHfpClientConnectStateChangeListener(onHfpClientConnectStateChangeListener)
        rgMain.setOnCheckedChangeListener { _, checkedId ->
            flContent.removeAllViews()
            when (checkedId) {
                R.id.rbSet -> {
                    flContent.addView(set.content)
                    llNotConnectedHint.visibility = View.GONE
                }
                R.id.rbMusic -> {
                    if (CoreController.isA2dpSinkConnected()) {
                        flContent.addView(music.content)
                        llNotConnectedHint.visibility = View.GONE
                    } else {
                        llNotConnectedHint.visibility = View.VISIBLE
                    }
                }
                R.id.rbPhone -> {
                    if (CoreController.isHfpClientConnected()) {
                        flContent.addView(phone.content)
                        llNotConnectedHint.visibility = View.GONE
                    } else {
                        llNotConnectedHint.visibility = View.VISIBLE
                    }
                }
            }
        }
        if (!checkIntent(intent)) {
            rbSet.isChecked = true
            flContent.removeAllViews()
            flContent.addView(set.content)
            llNotConnectedHint.visibility = View.GONE
        }
        flInCall.removeAllViews()
        flInCall.addView(inCall.content)

        tvSetBt.setOnClickListener { rbSet.isChecked = true }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        checkIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        inCall.onStart()
    }

    override fun onRestart() {
        set.onReStart()
        super.onRestart()
    }

    override fun onPause() {
        inCall.onPause(isFinishing)
        super.onPause()
    }

    override fun onStop() {
        set.onStop()
        inCall.onStop()
        InCallPresenter.showInCallFloatShowIfHasCall()
        super.onStop()
    }

    override fun onDestroy() {
        CoreController.removeOnA2dpSinkConnectStateChangeListener(onAvrcpSinkConnectStateChangeListener)
        CoreController.removeOnHfpClientConnectStateChangeListener(onHfpClientConnectStateChangeListener)
        set.release()
        music.release()
        phone.release()
        inCall.release()
        super.onDestroy()
    }

    private fun checkIntent(intent: Intent?): Boolean {
        if (intent != null) {
            val name = intent.getStringExtra(InCallPresenter.INTENT_EXTRA_CALL_NAME_KEY)
            val number = intent.getStringExtra(InCallPresenter.INTENT_EXTRA_CALL_NUMBER_KEY)
            val state = intent.getIntExtra(InCallPresenter.INTENT_EXTRA_CALL_STATE_KEY,
                    BluetoothHeadsetClientCall.CALL_STATE_TERMINATED)
            val callTime = intent.getStringExtra(InCallPresenter.INTENT_EXTRA_CALL_TIME_KEY)
            if (name != null && number != null && callTime != null
                    && state != BluetoothHeadsetClientCall.CALL_STATE_TERMINATED) {
                flInCall.visibility = View.VISIBLE
            }
        }
        return false
    }
}