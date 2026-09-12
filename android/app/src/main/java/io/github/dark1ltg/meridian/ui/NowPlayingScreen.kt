package io.github.dark1ltg.meridian.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import io.github.dark1ltg.meridian.ui.theme.UbuntuSans
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dark1ltg.meridian.data.TrackRow
import io.github.dark1ltg.meridian.ui.theme.Cream
import io.github.dark1ltg.meridian.ui.theme.Ink
import io.github.dark1ltg.meridian.ui.theme.Mute
import io.github.dark1ltg.meridian.ui.theme.Orange

@Composable
fun NowPlayingScreen(
    track: TrackRow?,
    playing: Boolean,
    positionMs: Long,
    durationMs: Long,
    volume: Float,
    onClose: () -> Unit,
    onToggle: () -> Unit,
    onSkip: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onLove: () -> Unit,
    onVolume: (Float) -> Unit,
) {
    val dur = durationMs.coerceAtLeast(1L)
    Box(Modifier.fillMaxSize()) {
        Sky(seed = 5)
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 12.dp),
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Close", tint = Ink)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onLove, enabled = track != null) {
                    Icon(
                        if (track?.loved == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Love",
                        tint = if (track?.loved == true) Color(0xFFFF4D9A) else Ink,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            TrackArt(
                path = track?.path.orEmpty(),
                title = track?.title ?: "Meridian",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(28.dp)),
                corner = 28.dp,
                useAlbumArt = true,
            )
            Spacer(Modifier.height(28.dp))
            Text(
                track?.title ?: "Nothing playing",
                color = Cream,
                fontSize = 28.sp,
                fontFamily = UbuntuSans,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(track?.artist?.ifBlank { null }, track?.album?.ifBlank { null })
                    .joinToString("\n")
                    .ifBlank { "Aim the lens, then play" },
                color = Mute,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(20.dp))
            Slider(
                value = (positionMs.toFloat() / dur).coerceIn(0f, 1f),
                onValueChange = { onSeek((it * dur).toLong()) },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Orange,
                    inactiveTrackColor = Color(0x33FFFFFF),
                ),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatMs(positionMs), color = Mute, fontSize = 12.sp)
                Text(formatMs(durationMs), color = Mute, fontSize = 12.sp)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Ink, modifier = Modifier.size(36.dp))
                }
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(onClick = onToggle),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color(0xFF12081C),
                        modifier = Modifier.size(36.dp),
                    )
                }
                IconButton(onClick = onSkip) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Ink, modifier = Modifier.size(36.dp))
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.VolumeUp, contentDescription = null, tint = Mute, modifier = Modifier.size(18.dp))
                Slider(
                    value = volume,
                    onValueChange = onVolume,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Orange,
                        inactiveTrackColor = Color(0x33FFFFFF),
                    ),
                )
            }
            Spacer(Modifier.height(8.dp))
            Waveform(playing)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Waveform(active: Boolean) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        val n = 42
        val gap = size.width / n
        for (i in 0 until n) {
            val h = (8f + ((i * 17) % 23) * 1.6f) * if (active) 1f else 0.55f
            val x = gap * i + gap / 2f
            val color = if (i < n / 2) Orange else Color(0xFF5CE1FF)
            drawLine(
                color.copy(alpha = 0.85f),
                Offset(x, size.height / 2f - h),
                Offset(x, size.height / 2f + h),
                strokeWidth = 3.2f,
                cap = StrokeCap.Round,
            )
        }
    }
}
