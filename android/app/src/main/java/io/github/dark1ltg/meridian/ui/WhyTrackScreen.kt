package io.github.dark1ltg.meridian.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
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
    val cards = whyCards(track, snapshot.bandLabel, snapshot.mode)
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
                val icons = listOf(Icons.Outlined.Star, Icons.Outlined.Place, Icons.Outlined.Map)
                cards.forEachIndexed { index, card ->
                    WhyRow(icons[index], card.title)
                }
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
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Orange)
                        .clickable(onClick = onPlay),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WhyRow(icon: ImageVector, title: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Orange,
            modifier = Modifier.padding(end = 12.dp, top = 2.dp),
        )
        Text(title, color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
    }
}
