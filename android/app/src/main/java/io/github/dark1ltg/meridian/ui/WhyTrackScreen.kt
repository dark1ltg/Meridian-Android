package io.github.dark1ltg.meridian.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import io.github.dark1ltg.meridian.ui.theme.UbuntuSans
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dark1ltg.meridian.data.EngineSnapshot
import io.github.dark1ltg.meridian.data.TrackRow
import io.github.dark1ltg.meridian.ui.theme.Card
import io.github.dark1ltg.meridian.ui.theme.Cream
import io.github.dark1ltg.meridian.ui.theme.Ink
import io.github.dark1ltg.meridian.ui.theme.Mute
import io.github.dark1ltg.meridian.ui.theme.Orange

@Composable
fun WhyTrackScreen(
    track: TrackRow,
    snapshot: EngineSnapshot,
    onPlay: () -> Unit,
    onViewInMap: () -> Unit,
) {
    val (glow, kinetic) = moodWord(track.valence, track.energy)
    val lines = track.why.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val headline = lines.getOrNull(1) ?: defaultHeadline(track)
    val detail = lines.drop(2).joinToString("\n").ifBlank { defaultDetail(track, snapshot) }
    Box(Modifier.fillMaxSize()) {
        Sky(seed = 21)
        Column(
            Modifier
                .fillMaxSize()
                .padding(20.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                track.title,
                color = Cream,
                fontSize = 32.sp,
                fontFamily = UbuntuSans,
                fontWeight = FontWeight.Medium,
            )
            Text(track.artist, color = Mute, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                trackTags(track).forEach { (label, _) -> MoodChip(label) }
            }
            Text(
                "Why this track?",
                color = Cream,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 28.dp, bottom = 12.dp),
            )
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                WhyRow(Icons.Outlined.Star, headline, "$glow · $kinetic · ${"%.0f".format(track.fit * 100)}% fit")
                WhyRow(
                    Icons.Outlined.Place,
                    "Importance ${"%.0f".format(track.importance * 100)}%",
                    buildString {
                        if (track.loved) append("Loved. ")
                        if (track.pinned) append("Pinned on the map. ")
                        append("Played ${track.playCount}× · skipped ${track.skipCount}×")
                    },
                )
                WhyRow(
                    Icons.Outlined.Map,
                    snapshot.bandLabel.ifBlank { "Clock band" } + " · ${snapshot.mode.replaceFirstChar { it.uppercase() }}",
                    detail,
                )
            }
            Button(
                onClick = onViewInMap,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A3348), contentColor = Ink),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
            ) {
                Text("View in map")
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Card)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TrackArt(track.path, track.title, Modifier.size(48.dp), corner = 10.dp, useAlbumArt = false)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(track.title, color = Ink, maxLines = 1)
                    Text(track.artist, color = Mute, fontSize = 12.sp, maxLines = 1)
                }
                Button(
                    onClick = onPlay,
                    colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = Color.White),
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp),
                ) {
                    Text("▶")
                }
            }
        }
    }
}

private fun defaultHeadline(track: TrackRow): String {
    return when (track.quadrant) {
        "now" -> "NOW — closest to the lens"
        "deep" -> "DEEP — worth sitting with"
        "fill" -> "FILL — keeps the room going"
        "shelf" -> "SHELF — parked until later"
        else -> "Fits the current mood"
    }
}

private fun defaultDetail(track: TrackRow, snapshot: EngineSnapshot): String {
    val parts = mutableListOf<String>()
    if (track.loved) parts += "Loved"
    if (track.pinned) parts += "Pinned"
    parts += "confidence ${"%.2f".format(track.confidence)}"
    if (track.confidenceNote.isNotBlank()) parts += track.confidenceNote
    if (snapshot.bandLabel.isNotBlank()) parts += snapshot.bandLabel
    return parts.joinToString(" · ")
}

@Composable
private fun WhyRow(icon: ImageVector, title: String, body: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Orange, modifier = Modifier.padding(end = 12.dp))
        Column {
            Text(title, color = Ink, fontWeight = FontWeight.SemiBold)
            Text(body, color = Mute, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
