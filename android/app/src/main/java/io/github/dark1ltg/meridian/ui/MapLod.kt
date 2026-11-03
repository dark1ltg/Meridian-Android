package io.github.dark1ltg.meridian.ui

/**
 * Zoom bands for the Android sky. Desktop uses a wider zoom range; these
 * thresholds are scaled to the 1.0–[ZOOM_MAX] pinch dig-in range used here.
 */
object MapLod {
    const val ZOOM_MIN = 1f
    /** Dig into dense clusters without leaving the map shell. */
    const val ZOOM_MAX = 5f
    const val LABEL_START = 1.55f
    /** Past this, bloom only selected — fill/shelf stay plain for dense dig-in. */
    const val DEEP_ZOOM = 3.2f
    const val CHROME_START = 1.0f
    const val CHROME_END = 2.4f
    const val MAX_LABELS = 48
    const val MAX_LABELS_DEEP = 64
    const val CULL_PAD = 72f
    const val LENS_COMMIT_IDLE_MS = 150L
    const val TITAN2_MIN_PX = 1440f

    fun clampZoom(zoom: Float): Float = zoom.coerceIn(ZOOM_MIN, ZOOM_MAX)

    fun galaxyBudget(compact: Boolean, starCount: Int, minDimPx: Float = 0f): Int {
        val highRes = minDimPx >= 1200f
        return when {
            compact -> if (highRes) 160 else 220
            starCount > 700 -> if (highRes) 260 else 320
            else -> if (highRes) 400 else 520
        }
    }

    fun shouldFlushLens(pointerReleased: Boolean, idleSinceMoveMs: Long, settleMs: Long = LENS_COMMIT_IDLE_MS): Boolean {
        return pointerReleased || idleSinceMoveMs >= settleMs
    }

    fun chromeAlpha(zoom: Float): Float {
        if (zoom <= CHROME_START) return 0.55f
        if (zoom >= CHROME_END) return 1f
        val t = (zoom - CHROME_START) / (CHROME_END - CHROME_START)
        return 0.55f + 0.45f * t
    }

    /** Peak mid-zoom, then ease off so dense dig-in stays readable (less glow haze). */
    fun glowScale(zoom: Float): Float {
        val rising = (0.55f + (zoom - 1f) * 0.42f).coerceIn(0.55f, 1.2f)
        if (zoom < DEEP_ZOOM) return rising
        val t = ((zoom - DEEP_ZOOM) / (ZOOM_MAX - DEEP_ZOOM)).coerceIn(0f, 1f)
        return rising + (0.72f - rising) * t
    }

    /** Soften wash as you dig in so star cores stay readable at 60 fps. */
    fun galaxyAlpha(zoom: Float): Float = (1.15f - (zoom - 1f) * 0.22f).coerceIn(0.22f, 1f)

    /** Cap point size growth so 5× dig-in does not explode overdraw. */
    fun starSizeScale(zoom: Float): Float = (0.85f + zoom * 0.12f).coerceAtMost(1.35f)

    fun shouldLabel(zoom: Float): Boolean = zoom >= LABEL_START

    fun maxLabels(zoom: Float): Int = if (zoom >= DEEP_ZOOM) MAX_LABELS_DEEP else MAX_LABELS

    /**
     * Bloom is expensive and washes dense clusters. Prefer NOW/DEEP (and selected);
     * never bloom everything mid-zoom. Deep dig-in: selected only.
     */
    fun shouldBloom(zoom: Float, quadrant: String, selected: Boolean): Boolean {
        if (selected) return true
        if (zoom >= DEEP_ZOOM) return false
        return quadrant == "now" || quadrant == "deep"
    }

    /** Approximate label box for collision (Compose canvas text, ~26sp Ubuntu Sans). */
    const val LABEL_CHAR_W = 14f
    const val LABEL_LINE_H = 28f
    const val LABEL_PAD = 4f

    /** Dig-in hit/grab radius must not grow past this fraction of the short edge. */
    const val LENS_HIT_CAP = 0.48f

    fun onScreen(x: Float, y: Float, w: Float, h: Float, pad: Float = CULL_PAD): Boolean {
        return x >= -pad && y >= -pad && x <= w + pad && y <= h + pad
    }
}
