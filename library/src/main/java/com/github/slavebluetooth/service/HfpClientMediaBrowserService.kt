package com.github.slavebluetooth.service

import android.app.PendingIntent
import android.bluetooth.BluetoothHeadsetClientCall
import android.content.*
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserServiceCompat
import android.support.v4.media.session.MediaButtonReceiver
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import com.android.settingslib.bluetooth.HfpClientProfile
import com.github.slavebluetooth.R
import com.github.slavebluetooth.controller.CoreController
import com.github.slavebluetooth.controller.hfpclient.InCallCallBack
import com.github.slavebluetooth.controller.hfpclient.InCallPresenter
import com.github.slavebluetooth.model.BluetoothCallInfo
import com.github.slavebluetooth.utils.resettableLazy
import com.github.slavebluetooth.utils.resettableManager


class HfpClientMediaBrowserService : MediaBrowserServiceCompat(), InCallCallBack {

    companion object {
        private const val ACTION_SCREEN_OFF = "android.intent.action.SCREEN_OFF"
        private const val ACTION_SCREEN_ON = "android.intent.action.SCREEN_ON"
    }

    private val resettableLazyManager = resettableManager()
    private val mediaPlayerLazy = resettableLazy(resettableLazyManager) {
        val mediaPlayer = MediaPlayer()
        mediaPlayer.isLooping = true
        mediaPlayer.setDataSource(this, Uri.parse("android.resource://" + packageName + "/" + R.raw.empty))
        mediaPlayer.setOnPreparedListener { mediaPlayer.start() }
        mediaPlayer
    }
    private val mediaPlayer: MediaPlayer by mediaPlayerLazy

    private val mediaSession: MediaSessionCompat by lazy {
        val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
        mediaButtonIntent.setClass(this, MediaButtonReceiver::class.java)
        val mbrIntent = PendingIntent.getBroadcast(this, 0, mediaButtonIntent, 0)
        val mbrComponent = ComponentName(this, MediaButtonReceiver::class.java)
        val mediaSession = MediaSessionCompat(this, packageName + this::class.java.simpleName,
                mbrComponent, mbrIntent)
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
        mediaSession.setCallback(mediaButtonCallback)
        try {
            mediaSession.isActive = true
        } catch (e: NullPointerException) {
            // Some versions of KitKat do not support AudioManager.registerMediaButtonIntent
            // with a PendingIntent. They will throw a NullPointerException, in which case
            // they should be able to activate a MediaSessionCompat with only transport
            // controls.
            mediaSession.isActive = false
            mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            mediaSession.isActive = true
        }
        sessionToken = mediaSession.sessionToken
        mediaSession
    }

    private val hfpClientProfile: HfpClientProfile by lazy { CoreController.hfpClientProfile }

    private val mediaButtonCallback = object : MediaSessionCompat.Callback() {

        override fun onPlay() {
            if (InCallPresenter.hasCall()) {
                hfpClientProfile.acceptCall()
            }
        }

        override fun onPause() {
            if (InCallPresenter.hasCall()) {
                InCallPresenter.onTerminateCall(hfpClientProfile.terminateCall())
            }
        }
    }

    private val playbackStateAction = (PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY)

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.e("wtf", "screenStateReceiver intent?.action = ${intent?.action}")
            when (intent?.action) {
                ACTION_SCREEN_ON -> {
                    if (!mediaPlayerLazy.isInitialized()) {
                        mediaPlayer.prepareAsync()
                    }
                }
                ACTION_SCREEN_OFF -> {
                    mediaPlayer.release()
                    resettableLazyManager.reset()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        InCallPresenter.addInCallListener(this)
        mediaPlayer.prepareAsync()

        val screenStateFilter = IntentFilter()
        screenStateFilter.addAction(ACTION_SCREEN_ON)
        screenStateFilter.addAction(ACTION_SCREEN_OFF)
        registerReceiver(screenStateReceiver, screenStateFilter)
    }

    override fun onDestroy() {
        unregisterReceiver(screenStateReceiver)
        InCallPresenter.removeInCallListener(this)
        mediaPlayer.release()
        mediaSession.release()
        super.onDestroy()
    }

    override fun updateName(name: String) = Unit
    override fun updateCallTime(time: String) = Unit

    override fun updateAll(callInfo: BluetoothCallInfo, state: Int, callTime: String) {
        when (state) {
            BluetoothHeadsetClientCall.CALL_STATE_HELD,         //保持通话
            BluetoothHeadsetClientCall.CALL_STATE_ACTIVE -> {    //通话中
                setPlaying()
            }
            BluetoothHeadsetClientCall.CALL_STATE_DIALING,
            BluetoothHeadsetClientCall.CALL_STATE_ALERTING -> {  //呼出
                setPlaying()
            }
            BluetoothHeadsetClientCall.CALL_STATE_WAITING,       //通话中有呼入
            BluetoothHeadsetClientCall.CALL_STATE_INCOMING -> {  //呼入 会一直调用
                setPlaying()
            }
        }
    }

    override fun onCallClear() {
        setNone()
    }

    private fun setPlaying() {
        mediaPlayer.start()
        mediaSession.setPlaybackState(PlaybackStateCompat.Builder().setState(
                PlaybackStateCompat.STATE_PLAYING, 0, 1.0f).setActions(playbackStateAction).build())
    }

    private fun setPause() {
        mediaPlayer.pause()
        mediaSession.setPlaybackState(PlaybackStateCompat.Builder().setState(
                PlaybackStateCompat.STATE_PAUSED, 0, 1.0f).setActions(playbackStateAction).build())
    }

    private fun setNone() {
        mediaPlayer.pause()
        mediaSession.setPlaybackState(PlaybackStateCompat.Builder().setState(
                PlaybackStateCompat.STATE_NONE, 0, 1.0f).setActions(0).build())
    }

    private fun forceGetCallbackWhenPause() {
        setPlaying()
        setPause()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int,
                           rootHints: Bundle?): BrowserRoot = BrowserRoot("ID_ROOT", null)

    override fun onLoadChildren(parentId: String,
                                result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        result.detach()
    }
}