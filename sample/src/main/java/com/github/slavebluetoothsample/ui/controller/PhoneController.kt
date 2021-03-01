package com.github.slavebluetoothsample.ui.controller

import android.content.Context
import android.view.View
import com.github.slavebluetoothsample.R
import kotlinx.android.synthetic.main.layout_bt_phone.view.*

class PhoneController(private val context: Context) {

    val content: View by lazy { View.inflate(context, R.layout.layout_bt_phone, null) }

    private val dial: PhoneDialController by lazy { PhoneDialController(context) }
    private val book: PhoneBookController by lazy { PhoneBookController(context) }
    private val record: PhoneRecordController by lazy { PhoneRecordController(context) }

    init {
        content.rgPhone.setOnCheckedChangeListener { _, checkedId ->
            content.flContent.removeAllViews()
            when (checkedId) {
                R.id.rbDial -> content.flContent.addView(dial.content)
                R.id.rbBook -> content.flContent.addView(book.content)
                R.id.rbRecord -> content.flContent.addView(record.content)
            }
        }
        content.rbDial.isChecked = true
        content.flContent.removeAllViews()
        content.flContent.addView(dial.content)
    }

    fun release() {
        dial.release()
        book.release()
        record.release()
    }
}