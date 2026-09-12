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
}
