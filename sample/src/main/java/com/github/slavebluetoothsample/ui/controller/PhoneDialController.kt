package com.github.slavebluetoothsample.ui.controller

import android.content.Context
import android.view.View
import com.github.slavebluetooth.controller.hfpclient.DialHelper
import com.github.slavebluetoothsample.R
import kotlinx.android.synthetic.main.layout_dial.view.*
import kotlinx.android.synthetic.main.layout_dial_num.view.*

class PhoneDialController(private val context: Context) {

    val content: View by lazy { View.inflate(context, R.layout.layout_dial, null) }
    private val dialHelper = DialHelper()

    init {
        dialHelper.setDialpadView(content.etInput, content.tvNum0, content.tvNum1, content.tvNum2,
                content.tvNum3, content.tvNum4, content.tvNum5, content.tvNum6, content.tvNum7,
                content.tvNum8, content.tvNum9, content.tvSymbolAsterisk, content.tvSymbolPoundSign,
                content.ivDelete, content.ivCall)
    }

    fun release() {
        dialHelper.release()
    }
}