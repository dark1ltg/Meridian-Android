package io.github.dark1ltg.meridian.ui

import org.junit.Assert.assertEquals
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
    fun digInZoomCeiling() {
        assertEquals(MapLod.ZOOM_MAX, MapLod.clampZoom(9f), 0.0001f)
        assertEquals(MapLod.ZOOM_MIN, MapLod.clampZoom(0.2f), 0.0001f)
        assertEquals(4.2f, MapLod.clampZoom(4.2f), 0.0001f)
    }

    @Test
    fun deepZoomLimitsBloomAndAllowsMoreLabels() {
        assertFalse(MapLod.shouldBloom(4.5f, "shelf", selected = false))
        assertFalse(MapLod.shouldBloom(4.5f, "now", selected = false))
        assertTrue(MapLod.shouldBloom(4.5f, "shelf", selected = true))
        assertEquals(MapLod.MAX_LABELS_DEEP, MapLod.maxLabels(4f))
        assertEquals(MapLod.MAX_LABELS, MapLod.maxLabels(2f))
    }

    @Test
    fun starSizeCapsAtDeepZoom() {
        assertTrue(MapLod.starSizeScale(MapLod.ZOOM_MAX) <= 1.35f + 0.001f)
        assertTrue(MapLod.galaxyAlpha(MapLod.ZOOM_MAX) >= 0.22f)
        assertTrue(MapLod.glowScale(MapLod.ZOOM_MAX) < MapLod.glowScale(2.5f))
    }

    @Test
    fun shelfStarsSkipBloomWhenZoomedOut() {
        assertFalse(MapLod.shouldBloom(1.0f, "shelf", selected = false))
        assertTrue(MapLod.shouldBloom(1.0f, "now", selected = false))
        assertTrue(MapLod.shouldBloom(1.0f, "shelf", selected = true))
    }

    @Test
    fun midZoomDoesNotBloomEveryStar() {
        assertFalse(MapLod.shouldBloom(2.0f, "shelf", selected = false))
        assertFalse(MapLod.shouldBloom(2.0f, "fill", selected = false))
        assertTrue(MapLod.shouldBloom(2.0f, "now", selected = false))
        assertTrue(MapLod.shouldBloom(2.0f, "fill", selected = true))
    }

    @Test
    fun labelCollisionBoxesMatchTextScale() {
        assertEquals(14f, MapLod.LABEL_CHAR_W, 0.001f)
        assertEquals(28f, MapLod.LABEL_LINE_H, 0.001f)
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
