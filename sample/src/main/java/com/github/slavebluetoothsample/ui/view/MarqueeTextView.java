package com.github.slavebluetoothsample.ui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

/**
 * 带跑马灯的TextView,只是将isFocused获取点击焦点,因为只有在获取焦点的时候才会跑马
 */
public class MarqueeTextView extends TextView {

    public MarqueeTextView(Context context) {
        super(context);
    }

    public MarqueeTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MarqueeTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /*@Override
    public boolean isFocused() {
        return true;
    }*/

    @Override
    public boolean isSelected() {
        return true;
    }
}