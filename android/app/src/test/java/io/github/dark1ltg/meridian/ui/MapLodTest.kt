package io.github.dark1ltg.meridian.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapLodTest {
    @Test
    fun labelsAfterZoomIn() {
        assertFalse(MapLod.shouldLabel(1.2f))
        assertTrue(MapLod.shouldLabel(1.8f))
    }

    @Test
    fun shelfStarsSkipBloomWhenZoomedOut() {
        assertFalse(MapLod.shouldBloom(1.0f, "shelf", selected = false))
        assertTrue(MapLod.shouldBloom(1.0f, "now", selected = false))
        assertTrue(MapLod.shouldBloom(1.0f, "shelf", selected = true))
    }

    @Test
    fun cullsOffscreen() {
        assertFalse(MapLod.onScreen(-400f, 10f, 200f, 200f, pad = 72f))
        assertTrue(MapLod.onScreen(10f, 10f, 200f, 200f))
    }

    @Test
    fun titan2GalaxyBudgetStaysUnderPanCost() {
        assertTrue(MapLod.galaxyBudget(compact = false, starCount = 200, minDimPx = MapLod.TITAN2_MIN_PX) <= 560)
        assertTrue(MapLod.galaxyBudget(compact = false, starCount = 1200, minDimPx = MapLod.TITAN2_MIN_PX) <= 360)
        assertTrue(MapLod.galaxyBudget(compact = true, starCount = 80, minDimPx = MapLod.TITAN2_MIN_PX) <= 220)
        assertTrue(MapLod.galaxyBudget(compact = false, starCount = 200) > MapLod.galaxyBudget(compact = false, starCount = 200, minDimPx = MapLod.TITAN2_MIN_PX))
    }

    @Test
    fun lensFlushesOnReleaseOrIdle() {
        assertFalse(MapLod.shouldFlushLens(pointerReleased = false, idleSinceMoveMs = 0L))
        assertFalse(MapLod.shouldFlushLens(pointerReleased = false, idleSinceMoveMs = 149L))
        assertTrue(MapLod.shouldFlushLens(pointerReleased = false, idleSinceMoveMs = 150L))
        assertTrue(MapLod.shouldFlushLens(pointerReleased = true, idleSinceMoveMs = 0L))
    }
}
