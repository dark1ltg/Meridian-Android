package io.github.dark1ltg.meridian.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import io.github.dark1ltg.meridian.playback.AudioFocusPolicy.Decision

/**
 * One focus owner for both crossfade decks. ExoPlayer audio-focus handling is
 * off so the incoming deck cannot pause the outgoing deck mid-fade.
 *
 * Yields only when another stream is actually playing (calls, alarms, other
 * media). Switching apps without audio does not pause. Our own DualDeck
 * config changes are not treated as a foreign player.
 */
class PlaybackAudioFocus(
    context: Context,
    private val onHold: () -> Unit,
    private val onResume: () -> Unit,
    private val onDuck: () -> Unit = {},
    private val onUnduck: () -> Unit = {},
) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val main = Handler(Looper.getMainLooper())
    private var held = false
    private var watchingPlayback = false
    private var resumeOnGain = false
    private var pausedUntilUser = false
    private var ducked = false

    private val recheckTransient = Runnable {
        apply(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, recheck = true)
    }

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        main.post { apply(change) }
    }

    private val playbackCallback = if (Build.VERSION.SDK_INT >= 26) {
        object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: MutableList<android.media.AudioPlaybackConfiguration>?) {
                main.post { onForeignPlaybackChanged() }
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

    /** True while we yielded to a call/alarm/other player and must not start a new track. */
    fun isYielding(): Boolean = resumeOnGain || pausedUntilUser

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
        main.removeCallbacks(recheckTransient)
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

    internal fun apply(change: Int, recheck: Boolean = false) {
        when (
            AudioFocusPolicy.decide(
                change,
                audio.mode,
                playbackUsages(),
                recheck = recheck,
            )
        ) {
            Decision.PauseUntilUser -> {
                main.removeCallbacks(recheckTransient)
                resumeOnGain = false
                pausedUntilUser = true
                unduckIfNeeded()
                onHold()
            }
            Decision.PauseAndResume -> {
                main.removeCallbacks(recheckTransient)
                if (!pausedUntilUser) resumeOnGain = true
                unduckIfNeeded()
                onHold()
            }
            Decision.Duck -> {
                main.removeCallbacks(recheckTransient)
                if (!ducked) {
                    ducked = true
                    onDuck()
                }
            }
            Decision.Recover -> {
                main.removeCallbacks(recheckTransient)
                val shouldResume = resumeOnGain && !pausedUntilUser
                resumeOnGain = false
                unduckIfNeeded()
                if (shouldResume) onResume()
            }
            Decision.Recheck -> {
                main.removeCallbacks(recheckTransient)
                main.postDelayed(recheckTransient, AudioFocusPolicy.TRANSIENT_RECHECK_MS)
            }
            Decision.Ignore -> {
                if (recheck) main.removeCallbacks(recheckTransient)
            }
        }
    }

    private fun onForeignPlaybackChanged() {
        if (!held) return
        val usages = playbackUsages()
        val mode = audio.mode
        when {
            AudioFocusPolicy.shouldPause(mode, usages) ->
                apply(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
            AudioFocusPolicy.shouldDuck(usages) ->
                apply(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
            resumeOnGain || ducked ->
                apply(AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun unduckIfNeeded() {
        if (!ducked) return
        ducked = false
        onUnduck()
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
                audio.registerAudioPlaybackCallback(cb, main)
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
