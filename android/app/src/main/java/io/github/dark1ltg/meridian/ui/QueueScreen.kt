package io.github.dark1ltg.meridian.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import io.github.dark1ltg.meridian.ui.theme.UbuntuCondensed
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dark1ltg.meridian.data.TrackRow
import io.github.dark1ltg.meridian.ui.theme.Cream
import io.github.dark1ltg.meridian.ui.theme.Fill
import io.github.dark1ltg.meridian.ui.theme.Ink
import io.github.dark1ltg.meridian.ui.theme.Mute
import io.github.dark1ltg.meridian.ui.theme.Orange

@Composable
fun QueueScreen(
    tracks: List<TrackRow>,
    currentId: Int?,
    queueIndex: Int,
    onPlay: (Int) -> Unit,
    onLove: (Int) -> Unit,
    onOpenWhy: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            "Queue",
            color = Cream,
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
        )
        if (tracks.isEmpty()) {
            Text(
                "Move the lens on the map — the context queue fills from that neighborhood.",
                color = Mute,
                modifier = Modifier.padding(top = 24.dp),
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(tracks, key = { _, row -> row.id }) { index, row ->
                    QueueRow(
                        index = index + 1,
                        row = row,
                        active = row.id == currentId || index == queueIndex,
                        onPlay = { onPlay(row.id) },
                        onLove = { onLove(row.id) },
                        onOpenWhy = { onOpenWhy(row.id) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun QueueRow(
    index: Int,
    row: TrackRow,
    active: Boolean,
    onPlay: () -> Unit,
    onLove: () -> Unit,
    onOpenWhy: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) Color(0x22FF7A1A) else Color.Transparent)
            .clickable(onClick = onPlay)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$index",
            color = if (active) Orange else Mute,
            fontSize = 14.sp,
            modifier = Modifier.padding(end = 10.dp),
        )
        TrackArt(row.path, row.title, Modifier.size(48.dp), corner = 10.dp, useAlbumArt = false)
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
                .clickable(onClick = onOpenWhy),
        ) {
            Text(
                row.title,
                color = if (active) Orange else Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = UbuntuCondensed,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                row.artist,
                color = Mute,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = UbuntuCondensed,
            )
        }
        Text(formatMs(row.durationMs), color = Mute, fontSize = 12.sp)
        IconButton(onClick = onLove) {
            Icon(
                if (row.loved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Love",
                tint = if (row.loved) Fill else Mute,
            )
        }
    }
}
