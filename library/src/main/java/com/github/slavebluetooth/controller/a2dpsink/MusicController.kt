package com.github.slavebluetooth.controller.a2dpsink

import android.bluetooth.BluetoothA2dpSink
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.*
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.TextUtils
import com.github.slavebluetooth.utils.resettableLazy
import com.github.slavebluetooth.utils.resettableManager

@Suppress("unused")
open class MusicController(private val context: Context) {

    companion object {
        private const val NOT_SUPPORTED = "NOT_SUPPORTED"
        private const val NOT_PROVIDED = "Not Provided"

        private const val SYSTEM_BLUETOOTH_PACKAGE = "com.android.bluetooth"
        private const val SYSTEM_BLUETOOTH_MEDIA_BROWSER = "com.android.bluetooth.a2dpsink.mbs.A2dpMediaBrowserService"
        private const val CUSTOM_ACTION_VOL_UP = "com.android.bluetooth.a2dpsink.mbs.CUSTOM_ACTION_VOL_UP"
        private const val CUSTOM_ACTION_VOL_DN = "com.android.bluetooth.a2dpsink.mbs.CUSTOM_ACTION_VOL_DN"
        private const val CUSTOM_ACTION_GET_PLAY_STATUS_NATIVE = "com.android.bluetooth.a2dpsink.mbs.CUSTOM_ACTION_GET_PLAY_STATUS_NATIVE"
        private const val CUSTOM_ACTION_GET_MEDIA_BUTTON_WHEN_PAUSE = "com.android.bluetooth.a2dpsink.mbs.CUSTOM_ACTION_GET_MEDIA_BUTTON_WHEN_PAUSE"
        private const val CUSTOM_ACTION_GIVE_UP_MEDIA_BUTTON_WHEN_PAUSE = "com.android.bluetooth.a2dpsink.mbs.CUSTOM_ACTION_GIVE_UP_MEDIA_BUTTON_WHEN_PAUSE"

        fun isMediaMetaValueEmpty(value: String?): Boolean {
            return TextUtils.isEmpty(value) || NOT_SUPPORTED == value || NOT_PROVIDED == value
        }
    }

    private var onConnectStateChangeListener: ((state: Int) -> Unit)? = null
    private var onPlaybackStateChangeListener: ((state: PlaybackState) -> Unit)? = null
    private var onMetadataChangedListener: ((metadata: MediaMetadata) -> Unit)? = null
    private val bluetoothAdapter: BluetoothAdapter by lazy { BluetoothAdapter.getDefaultAdapter() }
    private val mediaCallback: MediaControllerCompat.Callback by lazy {
        object : MediaControllerCompat.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackStateCompat) {
                onPlaybackStateChanged(state.playbackState as PlaybackState)
            }

