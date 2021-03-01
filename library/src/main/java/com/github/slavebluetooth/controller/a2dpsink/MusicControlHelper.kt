package com.github.slavebluetooth.controller.a2dpsink

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.view.View
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.widget.TextView
import com.github.slavebluetooth.R

@Suppress("unused")
class MusicControlHelper(context: Context) : MusicController(context) {

    private var playPauseView: View? = null
    private var onPlayDrawable: Drawable? = null
    private var onPauseDrawable: Drawable? = null
    private var musicImageView: View? = null
    private var tvTitle: TextView? = null
    private var tvArtist: TextView? = null
    private var tvAlbum: TextView? = null

    private val ivMusicImageRotationAnimator: ObjectAnimator by lazy {
        @SuppressLint("ObjectAnimatorBinding")
        val ra = ObjectAnimator.ofFloat(musicImageView, "rotation", 0f, 360f)
        ra.duration = 15000
        ra.repeatCount = Animation.INFINITE
        ra.interpolator = LinearInterpolator()
        ra.repeatMode = ObjectAnimator.RESTART
        ra.start()
        ra.pause()
        ra
    }

    fun setMusicImageView(view: View): MusicControlHelper {
        musicImageView = view
        return this
    }

    fun setPlayPauseView(view: View, onPlayDrawable: Drawable? = null,
                         onPauseDrawable: Drawable? = null): MusicControlHelper {
        view.setOnClickListener { playPause() }
        playPauseView = view
        this.onPlayDrawable = onPlayDrawable
        this.onPauseDrawable = onPauseDrawable
        onPlaybackStateChanged(playbackState)
        return this
    }

    fun setSkipToNextView(view: View): MusicControlHelper {
        view.setOnClickListener { skipToNext() }
        return this
    }

    fun setSkipToPreviousView(view: View): MusicControlHelper {
        view.setOnClickListener { skipToPrevious() }
        return this
    }

    fun setStopView(view: View): MusicControlHelper {
        view.setOnClickListener { stop() }
        return this
    }

    fun setRewindView(view: View): MusicControlHelper {
        view.setOnClickListener { rewind() }
        return this
    }

    fun setFastForwardView(view: View): MusicControlHelper {
        view.setOnClickListener { fastForward() }
        return this
    }

    fun setMediaMataShowView(tvTitle: TextView, tvArtist: TextView? = null,
                             tvAlbum: TextView? = null): MusicControlHelper {
        this.tvTitle = tvTitle
        this.tvArtist = tvArtist
        this.tvAlbum = tvAlbum
        onMetadataChanged(mediaMetadata)
        return this
    }

    override fun onMetadataChanged(metadata: MediaMetadata) {
        super.onMetadataChanged(metadata)
        tvTitle?.run {
            val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            if (isMediaMetaValueEmpty(title)) {
                setText(R.string.unknown)
            } else {
                text = title
            }
        }
        tvArtist?.run {
            val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            if (isMediaMetaValueEmpty(artist)) {
                setText(R.string.unknown)
            } else {
                text = artist
            }
        }
        tvAlbum?.run {
            val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)
            if (isMediaMetaValueEmpty(album)) {
                setText(R.string.unknown)
            } else {
                text = album
            }
        }
    }

    override fun onPlaybackStateChanged(playbackState: PlaybackState) {
        super.onPlaybackStateChanged(playbackState)
        if (playbackState.state == PlaybackState.STATE_PLAYING
                || playState == PlaybackState.STATE_FAST_FORWARDING
                || playState == PlaybackState.STATE_REWINDING) {
            playPauseView?.run { onPlayDrawable?.run { background = onPlayDrawable } }
            musicImageView?.run { ivMusicImageRotationAnimator.resume() }
        } else {
            playPauseView?.run { onPauseDrawable?.run { background = onPauseDrawable } }
            musicImageView?.run { ivMusicImageRotationAnimator.pause() }
        }
    }

    override fun onA2dpSinkConnectStateChange(state: Int) {
        super.onA2dpSinkConnectStateChange(state)
        musicImageView?.run { ivMusicImageRotationAnimator.pause() }
        musicImageView?.rotation = 0f
        playPauseView?.run { onPauseDrawable?.run { background = onPauseDrawable } }
    }
}