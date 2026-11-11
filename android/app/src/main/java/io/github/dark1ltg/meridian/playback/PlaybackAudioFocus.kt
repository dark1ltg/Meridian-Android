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
    /** True while waiting to see if a bare LOSS_TRANSIENT is a call/alarm or an app-switch blip. */
    private var recheckPending = false
    /** Ignore lingering duck usages after [AudioFocusPolicy.DUCK_STICKY_TICKS] until they disappear. */
    private var ignoreStickyDuck = false
    private var duckStickyTicks = 0

    private val recheckTransient = Runnable {
        apply(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, recheck = true)
    }

    private val unduckStuck = Runnable {
        if (!ducked) return@Runnable
        val usages = playbackUsages()
        if (AudioFocusPolicy.shouldPause(audio.mode, usages)) return@Runnable
        if (AudioFocusPolicy.shouldDuck(usages)) {
            duckStickyTicks += 1
            if (duckStickyTicks < AudioFocusPolicy.DUCK_STICKY_TICKS) {
                armDuckTimeout()
                return@Runnable
            }
            ignoreStickyDuck = true
        }
        duckStickyTicks = 0
        unduckIfNeeded()
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

    /** True while a call/alarm has us paused and we must not start a new track. */
    fun isHoldingForCall(): Boolean = resumeOnGain

    /** True while we yielded to a call/alarm/other player and must not auto-resume. */
    fun isYielding(): Boolean = resumeOnGain || pausedUntilUser

    /** User pause during a call: stay paused after hang-up, never play over the call. */
    fun markPausedUntilUser() {
        pausedUntilUser = true
        resumeOnGain = false
    }

    /** Another stream (call, alarm, voice) is actually playing right now. */
    fun foreignAudioShouldBlock(): Boolean {
        return AudioFocusPolicy.shouldPause(audio.mode, playbackUsages())
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
        if (ok) {
            pausedUntilUser = false
            resumeOnGain = false
            held = true
            registerPlaybackWatch()
        }
        // A denied re-request must not clear [held]. Skip during a call would otherwise
        // stop [onForeignPlaybackChanged] while the decks are still the active player.
        return ok
    }

    fun abandon() {
        main.removeCallbacks(recheckTransient)
        main.removeCallbacks(unduckStuck)
        recheckPending = false
        ignoreStickyDuck = false
        duckStickyTicks = 0
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
                clearRecheck()
                resumeOnGain = false
                pausedUntilUser = true
                unduckIfNeeded()
                onHold()
            }
            Decision.PauseAndResume -> {
                clearRecheck()
                if (!pausedUntilUser) resumeOnGain = true
                unduckIfNeeded()
                onHold()
            }
            Decision.Duck -> {
                clearRecheck()
                ignoreStickyDuck = false
                duckStickyTicks = 0
                duckIfNeeded()
                armDuckTimeout()
            }
            Decision.Recover -> {
                clearRecheck()
                val shouldResume = resumeOnGain && !pausedUntilUser
                resumeOnGain = false
                unduckIfNeeded()
                if (shouldResume) onResume()
            }
            Decision.Recheck -> {
                // Do not duck here: DualDeck's own MEDIA configs would stay quiet for 400ms
                // on every app-switch. Playback callback still pauses/ducks as soon as a
                // ringtone, alarm, or notification usage actually appears.
                recheckPending = true
                main.removeCallbacks(recheckTransient)
                main.postDelayed(recheckTransient, AudioFocusPolicy.TRANSIENT_RECHECK_MS)
            }
            Decision.Ignore -> {
                if (recheck) {
                    clearRecheck()
                    unduckIfNeeded()
                }
            }
        }
    }

    private fun onForeignPlaybackChanged() {
        if (!held) return
        val usages = playbackUsages()
        val mode = audio.mode
        if (ignoreStickyDuck && !AudioFocusPolicy.shouldDuck(usages)) {
            ignoreStickyDuck = false
        }
        if (recheckPending) {
            // Keep the hedge duck; DualDeck's own MEDIA configs must not Recover yet.
            when {
                AudioFocusPolicy.shouldPause(mode, usages) ->
                    apply(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
                AudioFocusPolicy.shouldDuck(usages) ->
                    apply(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
            }
            return
        }
        when {
            AudioFocusPolicy.shouldPause(mode, usages) ->
                apply(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
            AudioFocusPolicy.shouldDuck(usages) && !ignoreStickyDuck ->
                apply(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
            resumeOnGain || ducked ->
                apply(AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun duckIfNeeded() {
        if (ducked) return
        ducked = true
        onDuck()
    }

    private fun unduckIfNeeded() {
        main.removeCallbacks(unduckStuck)
        if (!ducked) return
        ducked = false
        onUnduck()
    }

    private fun armDuckTimeout() {
        main.removeCallbacks(unduckStuck)
        main.postDelayed(unduckStuck, AudioFocusPolicy.DUCK_MAX_MS)
    }

    private fun clearRecheck() {
        recheckPending = false
        main.removeCallbacks(recheckTransient)
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
