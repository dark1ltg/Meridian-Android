package io.github.dark1ltg.meridian.playback

import android.media.AudioAttributes
import android.media.AudioManager
import io.github.dark1ltg.meridian.playback.AudioFocusPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFocusPolicyTest {
    @Test
    fun appSwitchBlipAsksForRecheckThenIgnores() {
        assertEquals(
            Decision.Recheck,
            AudioFocusPolicy.decide(
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.MODE_NORMAL,
                playbackUsages = listOf(AudioAttributes.USAGE_MEDIA),
            ),
        )
        assertEquals(
            Decision.Ignore,
            AudioFocusPolicy.decide(
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.MODE_NORMAL,
                playbackUsages = listOf(AudioAttributes.USAGE_MEDIA),
                recheck = true,
            ),
        )
    }

    @Test
    fun ownDualDeckMediaDoesNotLookLikeAnotherPlayer() {
        assertFalse(
            AudioFocusPolicy.shouldPause(
                AudioManager.MODE_NORMAL,
                listOf(AudioAttributes.USAGE_MEDIA, AudioAttributes.USAGE_MEDIA),
            ),
        )
    }

    @Test
    fun phoneCallPausesAndResumes() {
        assertEquals(
            Decision.PauseAndResume,
            AudioFocusPolicy.decide(
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.MODE_IN_CALL,
                playbackUsages = listOf(AudioAttributes.USAGE_MEDIA),
            ),
        )
        assertEquals(
            Decision.PauseAndResume,
            AudioFocusPolicy.decide(
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.MODE_RINGTONE,
                playbackUsages = emptyList(),
            ),
        )
    }

    @Test
    fun alarmPausesAndResumes() {
        assertEquals(
            Decision.PauseAndResume,
            AudioFocusPolicy.decide(
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.MODE_NORMAL,
                playbackUsages = listOf(
                    AudioAttributes.USAGE_MEDIA,
                    AudioAttributes.USAGE_ALARM,
                ),
            ),
        )
    }

    @Test
    fun otherMediaPausesUntilUser() {
        assertEquals(
            Decision.PauseUntilUser,
            AudioFocusPolicy.decide(
                AudioManager.AUDIOFOCUS_LOSS,
                AudioManager.MODE_NORMAL,
                playbackUsages = listOf(AudioAttributes.USAGE_MEDIA),
            ),
        )
    }

    @Test
    fun notificationAndSonificationDuckNotPause() {
        assertEquals(
            Decision.Duck,
            AudioFocusPolicy.decide(
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
                AudioManager.MODE_NORMAL,
                playbackUsages = listOf(
                    AudioAttributes.USAGE_MEDIA,
                    AudioAttributes.USAGE_NOTIFICATION,
                ),
            ),
        )
        assertEquals(
            Decision.Duck,
            AudioFocusPolicy.decide(
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.MODE_NORMAL,
                playbackUsages = listOf(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION),
            ),
        )
        assertFalse(
            AudioFocusPolicy.shouldPause(
                AudioManager.MODE_NORMAL,
                listOf(AudioAttributes.USAGE_NOTIFICATION),
            ),
        )
        assertTrue(
            AudioFocusPolicy.shouldDuck(listOf(AudioAttributes.USAGE_NOTIFICATION)),
        )
    }

    @Test
    fun gameSoundIsNotAHardInterrupt() {
        assertFalse(
            AudioFocusPolicy.shouldPause(
                AudioManager.MODE_NORMAL,
                listOf(AudioAttributes.USAGE_GAME),
            ),
        )
    }

    @Test
    fun duckBlipWithoutOtherAudioIgnored() {
        assertEquals(
            Decision.Ignore,
            AudioFocusPolicy.decide(
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
                AudioManager.MODE_NORMAL,
                playbackUsages = listOf(AudioAttributes.USAGE_MEDIA),
            ),
        )
    }

    @Test
    fun gainRecovers() {
        assertEquals(
            Decision.Recover,
            AudioFocusPolicy.decide(
                AudioManager.AUDIOFOCUS_GAIN,
                AudioManager.MODE_NORMAL,
                playbackUsages = listOf(AudioAttributes.USAGE_MEDIA),
            ),
        )
    }

    @Test
    fun unknownUsageDoesNotPauseOnItsOwn() {
        assertFalse(
            AudioFocusPolicy.shouldPause(
                AudioManager.MODE_NORMAL,
                listOf(AudioAttributes.USAGE_UNKNOWN),
            ),
        )
    }

    @Test
    fun navigationGuidanceDucksNotPauses() {
        assertEquals(
            Decision.Duck,
            AudioFocusPolicy.decide(
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.MODE_NORMAL,
                playbackUsages = listOf(
                    AudioAttributes.USAGE_MEDIA,
                    AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
                ),
            ),
        )
        assertEquals(
            Decision.Duck,
            AudioFocusPolicy.decide(
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
                AudioManager.MODE_NORMAL,
                playbackUsages = listOf(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE),
            ),
        )
        assertFalse(
            AudioFocusPolicy.shouldPause(
                AudioManager.MODE_NORMAL,
                listOf(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE),
            ),
        )
        assertTrue(
            AudioFocusPolicy.shouldDuck(
                listOf(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE),
            ),
        )
    }
}
