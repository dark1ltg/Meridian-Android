package io.github.dark1ltg.meridian.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dark1ltg.meridian.data.EngineSnapshot
import io.github.dark1ltg.meridian.data.TrackRow
import io.github.dark1ltg.meridian.ui.theme.Card
import io.github.dark1ltg.meridian.ui.theme.Cream
import io.github.dark1ltg.meridian.ui.theme.Deep
import io.github.dark1ltg.meridian.ui.theme.Fill
import io.github.dark1ltg.meridian.ui.theme.Ink
import io.github.dark1ltg.meridian.ui.theme.Mute
import io.github.dark1ltg.meridian.ui.theme.Now
import io.github.dark1ltg.meridian.ui.theme.Orange
import io.github.dark1ltg.meridian.ui.theme.Shelf
import io.github.dark1ltg.meridian.ui.theme.UbuntuCondensed

@Composable
fun ContextScreen(
    snapshot: EngineSnapshot,
    onPlay: (Int) -> Unit,
    onPlayContext: () -> Unit,
    onOpenWhy: (Int) -> Unit,
    onLens: (Float, Float, Float) -> Unit,
    onPin: (Int, Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (glow, kinetic) = moodWord(snapshot.lensX, snapshot.lensY)
    val empty = snapshot.now.isEmpty() && snapshot.deep.isEmpty() &&
        snapshot.fill.isEmpty() && snapshot.shelf.isEmpty()
    Box(modifier.fillMaxSize()) {
        Sky()
        Column(Modifier.fillMaxSize()) {
            Text(
                "Context",
                color = Cream,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(168.dp)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(22.dp)),
            ) {
                MoodMap(
                    snapshot = snapshot,
                    onLens = onLens,
                    onPlay = onPlay,
                    onPin = onPin,
                    showSky = true,
                    compact = true,
                )
            }
            Row(
                Modifier.padding(start = 20.dp, top = 10.dp, end = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MoodChip(glow)
                MoodChip(kinetic)
                if (snapshot.bandLabel.isNotBlank()) MoodChip(snapshot.bandLabel)
            }
            Text(
                "Listen matrix — the same NOW / DEEP / FILL / SHELF bands as the desktop app.",
                color = Mute,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (empty) {
                    Text(
                        "No tracks in this neighborhood yet. Scan a folder, then aim the lens.",
                        color = Mute,
                        modifier = Modifier.padding(12.dp),
                    )
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MatrixCell("NOW", "Closest to the lens", Now, snapshot.now, snapshot.current?.id, onPlay, onOpenWhy, Modifier.weight(1f))
                        MatrixCell("DEEP", "Worth sitting with", Deep, snapshot.deep, snapshot.current?.id, onPlay, onOpenWhy, Modifier.weight(1f))
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MatrixCell("FILL", "Keeps the room going", Fill, snapshot.fill, snapshot.current?.id, onPlay, onOpenWhy, Modifier.weight(1f))
                        MatrixCell("SHELF", "Parked until later", Shelf, snapshot.shelf, snapshot.current?.id, onPlay, onOpenWhy, Modifier.weight(1f))
                    }
                }
            }
            Button(
                onClick = onPlayContext,
                enabled = snapshot.queue.isNotEmpty() || snapshot.now.isNotEmpty() || snapshot.stars.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(28.dp),
            ) {
                Text("Play Context", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun MatrixCell(
    title: String,
    subtitle: String,
    accent: Color,
    tracks: List<TrackRow>,
    currentId: Int?,
    onPlay: (Int) -> Unit,
    onWhy: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .padding(10.dp),
    ) {
        Text(title, color = accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Text(subtitle, color = Mute, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (tracks.isEmpty()) {
            Text("—", color = Mute, modifier = Modifier.padding(top = 12.dp), fontSize = 12.sp)
        } else {
            tracks.take(5).forEach { row ->
                Text(
                    row.title,
                    color = if (row.id == currentId) Orange else Ink,
                    fontFamily = UbuntuCondensed,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onWhy(row.id)
                        }
                        .padding(top = 6.dp),
                )
                Text(
                    row.artist,
                    color = Mute,
                    fontFamily = UbuntuCondensed,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlay(row.id) },
                )
            }
        }
    }
}

@Composable
internal fun MoodChip(label: String) {
    Text(
        label,
        color = Ink,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Card)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
