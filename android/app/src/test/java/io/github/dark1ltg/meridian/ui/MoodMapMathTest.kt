package io.github.dark1ltg.meridian.ui

import androidx.compose.ui.geometry.Offset
import io.github.dark1ltg.meridian.data.TrackRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodMapMathTest {
    @Test
    fun moodAndScreenRoundTrip() {
        val pan = Offset.Zero
        val zoom = 1f
        val w = 1440f
        val h = 1440f
        val (v, e) = screenToMood(moodToPx(0.31f, 0.72f, w, h, pan, zoom), w, h, pan, zoom)
        assertEquals(0.31f, v, 0.002f)
        assertEquals(0.72f, e, 0.002f)
    }

    @Test
    fun zoomedRoundTrip() {
        val pan = Offset(40f, -18f)
        val zoom = 2.1f
        val w = 1440f
        val h = 1440f
        val origin = moodToPx(0.44f, 0.61f, w, h, pan, zoom)
        val (v, e) = screenToMood(origin, w, h, pan, zoom)
        assertEquals(0.44f, v, 0.003f)
        assertEquals(0.61f, e, 0.003f)
    }

    @Test
    fun digInZoomKeepsFocusMoodUnderFingers() {
        val w = 1440f
        val h = 1440f
        val pan = Offset(30f, -20f)
        val oldZoom = 2f
        val focus = moodToPx(0.55f, 0.42f, w, h, pan, oldZoom)
        val newZoom = 5f
        val nextPan = panAfterZoom(focus, w, h, pan, oldZoom, newZoom)
        val after = moodToPx(0.55f, 0.42f, w, h, nextPan, newZoom)
        assertEquals(focus.x, after.x, 1.5f)
        assertEquals(focus.y, after.y, 1.5f)
    }

    @Test
    fun nearestPicksClosestStar() {
        val a = row(1, 0.2f, 0.8f)
        val b = row(2, 0.8f, 0.2f)
        val hit = nearest(listOf(a, b), moodToPx(0.21f, 0.79f, 200f, 200f), 200, 200, 40f)
        assertSame(a, hit)
        assertNull(nearest(listOf(a, b), Offset(1000f, 1000f), 200, 200, 20f))
    }

    @Test
    fun skipStarHitTestWhileAimingLens() {
        assertFalse(shouldHitTestStars(aimingLens = true, pinning = false))
        assertFalse(shouldHitTestStars(aimingLens = false, pinning = true))
        assertTrue(shouldHitTestStars(aimingLens = false, pinning = false))
    }

    @Test
    fun lensGrabBlocksStarPinLatch() {
        assertFalse(shouldLatchStarPin(nearLens = true, hasStar = true))
        assertTrue(shouldLatchStarPin(nearLens = false, hasStar = true))
        assertFalse(shouldLatchStarPin(nearLens = false, hasStar = false))
    }

    @Test
    fun lensAimAllowsTighterNeighborhood() {
        val aim = LensAim(radius = 0.22f)
        aim.resizeBy(0.2f)
        assertEquals(LENS_RADIUS_MIN, aim.radius, 0.0001f)
        aim.resizeBy(0.01f)
        assertEquals(LENS_RADIUS_MIN, aim.radius, 0.0001f)
    }

    @Test
    fun inLensCountsNeighborhood() {
        val inside = row(1, 0.50f, 0.50f)
        val rim = row(2, 0.70f, 0.50f)
        val out = row(3, 0.90f, 0.90f)
        assertTrue(inLens(inside, 0.50f, 0.50f, 0.22f))
        assertTrue(inLens(rim, 0.50f, 0.50f, 0.22f))
        assertFalse(inLens(out, 0.50f, 0.50f, 0.22f))
    }

    private fun row(id: Int, valence: Float, energy: Float) = TrackRow(
        id = id,
        path = "",
        title = "t$id",
        artist = "a",
        album = "",
        durationMs = 1_000,
        valence = valence,
        energy = energy,
        confidence = 0.8f,
        lowTrust = false,
        pinned = false,
        loved = false,
        quadrant = "now",
    )
}
