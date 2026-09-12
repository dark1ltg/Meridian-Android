package io.github.dark1ltg.meridian.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.dark1ltg.meridian.playback.CoverArt
import kotlin.math.abs

@Composable
fun TrackArt(
    path: String,
    title: String,
    modifier: Modifier = Modifier,
    corner: Dp = 16.dp,
    useAlbumArt: Boolean = false,
) {
    val bitmap = remember(path, useAlbumArt) {
        if (useAlbumArt) CoverArt.bitmap(path) else null
    }
    Box(modifier.clip(RoundedCornerShape(corner))) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            val hash = abs(title.hashCode())
            val a = Color(0xFF2A1058)
            val b = Color(
                red = 0.35f + (hash % 50) / 120f,
                green = 0.12f + (hash / 7 % 40) / 140f,
                blue = 0.45f + (hash / 13 % 50) / 120f,
            )
            Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(b, a, Color(0xFF070B18)),
                        center = Offset(size.width * 0.42f, size.height * 0.38f),
                        radius = size.maxDimension * 0.7f,
                    ),
                )
                drawCircle(Color.White.copy(alpha = 0.18f), 3f, Offset(size.width * 0.3f, size.height * 0.28f))
                drawCircle(Color(0xFFFFC14D).copy(alpha = 0.5f), 5f, Offset(size.width * 0.7f, size.height * 0.35f))
            }
        }
    }
}
