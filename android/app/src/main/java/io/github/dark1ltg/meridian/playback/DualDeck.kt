package io.github.dark1ltg.meridian.playback

import android.os.Handler
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

@UnstableApi
class DualDeck(
    private val a: ExoPlayer,
    private val b: ExoPlayer,
    private val main: Handler,
    private val onActiveChanged: (ExoPlayer) -> Unit,
    private val onNearEnd: () -> Unit,
    private val onEnded: () -> Unit,
    private val onError: (mediaId: String?) -> Unit,
    private val onFadeSettled: () -> Unit,
) {
    var masterVolume: Float = 0.85f
        set(value) {
            field = value.coerceIn(0f, 1f)
            applyVolumes(if (crossfading) lastT else 1f)
        }

    var active: ExoPlayer = a
        private set
    private val idle: ExoPlayer
        get() = if (active === a) b else a

    var crossfading: Boolean = false
        private set
    private var outgoing: ExoPlayer? = null
    private var advanceEmitted = false
    private var incomingEnded = false
    private var lastT = 1f
    private var fadeStartElapsed = 0L
    private var fadeDuration = Crossfade.MS.toLong()
    private var fading = false

    private val fadeTick = object : Runnable {
        override fun run() {
            if (!fading) return
            val elapsed = SystemClock.elapsedRealtime() - fadeStartElapsed
            val linear = (elapsed.toFloat() / fadeDuration.toFloat()).coerceIn(0f, 1f)
            val t = Crossfade.inOutQuad(linear)
            applyVolumes(t)
            if (linear >= 1f) {
                finishFade(immediate = false, notify = true)
            } else {
                main.postDelayed(this, 16L)
            }
        }
    }

    init {
        wire(a)
        wire(b)
        a.volume = masterVolume
        b.volume = 0f
    }

    fun isAudible(): Boolean {
        return (a.playWhenReady && a.mediaItemCount > 0 && a.playbackState != Player.STATE_IDLE) ||
            (b.playWhenReady && b.mediaItemCount > 0 && b.playbackState != Player.STATE_IDLE)
    }

    fun play(item: MediaItem) {
        if (crossfading) finishFade(immediate = true, notify = false)
        val currentId = active.currentMediaItem?.mediaId
        val canFade = Crossfade.shouldFade(active.isPlaying, currentId, item.mediaId)
        advanceEmitted = false
        incomingEnded = false
        if (canFade) {
            startFade(item)
        } else {
            hardCut(item)
            onFadeSettled()
        }
    }

    fun pauseActive() {
        pauseBoth()
    }

    fun pauseBoth() {
        if (crossfading) finishFade(immediate = true, notify = true)
        a.pause()
        b.pause()
    }

    fun resumeActive() {
        active.play()
    }

    fun stopAll(notifySettle: Boolean = true) {
        if (crossfading) finishFade(immediate = true, notify = notifySettle)
        a.stop()
        b.stop()
        a.volume = 0f
        b.volume = 0f
        active.volume = masterVolume
    }

    fun seek(positionMs: Long) {
        if (crossfading) finishFade(immediate = true, notify = true)
        val duration = active.duration
        var pos = positionMs.coerceAtLeast(0L)
        if (duration > 0) pos = pos.coerceAtMost(duration)
        active.seekTo(pos)
        advanceEmitted = false
    }

    fun release() {
        fading = false
        main.removeCallbacks(fadeTick)
        a.release()
        b.release()
    }

    private fun hardCut(item: MediaItem) {
        idle.stop()
        idle.volume = 0f
        active.volume = masterVolume
        active.setMediaItem(item)
        active.prepare()
        active.play()
    }

    private fun startFade(item: MediaItem) {
        val from = active
        val to = idle
        outgoing = from
        active = to
        crossfading = true
        incomingEnded = false
        lastT = 0f
        val duration = from.duration
        fadeDuration = Crossfade.fadeMs(if (duration > 0) duration else 0L).toLong()
        to.volume = 0f
        to.setMediaItem(item)
        to.prepare()
        to.play()
        onActiveChanged(to)
        fading = true
        fadeStartElapsed = SystemClock.elapsedRealtime()
        main.removeCallbacks(fadeTick)
        main.post(fadeTick)
    }

    private fun finishFade(immediate: Boolean, notify: Boolean = !immediate) {
        fading = false
        main.removeCallbacks(fadeTick)
        val out = outgoing
        outgoing = null
        val was = crossfading
        crossfading = false
        var ended = incomingEnded
        incomingEnded = false
        lastT = 1f
        if (out != null) {
            out.stop()
            out.volume = 0f
        }
        active.volume = masterVolume
        if (!was) return
        if (notify) onFadeSettled()
        if (
            !immediate &&
            !ended &&
            active.playbackState == Player.STATE_ENDED &&
            Crossfade.incomingEndIsReal(active.currentPosition, active.duration)
        ) {
            ended = true
        }
        if (!immediate && ended && !advanceEmitted) {
            advanceEmitted = true
            onEnded()
        }
    }

    private fun applyVolumes(t: Float) {
        lastT = t
        val out = outgoing
        if (crossfading && out != null) {
            out.volume = masterVolume * (1f - t)
            active.volume = masterVolume * t
        } else {
            active.volume = masterVolume
            idle.volume = 0f
        }
    }

    private fun wire(deck: ExoPlayer) {
        deck.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState != Player.STATE_ENDED) return
                    if (deck !== active) return
                    if (crossfading) {
                        if (Crossfade.incomingEndIsReal(deck.currentPosition, deck.duration)) {
                            incomingEnded = true
                        }
                        return
                    }
                    if (advanceEmitted) return
                    advanceEmitted = true
                    onEnded()
                }

                override fun onEvents(player: Player, events: Player.Events) {
                    if (deck !== active) return
                    if (Crossfade.shouldArmNearEnd(
                            crossfading,
                            advanceEmitted,
                            deck.duration,
                            deck.currentPosition,
                        )
                    ) {
                        advanceEmitted = true
                        onNearEnd()
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    if (deck !== active) return
                    if (advanceEmitted) return
                    advanceEmitted = true
                    onError(deck.currentMediaItem?.mediaId)
                }
            },
        )
    }
}
