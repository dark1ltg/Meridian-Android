package io.github.dark1ltg.meridian.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import io.github.dark1ltg.meridian.playback.AudioFocusPolicy.Decision

/**
 * One focus owner for both crossfade decks. ExoPlayer audio-focus handling is
 * off so the incoming deck cannot pause the outgoing deck mid-fade.
 *
 * Yields only when another stream is actually playing (calls, alarms, other
 * media). Switching apps without audio does not pause.
 */
class PlaybackAudioFocus(
    context: Context,
    private val ownMediaCount: () -> Int = { 0 },
    private val onHold: () -> Unit,
    private val onResume: () -> Unit,
    private val onDuck: () -> Unit = {},
    private val onUnduck: () -> Unit = {},
) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var held = false
    private var watchingPlayback = false
    private var resumeOnGain = false
    private var pausedUntilUser = false
    private var ducked = false

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        apply(change)
    }

    private val playbackCallback = if (Build.VERSION.SDK_INT >= 26) {
        object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: MutableList<android.media.AudioPlaybackConfiguration>?) {
                if (!held) return
                if (otherAudioPlaying()) {
                    apply(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
                } else if (resumeOnGain || ducked) {
                    apply(AudioManager.AUDIOFOCUS_GAIN)
                }
            }
        }
    } else {
        null
    }

    private val request: AudioFocusRequest? =
        if (Build.VERSION.SDK_INT >= 26) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener(listener)
                .build()
        } else {
            null
        }

    fun request(): Boolean {
        val ok = if (Build.VERSION.SDK_INT >= 26 && request != null) {
            audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audio.requestAudioFocus(
                listener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        if (ok) pausedUntilUser = false
        held = ok
        if (ok) registerPlaybackWatch()
        return ok
    }

    fun abandon() {
        if (!held && !ducked) return
        held = false
        resumeOnGain = false
        pausedUntilUser = false
        if (ducked) {
            ducked = false
            onUnduck()
        }
        unregisterPlaybackWatch()
        if (Build.VERSION.SDK_INT >= 26 && request != null) {
            audio.abandonAudioFocusRequest(request)
        } else {
            @Suppress("DEPRECATION")
            audio.abandonAudioFocus(listener)
        }
    }

    internal fun apply(change: Int) {
        when (
            AudioFocusPolicy.decide(
                change,
                audio.mode,
                playbackUsages(),
                ownMediaCount(),
            )
        ) {
            Decision.PauseUntilUser -> {
                resumeOnGain = false
                pausedUntilUser = true
                unduckIfNeeded()
                onHold()
            }
            Decision.PauseAndResume -> {
                if (!pausedUntilUser) resumeOnGain = true
                unduckIfNeeded()
                onHold()
            }
            Decision.Duck -> {
                if (!ducked) {
                    ducked = true
                    onDuck()
                }
            }
            Decision.Recover -> {
                val shouldResume = resumeOnGain && !pausedUntilUser
                resumeOnGain = false
                unduckIfNeeded()
                if (shouldResume) onResume()
            }
            Decision.Ignore -> Unit
        }
    }

    private fun unduckIfNeeded() {
        if (!ducked) return
        ducked = false
        onUnduck()
    }

    private fun otherAudioPlaying(): Boolean {
        return AudioFocusPolicy.otherAudioPlaying(audio.mode, playbackUsages(), ownMediaCount())
    }

    private fun playbackUsages(): List<Int> {
        if (Build.VERSION.SDK_INT < 26) return emptyList()
        return audio.activePlaybackConfigurations.map { it.audioAttributes.usage }
    }

    private fun registerPlaybackWatch() {
        if (watchingPlayback) return
        if (Build.VERSION.SDK_INT >= 26) {
            val cb = playbackCallback ?: return
            runCatching {
                audio.registerAudioPlaybackCallback(cb, null)
                watchingPlayback = true
            }
        }
    }

    private fun unregisterPlaybackWatch() {
        if (!watchingPlayback) return
        if (Build.VERSION.SDK_INT >= 26) {
            val cb = playbackCallback ?: return
            runCatching { audio.unregisterAudioPlaybackCallback(cb) }
        }
        watchingPlayback = false
    }
}
