package io.github.dark1ltg.meridian.playback

import android.media.AudioAttributes
import android.media.AudioManager

/**
 * When to yield the decks. App-switch and launcher focus blips are not enough;
 * another stream (call, alarm, other media) is.
 */
internal object AudioFocusPolicy {
    enum class Decision {
        /** Another app owns audio; pause until the user hits play. */
        PauseUntilUser,
        /** Call / alarm / other stream; pause and resume on gain. */
        PauseAndResume,
        /** Short notification-style audio; lower volume, keep playing. */
        Duck,
        /** Regain after a yield we chose. */
        Recover,
        /** Keep playing (switching apps, empty focus blip). */
        Ignore,
    }

    fun decide(
        change: Int,
        phoneMode: Int,
        playbackUsages: List<Int>,
        ownMediaCount: Int,
    ): Decision {
        return when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> Decision.Recover
            AudioManager.AUDIOFOCUS_LOSS -> Decision.PauseUntilUser
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (otherAudioPlaying(phoneMode, playbackUsages, ownMediaCount)) {
                    Decision.Duck
                } else {
                    Decision.Ignore
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (otherAudioPlaying(phoneMode, playbackUsages, ownMediaCount)) {
                    Decision.PauseAndResume
                } else {
                    Decision.Ignore
                }
            }
            else -> Decision.Ignore
        }
    }

    fun otherAudioPlaying(
        phoneMode: Int,
        playbackUsages: List<Int>,
        ownMediaCount: Int,
    ): Boolean {
        if (
            phoneMode == AudioManager.MODE_IN_CALL ||
            phoneMode == AudioManager.MODE_IN_COMMUNICATION ||
            phoneMode == AudioManager.MODE_RINGTONE
        ) {
            return true
        }
        if (playbackUsages.any { it in INTERRUPT_USAGES }) return true
        val foreignMedia = playbackUsages.count { it == AudioAttributes.USAGE_MEDIA } - ownMediaCount.coerceAtLeast(0)
        return foreignMedia > 0
    }

    private val INTERRUPT_USAGES = setOf(
        AudioAttributes.USAGE_ALARM,
        AudioAttributes.USAGE_NOTIFICATION_RINGTONE,
        AudioAttributes.USAGE_VOICE_COMMUNICATION,
        AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING,
        AudioAttributes.USAGE_ASSISTANT,
        AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
        AudioAttributes.USAGE_ASSISTANCE_SONIFICATION,
        AudioAttributes.USAGE_NOTIFICATION,
        AudioAttributes.USAGE_GAME,
    )
}
