package com.github.slavebluetooth.controller.hfpclient

import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import com.github.slavebluetooth.view.DigitsEditText

class DialHelper : TextWatcher {

    private var etInput: DigitsEditText? = null
    private var onInputTextChangeListener: ((text: CharSequence) -> Unit)? = null

    fun setDialpadView(etInput: DigitsEditText, num0: View, num1: View, num2: View, num3: View, num4: View,
                       num5: View, num6: View, num7: View, num8: View, num9: View, star: View,
                       pound: View, del: View, call: View) {
        this.etInput = etInput
        etInput.setOnClickListener {
            if (etInput.length() != 0) {
                etInput.isCursorVisible = true
            }
        }
        etInput.addTextChangedListener(this)

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
        del.setOnClickListener { keyPressed(KeyEvent.KEYCODE_DEL) }
        del.setOnLongClickListener {
            etInput.text.clear()
            true
        }

        call.setOnClickListener {
            val inputText = etInput.text
            if (!TextUtils.isEmpty(inputText)) {
                if (InCallPresenter.dial(inputText.toString())) {
                    etInput.text.clear()
                }
            }
        }
    }

    fun setOnInputTextChangeListener(listener: (text: CharSequence) -> Unit) {
        onInputTextChangeListener = listener
    }

    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit

    override fun afterTextChanged(s: Editable) {
        etInput?.run {
            if (length() == 0) {
                isCursorVisible = false
            }
            onInputTextChangeListener?.invoke(text)
        }
    }

    private fun keyPressed(keyCode: Int) {
        etInput?.run {
            onKeyDown(keyCode, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            val length = length()
            if (length == selectionStart && length == selectionEnd) {
                isCursorVisible = false
            }
        }
    }

    fun release() {
        onInputTextChangeListener = null
        etInput?.removeTextChangedListener(this)
    }
}