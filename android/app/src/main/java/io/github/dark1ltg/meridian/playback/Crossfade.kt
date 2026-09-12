package io.github.dark1ltg.meridian.playback

/**
 * Same timing rules as desktop [meridian.player]: always-on ~3s overlap,
 * InOutQuad ramps, and conservative near-end detection so VBR durations
 * do not fire a fade in the first seconds of a track.
 */
object Crossfade {
    const val MS = 3000
    const val MIN_FADE_MS = 400
    const val SHORT_TRACK_MS = 12_000
    const val NEAR_END_GUARD_MS = 10_000

    fun fadeMs(durationMs: Long): Int {
        if (durationMs <= 0L) return MS
        return maxOf(MIN_FADE_MS, minOf(MS, (durationMs / 3L).toInt()))
    }

    fun shouldFade(playing: Boolean, currentId: String?, newId: String): Boolean {
        return playing && !currentId.isNullOrEmpty() && currentId != newId
    }

    fun shouldArmNearEnd(
        crossfading: Boolean,
        advanceEmitted: Boolean,
        durationMs: Long,
        positionMs: Long,
    ): Boolean {
        if (crossfading || advanceEmitted) return false
        if (durationMs < SHORT_TRACK_MS) return false
        if (positionMs < NEAR_END_GUARD_MS) return false
        val fade = fadeMs(durationMs)
        val remaining = durationMs - positionMs
        return remaining in 0..fade.toLong()
    }

    fun incomingEndIsReal(positionMs: Long, durationMs: Long): Boolean {
        if (durationMs > 0 && durationMs <= MS + 1000) return true
        return positionMs >= 500
    }

    fun inOutQuad(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return if (x < 0.5f) {
            2f * x * x
        } else {
            1f - 2f * (1f - x) * (1f - x)
        }
    }
}