            override fun onMetadataChanged(metadata: MediaMetadataCompat) {
                onMetadataChanged(metadata.mediaMetadata as MediaMetadata)
            }
        }
    }

    private val resettableManager = resettableManager()
    private val mediaControllerCompat: MediaControllerCompat by resettableLazy(resettableManager) {
        val mediaControllerCompat = MediaControllerCompat(context, mediaBrowser.sessionToken)
        mediaControllerCompat.registerCallback(mediaCallback)
        mediaControllerCompat
    }
    private val transportControls: MediaControllerCompat.TransportControls by resettableLazy(resettableManager) {
        mediaControllerCompat.transportControls
    }

    private val connectionCallback: MediaBrowserCompat.ConnectionCallback by lazy {
        object : MediaBrowserCompat.ConnectionCallback() {
            override fun onConnected() {   //服务端接受连接
                refreshInfo()
            }

            //与服务断开回调,服务被杀掉重启会调用,之后会重新自动连接调用onConnected,但是重连的没有效果
            override fun onConnectionSuspended() {
            }

            override fun onConnectionFailed() {  //连接失败回调
                resettableManager.reset()
                mediaBrowser.connect()
            }
        }
    }

    private val mediaBrowserLazy = resettableLazy(resettableManager) {
        MediaBrowserCompat(context, ComponentName(SYSTEM_BLUETOOTH_PACKAGE,
                SYSTEM_BLUETOOTH_MEDIA_BROWSER), connectionCallback, null)
    }
    private val mediaBrowser: MediaBrowserCompat by mediaBrowserLazy

    private val a2dpSinkStateChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (BluetoothA2dpSink.ACTION_CONNECTION_STATE_CHANGED == intent.action) {
                val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
                //val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                onA2dpSinkConnectStateChange(state)
            }
        }
    }

    private val bluetoothEnabledStateChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED == intent.action) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR)
                if (state == BluetoothAdapter.STATE_TURNING_OFF) {
                    //Reregister Profile Broadcast Receiver as part of TURN OFF
                    //这个重新注册广播是参照源码,意义未明
                    context.unregisterReceiver(a2dpSinkStateChangeReceiver)
                    registerProfileReceiver()
                    onA2dpSinkConnectStateChange(BluetoothProfile.STATE_DISCONNECTED)
                }
            }
        }
    }

    var playState = PlaybackState.STATE_NONE
    var playbackState: PlaybackState = PlaybackState.Builder()
            .setState(PlaybackState.STATE_ERROR, 0, 0f).build()
    var mediaMetadata: MediaMetadata = MediaMetadata.Builder().build()

    @Suppress("MemberVisibilityCanBePrivate")
    fun getConnectState() = bluetoothAdapter.getProfileConnectionState(11)

    fun isConnected() = getConnectState() == BluetoothProfile.STATE_CONNECTED

    fun setOnConnectStateChangeListener(listener: (state: Int) -> Unit) {
        onConnectStateChangeListener = listener
        onConnectStateChangeListener?.invoke(getConnectState())
    }

    fun setOnPlaybackStateChangeListener(listener: ((state: PlaybackState) -> Unit)) {
        onPlaybackStateChangeListener = listener
        onPlaybackStateChangeListener?.invoke(playbackState)
    }

    fun setOnMetadataChangedListener(listener: ((metadata: MediaMetadata) -> Unit)) {
        onMetadataChangedListener = listener
        onMetadataChangedListener?.invoke(mediaMetadata)
    }

    fun init() {
        val enabledStateFilter = IntentFilter()
        enabledStateFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(bluetoothEnabledStateChangeReceiver, enabledStateFilter)
        registerProfileReceiver()
        if (isConnected() && !mediaBrowserLazy.isInitialized() && !mediaBrowser.isConnected) {
            mediaBrowser.connect()
        }
    }

    private fun registerProfileReceiver() {
        val a2dpSinkStateFilter = IntentFilter()
        a2dpSinkStateFilter.addAction(BluetoothA2dpSink.ACTION_CONNECTION_STATE_CHANGED)
        context.registerReceiver(a2dpSinkStateChangeReceiver, a2dpSinkStateFilter)
    }

    private fun refreshInfo() {
        if (mediaBrowserLazy.isInitialized() && mediaBrowser.isConnected) {
            val state = mediaControllerCompat.playbackState
            if (state != null) {
                onPlaybackStateChanged(state.playbackState as PlaybackState)
            } else {
                onPlaybackStateChanged(PlaybackState.Builder()
                        .setState(PlaybackState.STATE_ERROR, 0, 0f).build())
            }
            val metadata = mediaControllerCompat.metadata
            if (metadata != null) {
                onMetadataChanged(metadata.mediaMetadata as MediaMetadata)
            } else {
                onMetadataChanged(MediaMetadata.Builder().build())
            }
        }
    }

    /**
     * 准备,会获取音频焦点
     */
    fun prepare() {
        transportControls.prepare()
    }

    fun playPause() {
        if (playState == PlaybackState.STATE_PLAYING
                || playState == PlaybackState.STATE_FAST_FORWARDING
                || playState == PlaybackState.STATE_REWINDING) {
            transportControls.pause()
        } else {
            transportControls.play()
        }
    }

    fun play() = transportControls.play()

    fun pause() = transportControls.pause()

    fun stop() = transportControls.stop()

    fun skipToNext() = transportControls.skipToNext()

    fun skipToPrevious() = transportControls.skipToPrevious()

    fun rewind() = transportControls.rewind()

    fun fastForward() = transportControls.fastForward()

    fun volUp() = transportControls.sendCustomAction(CUSTOM_ACTION_VOL_UP, null)

    fun volDown() = transportControls.sendCustomAction(CUSTOM_ACTION_VOL_DN, null)

    fun getMediaButtonWhenPause() = transportControls.sendCustomAction(CUSTOM_ACTION_GET_MEDIA_BUTTON_WHEN_PAUSE, null)

    fun giveUpMediaButtonWhenPause() = transportControls.sendCustomAction(CUSTOM_ACTION_GIVE_UP_MEDIA_BUTTON_WHEN_PAUSE, null)

    fun refreshPlayStatus() = transportControls.sendCustomAction(CUSTOM_ACTION_GET_PLAY_STATUS_NATIVE, null)

    open fun onMetadataChanged(metadata: MediaMetadata) {
        this.mediaMetadata = metadata
        onMetadataChangedListener?.invoke(metadata)
    }

    open fun onPlaybackStateChanged(playbackState: PlaybackState) {
        this.playbackState = playbackState
        playState = playbackState.state
        onPlaybackStateChangeListener?.invoke(playbackState)
    }

    open fun onA2dpSinkConnectStateChange(state: Int) {
        onConnectStateChangeListener?.invoke(state)
        if (state == BluetoothProfile.STATE_CONNECTED) {
            mediaBrowser.connect()
        } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
            mediaBrowser.disconnect()
            resettableManager.reset()
        }
    }

    fun release() {
        onConnectStateChangeListener = null
        onPlaybackStateChangeListener = null
        onMetadataChangedListener = null
        context.unregisterReceiver(bluetoothEnabledStateChangeReceiver)
        context.unregisterReceiver(a2dpSinkStateChangeReceiver)
        if (mediaBrowserLazy.isInitialized()) mediaBrowser.disconnect()
    }
}