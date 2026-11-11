package io.github.dark1ltg.meridian.playback

import android.media.AudioAttributes
import android.media.AudioManager

/**
 * When to yield the decks. App-switch and our own DualDeck config changes are
 * not enough; a call, alarm, or another real stream is.
 */
internal object AudioFocusPolicy {
    /** Wait for ringtone / MODE_IN_CALL to show up after a bare transient loss. */
    const val TRANSIENT_RECHECK_MS = 400L

    /** Cap a duck so a stuck OEM notification config cannot leave volume at 0.2 forever. */
    const val DUCK_MAX_MS = 5_000L

    /** Consecutive duck timeouts before a stuck OEM usage is ignored (~30s). */
    const val DUCK_STICKY_TICKS = 6

    enum class Decision {
        PauseUntilUser,
        PauseAndResume,
        Duck,
        Recover,
        Ignore,
        /** Transient loss with no stream yet — check again shortly. */
        Recheck,
    }

    fun decide(
        change: Int,
        phoneMode: Int,
        playbackUsages: List<Int>,
        recheck: Boolean = false,
    ): Decision {
        return when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> Decision.Recover
            AudioManager.AUDIOFOCUS_LOSS -> Decision.PauseUntilUser
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                when {
                    shouldPause(phoneMode, playbackUsages) -> Decision.PauseAndResume
                    shouldDuck(playbackUsages) -> Decision.Duck
                    else -> Decision.Ignore
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                when {
                    shouldPause(phoneMode, playbackUsages) -> Decision.PauseAndResume
                    shouldDuck(playbackUsages) -> Decision.Duck
                    recheck -> Decision.Ignore
                    else -> Decision.Recheck
                }
            }
            else -> Decision.Ignore
        }
    }

    fun shouldPause(phoneMode: Int, playbackUsages: List<Int>): Boolean {
        if (inPhoneCall(phoneMode)) return true
        return playbackUsages.any { it in PAUSE_USAGES }
    }

    fun shouldDuck(playbackUsages: List<Int>): Boolean {
        return playbackUsages.any { it in DUCK_USAGES }
    }

    fun inPhoneCall(phoneMode: Int): Boolean {
        return phoneMode == AudioManager.MODE_IN_CALL ||
            phoneMode == AudioManager.MODE_IN_COMMUNICATION ||
            phoneMode == AudioManager.MODE_RINGTONE
    }

    private val PAUSE_USAGES = setOf(
        AudioAttributes.USAGE_ALARM,
        AudioAttributes.USAGE_NOTIFICATION_RINGTONE,
        AudioAttributes.USAGE_VOICE_COMMUNICATION,
        AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING,
        AudioAttributes.USAGE_ASSISTANT,
    )

    private val DUCK_USAGES = setOf(
        AudioAttributes.USAGE_NOTIFICATION,
        AudioAttributes.USAGE_NOTIFICATION_EVENT,
        AudioAttributes.USAGE_ASSISTANCE_SONIFICATION,
        // Turn-by-turn prompts are short; pausing on every instruction is worse than ducking.
        AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
    )
}
