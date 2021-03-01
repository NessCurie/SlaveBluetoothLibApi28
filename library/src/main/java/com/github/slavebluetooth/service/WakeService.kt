package com.github.slavebluetooth.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * 用于launcher进行服务启动把蓝牙应用在后台运行.
 */
class WakeService : Service() {
    override fun onBind(intent: Intent): IBinder? = null
}