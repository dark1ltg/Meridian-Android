package io.github.dark1ltg.meridian.ui

/**
 * Zoom bands for the Android sky. Desktop uses a wider zoom range; these
 * thresholds are scaled to the 1.0–3.2 pinch range used here.
 */
object MapLod {
    const val LABEL_START = 1.55f
    const val BLOOM_START = 1.28f
    const val CHROME_START = 1.0f
    const val CHROME_END = 2.4f
    const val MAX_LABELS = 48
    const val CULL_PAD = 72f

    fun chromeAlpha(zoom: Float): Float {
        if (zoom <= CHROME_START) return 0.55f
        if (zoom >= CHROME_END) return 1f
        val t = (zoom - CHROME_START) / (CHROME_END - CHROME_START)
        return 0.55f + 0.45f * t
    }

    fun glowScale(zoom: Float): Float = (0.55f + (zoom - 1f) * 0.42f).coerceIn(0.55f, 1.35f)

    fun galaxyAlpha(zoom: Float): Float = (1.15f - (zoom - 1f) * 0.28f).coerceIn(0.35f, 1f)

    fun shouldLabel(zoom: Float): Boolean = zoom >= LABEL_START

    fun shouldBloom(zoom: Float, quadrant: String, selected: Boolean): Boolean {
        if (selected) return true
        if (zoom >= BLOOM_START) return true
        return quadrant == "now" || quadrant == "deep"
    }

    fun onScreen(x: Float, y: Float, w: Float, h: Float, pad: Float = CULL_PAD): Boolean {
        return x >= -pad && y >= -pad && x <= w + pad && y <= h + pad
    }
}
