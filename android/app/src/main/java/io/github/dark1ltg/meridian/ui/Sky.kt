package io.github.dark1ltg.meridian.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import io.github.dark1ltg.meridian.ui.theme.NebulaDeep
import io.github.dark1ltg.meridian.ui.theme.Night
import kotlin.random.Random

@Composable
fun Sky(modifier: Modifier = Modifier, seed: Int = 7) {
    val stars = remember(seed) {
        val rng = Random(seed)
        List(140) {
            StarSpeck(
                x = rng.nextFloat(),
                y = rng.nextFloat(),
                r = 0.4f + rng.nextFloat() * 1.8f,
                a = 0.15f + rng.nextFloat() * 0.85f,
                tint = when (rng.nextInt(6)) {
                    0 -> Color(0xFFFFC14D)
                    1 -> Color(0xFFFF6B9A)
                    2 -> Color(0xFF7AD7FF)
                    else -> Color.White
                },
            )
        }
    }
    Canvas(modifier.fillMaxSize()) {
        drawRect(
            Brush.verticalGradient(
                listOf(Color(0xFF1A0A38), Night, Color(0xFF05070F)),
            ),
        )
        drawCircle(
            Brush.radialGradient(
                colors = listOf(Color(0x88C43B8C), Color(0x33401A88), Color.Transparent),
                center = Offset(size.width * 0.28f, size.height * 0.38f),
                radius = size.minDimension * 0.72f,
            ),
            radius = size.minDimension * 0.72f,
            center = Offset(size.width * 0.28f, size.height * 0.38f),
        )
        drawCircle(
            Brush.radialGradient(
                colors = listOf(Color(0x6640C4FF), Color(0x22301A80), Color.Transparent),
                center = Offset(size.width * 0.78f, size.height * 0.22f),
                radius = size.minDimension * 0.55f,
            ),
            radius = size.minDimension * 0.55f,
            center = Offset(size.width * 0.78f, size.height * 0.22f),
        )
        drawCircle(
            Brush.radialGradient(
                colors = listOf(Color(0x55FF7A1A), Color.Transparent),
                center = Offset(size.width * 0.55f, size.height * 0.62f),
                radius = size.minDimension * 0.4f,
            ),
            radius = size.minDimension * 0.4f,
            center = Offset(size.width * 0.55f, size.height * 0.62f),
        )
        drawRect(NebulaDeep.copy(alpha = 0.12f))
        stars.forEach { s ->
            drawCircle(
                s.tint.copy(alpha = s.a),
                s.r,
                Offset(s.x * size.width, s.y * size.height),
            )
        }
    }
}

private data class StarSpeck(val x: Float, val y: Float, val r: Float, val a: Float, val tint: Color)
