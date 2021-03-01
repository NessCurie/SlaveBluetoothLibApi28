package com.github.slavebluetoothsample.ui.controller

import android.content.Context
import android.view.View
import com.github.slavebluetooth.controller.a2dpsink.MusicControlHelper
import com.github.slavebluetoothsample.R
import kotlinx.android.synthetic.main.layout_bt_music.view.*

class MusicController(private val context: Context) {

    private val musicControlHelper: MusicControlHelper by lazy { MusicControlHelper(context) }

    val content: View by lazy { View.inflate(context, R.layout.layout_bt_music, null) }

    init {
        musicControlHelper.setPlayPauseView(content.ivPlayPause,
                context.getDrawable(R.drawable.ic_on_play_selector),
                context.getDrawable(R.drawable.ic_on_pause_selector))
                .setMusicImageView(content.ivMusicImage)
                .setSkipToNextView(content.ivNext)
                .setSkipToPreviousView(content.ivLast)
                .setMediaMataShowView(content.tvMusicName, content.tvSinger)
                .init()
    }

    fun release() {
        musicControlHelper.release()
    }
}