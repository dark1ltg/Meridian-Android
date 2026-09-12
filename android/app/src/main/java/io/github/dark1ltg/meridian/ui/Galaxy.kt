package io.github.dark1ltg.meridian.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

internal data class FieldStar(
    val x: Float,
    val y: Float,
    val r: Float,
    val glow: Float,
    val alpha: Float,
    val color: Color,
    val spike: Boolean,
)

internal fun galaxyField(seed: Int = 42, count: Int = 1600): List<FieldStar> {
    val rng = Random(seed)
    val palette = listOf(
        Color(0xFFFFF6E8),
        Color(0xFFFFD27A),
        Color(0xFFFF8A3D),
        Color(0xFFFF4D8D),
        Color(0xFFE24BFF),
        Color(0xFF7AD7FF),
        Color(0xFF5C9CFF),
        Color.White,
    )
    return List(count) { i ->
        val kind = rng.nextFloat()
        val (x, y, dense) = when {
            kind < 0.38f -> {
                val arm = rng.nextInt(4)
                val t = rng.nextFloat() * 3.4f
                val rad = 0.06f + t * 0.34f + (rng.nextFloat() - 0.5f) * 0.045f
                val ang = t * 2.55f + arm * 1.62f + (rng.nextFloat() - 0.5f) * 0.22f
                Triple(
                    (0.50f + rad * cos(ang)).coerceIn(0.02f, 0.98f),
                    (0.46f + rad * sin(ang) * 0.92f).coerceIn(0.02f, 0.98f),
                    1.15f,
                )
            }
            kind < 0.72f -> {
                val u = rng.nextFloat()
                val v = rng.nextFloat()
                val r = kotlin.math.sqrt(-2.0 * kotlin.math.ln((u + 1e-6).toDouble())).toFloat() * 0.16f
                val th = (v * 6.28318f)
                Triple(
                    (0.50f + r * cos(th)).coerceIn(0.02f, 0.98f),
                    (0.46f + r * sin(th) * 0.88f).coerceIn(0.02f, 0.98f),
                    1.35f,
                )
            }
            else -> Triple(rng.nextFloat(), rng.nextFloat(), 0.55f)
        }
        val bright = rng.nextFloat()
        FieldStar(
            x = x,
            y = y,
            r = (0.45f + rng.nextFloat() * 1.85f) * dense,
            glow = if (bright > 0.88f) 7f + rng.nextFloat() * 9f else if (bright > 0.70f) 3.2f + rng.nextFloat() * 3.5f else 0f,
            alpha = (0.35f + rng.nextFloat() * 0.65f) * dense.coerceIn(0.65f, 1.25f).coerceAtMost(1f),
            color = palette[rng.nextInt(palette.size)],
            spike = bright > 0.96f && i % 9 == 0,
        )
    }
}

internal fun DrawScope.drawGalaxy(stars: List<FieldStar>, alpha: Float = 1f) {
    val a = alpha.coerceIn(0f, 1f)
    val w = size.width
    val h = size.height
    nebulaBlob(Color(0xFF8B2BB0), Offset(w * 0.42f, h * 0.40f), size.minDimension * 0.58f, 0.62f * a)
    nebulaBlob(Color(0xFF1A5CB8), Offset(w * 0.62f, h * 0.32f), size.minDimension * 0.50f, 0.48f * a)
    nebulaBlob(Color(0xFFD4452E), Offset(w * 0.34f, h * 0.58f), size.minDimension * 0.42f, 0.38f * a)
    nebulaBlob(Color(0xFF2EC4FF), Offset(w * 0.72f, h * 0.62f), size.minDimension * 0.38f, 0.28f * a)
    nebulaBlob(Color(0xFFFFC14D), Offset(w * 0.50f, h * 0.46f), size.minDimension * 0.24f, 0.34f * a)
    stars.forEach { s ->
        val p = Offset(s.x * w, s.y * h)
        if (s.glow > 0f) {
            drawCircle(s.color.copy(alpha = s.alpha * 0.16f * a), s.glow, p)
            drawCircle(s.color.copy(alpha = s.alpha * 0.32f * a), s.glow * 0.45f, p)
        }
        drawCircle(s.color.copy(alpha = s.alpha * a), s.r, p)
        if (s.spike) {
            val len = s.r * 7f
            drawLine(s.color.copy(alpha = 0.45f * a), Offset(p.x - len, p.y), Offset(p.x + len, p.y), 1.1f)
            drawLine(s.color.copy(alpha = 0.45f * a), Offset(p.x, p.y - len), Offset(p.x, p.y + len), 1.1f)
        }
    }
}

private fun DrawScope.nebulaBlob(color: Color, center: Offset, radius: Float, alpha: Float) {
    drawCircle(
        brush = androidx.compose.ui.graphics.Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.35f), Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

internal fun DrawScope.drawBloomStar(center: Offset, color: Color, core: Float, selected: Boolean) {
    val bloom = if (selected) core * 3.6f else core * 2.2f
    drawCircle(color.copy(alpha = if (selected) 0.22f else 0.12f), bloom, center)
    drawCircle(color.copy(alpha = 0.55f), core * 1.15f, center)
    drawCircle(Color.White.copy(alpha = 0.95f), (core * 0.42f).coerceAtLeast(1.2f), center)
    drawCircle(color, core * 0.72f, center)
    if (selected) {
        drawCircle(Color.White.copy(alpha = 0.7f), core * 1.8f, center, style = Stroke(1.3f))
    }
}
