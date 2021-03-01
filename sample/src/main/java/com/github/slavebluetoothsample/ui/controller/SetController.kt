package com.github.slavebluetoothsample.ui.controller

import android.app.Dialog
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.view.View
import com.github.slavebluetooth.controller.set.DeviceSingleListHelper
import com.github.slavebluetooth.controller.set.SetControlHelper
import com.github.slavebluetoothsample.R
import kotlinx.android.synthetic.main.dialog_set_bt_name.view.*
import kotlinx.android.synthetic.main.layout_bt_set.view.*
import org.jetbrains.anko.toast

class SetController(private val context: Context) {

    val content: View by lazy { View.inflate(context, R.layout.layout_bt_set, null) }

    private val setControlHelper: SetControlHelper by lazy { SetControlHelper(context) }
    private val deviceSingleListHelper: DeviceSingleListHelper by lazy { DeviceSingleListHelper(context) }

    @Suppress("DEPRECATION")
    private val dialogView: View by lazy {
        val view = View.inflate(context, R.layout.dialog_set_bt_name, null)
        view.tvCancel.setOnClickListener { dialog.dismiss() }
        view
    }
    private val dialog: Dialog by lazy {
        val dialog = Dialog(context, R.style.NormalDialog)
        dialog.setContentView(dialogView)
        dialog
    }

    init {
        content.rlBtName.setOnClickListener {
            dialogView.tvTitle.setText(R.string.bt_name)
            dialogView.etInput.inputType = InputType.TYPE_NULL
            dialogView.etInput.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(10))
            @Suppress("DEPRECATION")
            dialogView.etInput.setHint(R.string.please_input_bluetooth_name)
            dialogView.etInput.setText("")
            dialogView.tvConfirm.setOnClickListener tvConfirmClick@{
                val text = dialogView.etInput.text.toString().trim()
                if (TextUtils.isEmpty(text)) {
                    context.toast(R.string.please_input_bluetooth_name)
                    return@tvConfirmClick
                }
                setControlHelper.setBluetoothName(text)
                content.tvBtName.text = text
                dialog.dismiss()
            }
            dialog.show()
        }

        setControlHelper.setNameView(content.tvBtName, content.rlBtName)
                .setBtSwitchView(content.switchBt)
                .setBtAutoSyncPhone(content.sbAutoSyncPhone)
                .setBtAutoAnswer(content.sbAutoAnswer)
                .setOnBluetoothStateChangedListener {
                    when (it) {
                        BluetoothAdapter.STATE_ON -> {
                            content.tvBtCloseHint.visibility = View.GONE
                            content.llBtOpen.visibility = View.VISIBLE
                        }
                        BluetoothAdapter.STATE_TURNING_ON -> {
                            content.tvBtCloseHint.visibility = View.VISIBLE
                            content.llBtOpen.visibility = View.GONE
                            content.tvBtCloseHint.setText(R.string.bluetooth_turning_on)
                        }
                        BluetoothAdapter.STATE_TURNING_OFF -> {
                            content.tvBtCloseHint.visibility = View.VISIBLE
                            content.llBtOpen.visibility = View.GONE
                            content.tvBtCloseHint.setText(R.string.bluetooth_turning_off)
                        }
                        BluetoothAdapter.STATE_OFF -> {
                            content.tvBtCloseHint.visibility = View.VISIBLE
                            content.llBtOpen.visibility = View.GONE
                            content.tvBtCloseHint.setText(R.string.bluetooth_empty_list_bluetooth_off)
                        }
                    }
                }
                .init()
        deviceSingleListHelper.setDevicesListView(content.rvFindDevice, R.layout.item_bt_find_device)
                .setDevicesListItem(R.id.tvName, R.id.tvState, null, null,
                        context.getColor(R.color.colorPrimary),
                        context.getColor(R.color.colorTextDim))
                .setRefreshView(content.ivRefresh, content.tvNoFindDevice)
                .init()
    }

    fun onReStart() {
        deviceSingleListHelper.onRestart()
    }

    fun onStop() {
        deviceSingleListHelper.onStop()
    }

    fun release() {
        setControlHelper.release()
    }
}