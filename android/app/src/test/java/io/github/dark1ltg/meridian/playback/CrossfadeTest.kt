package io.github.dark1ltg.meridian.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossfadeTest {
    @Test
    fun fadeLengthClampsToTrackThird() {
        assertEquals(Crossfade.MS, Crossfade.fadeMs(0))
        assertEquals(400, Crossfade.fadeMs(1_200))
        assertEquals(Crossfade.MS, Crossfade.fadeMs(180_000))
        assertEquals(5_000 / 3, Crossfade.fadeMs(5_000))
    }

    @Test
    fun fadeOnlyWhenAlreadyPlayingADifferentTrack() {
        assertFalse(Crossfade.shouldFade(false, "1", "2"))
        assertFalse(Crossfade.shouldFade(true, null, "2"))
        assertFalse(Crossfade.shouldFade(true, "1", "1"))
        assertTrue(Crossfade.shouldFade(true, "1", "2"))
    }

    @Test
    fun nearEndIgnoresShortAndEarlyPositions() {
        assertFalse(Crossfade.shouldArmNearEnd(false, false, 8_000, 7_500))
        assertFalse(Crossfade.shouldArmNearEnd(false, false, 240_000, 4_000))
        assertFalse(Crossfade.shouldArmNearEnd(true, false, 240_000, 238_000))
        assertFalse(Crossfade.shouldArmNearEnd(false, true, 240_000, 238_000))
        assertTrue(Crossfade.shouldArmNearEnd(false, false, 240_000, 238_000))
    }

    @Test
    fun incomingEndDuringFade() {
        assertTrue(Crossfade.incomingEndIsReal(0, 2_000))
        assertFalse(Crossfade.incomingEndIsReal(0, 180_000))
        assertTrue(Crossfade.incomingEndIsReal(800, 180_000))
    }

    @Test
    fun inOutQuadIsSymmetric() {
        assertEquals(0f, Crossfade.inOutQuad(0f), 1e-5f)
        assertEquals(1f, Crossfade.inOutQuad(1f), 1e-5f)
        assertEquals(0.5f, Crossfade.inOutQuad(0.5f), 1e-5f)
        assertTrue(Crossfade.inOutQuad(0.25f) < 0.25f)
        assertTrue(Crossfade.inOutQuad(0.75f) > 0.75f)
    }
}
