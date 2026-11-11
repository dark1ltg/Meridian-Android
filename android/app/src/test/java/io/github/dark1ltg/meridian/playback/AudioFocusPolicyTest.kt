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
    fun appSwitchBlipKeepsPlaying() {
        assertEquals(
            Decision.Ignore,
            AudioFocusPolicy.decide(
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.MODE_NORMAL,
                playbackUsages = listOf(AudioAttributes.USAGE_MEDIA),
                ownMediaCount = 1,
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
                ownMediaCount = 1,
            ),
        )
        assertEquals(
            Decision.PauseAndResume,
            AudioFocusPolicy.decide(
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.MODE_RINGTONE,
                playbackUsages = emptyList(),
                ownMediaCount = 1,
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
                ownMediaCount = 1,
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
                playbackUsages = listOf(
                    AudioAttributes.USAGE_MEDIA,
                    AudioAttributes.USAGE_MEDIA,
                ),
                ownMediaCount = 1,
            ),
        )
    }

    @Test
    fun otherMediaOnTransientStillYields() {
        assertEquals(
            Decision.PauseAndResume,
            AudioFocusPolicy.decide(
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.MODE_NORMAL,
                playbackUsages = listOf(
                    AudioAttributes.USAGE_MEDIA,
                    AudioAttributes.USAGE_MEDIA,
                ),
                ownMediaCount = 1,
            ),
        )
    }

    @Test
    fun notificationDucks() {
        assertEquals(
            Decision.Duck,
            AudioFocusPolicy.decide(
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
                AudioManager.MODE_NORMAL,
                playbackUsages = listOf(
                    AudioAttributes.USAGE_MEDIA,
                    AudioAttributes.USAGE_NOTIFICATION,
                ),
                ownMediaCount = 1,
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
                ownMediaCount = 1,
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
                ownMediaCount = 1,
            ),
        )
    }

    @Test
    fun otherAudioDetectsCallAndAlarmNotOwnMedia() {
        assertFalse(
            AudioFocusPolicy.otherAudioPlaying(
                AudioManager.MODE_NORMAL,
                listOf(AudioAttributes.USAGE_MEDIA),
                ownMediaCount = 1,
            ),
        )
        assertTrue(
            AudioFocusPolicy.otherAudioPlaying(
                AudioManager.MODE_IN_COMMUNICATION,
                emptyList(),
                ownMediaCount = 1,
            ),
        )
        assertTrue(
            AudioFocusPolicy.otherAudioPlaying(
                AudioManager.MODE_NORMAL,
                listOf(AudioAttributes.USAGE_MEDIA, AudioAttributes.USAGE_ALARM),
                ownMediaCount = 1,
            ),
        )
        assertTrue(
            AudioFocusPolicy.otherAudioPlaying(
                AudioManager.MODE_NORMAL,
                listOf(AudioAttributes.USAGE_MEDIA, AudioAttributes.USAGE_MEDIA),
                ownMediaCount = 1,
            ),
        )
    }
}
