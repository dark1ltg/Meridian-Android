package io.github.dark1ltg.meridian.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceUnlockTest {
    @Test
    fun lockedHidesApp() {
        assertFalse(DeviceUnlock.canRevealApp(locked = true))
    }

    @Test
    fun unlockedShowsApp() {
        assertTrue(DeviceUnlock.canRevealApp(locked = false))
    }
}
