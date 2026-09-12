package io.github.dark1ltg.meridian.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dark1ltg.meridian.R
import io.github.dark1ltg.meridian.data.Track
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

@Composable
fun ContextScreen(
    track: Track,
    onBack: () -> Unit,
    onOpenMoodMap: () -> Unit,
    onOpenWhy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scroll = rememberScrollState()
    val formatter =
        remember {
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                .withZone(ZoneId.systemDefault())
        }
    val lastPlayed =
        remember(track.lastPlayedAt) {
            track.lastPlayedAt?.let { formatter.format(Instant.ofEpochMilli(it)) }
                ?: context.getString(R.string.context_never_played)
        }
    val skipRate =
        remember(track.playCount, track.skipCount) {
            val total = track.playCount + track.skipCount
            if (total <= 0) context.getString(R.string.context_skip_empty) else "${((track.skipCount.toFloat() / total) * 100f).roundToInt()}%"
        }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MeridianTheme.colors.bg)
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        ScreenHeader(title = stringResource(R.string.context_title), onBack = onBack)
        Text(
            text = track.title,
            color = MeridianTheme.colors.text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp).semantics { heading() },
        )
        Text(
            text = track.artist,
            color = MeridianTheme.colors.muted,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = stringResource(R.string.context_album_line, track.album),
            color = MeridianTheme.colors.muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.height(18.dp))
        ContextRow(stringResource(R.string.context_energy), "${track.energy.roundToInt()}")
        ContextRow(stringResource(R.string.context_valence), "${track.valence.roundToInt()}")
        ContextRow(stringResource(R.string.context_tempo), stringResource(R.string.bpm_value, track.tempo.roundToInt()))
        ContextRow(stringResource(R.string.context_key), track.key)
        ContextRow(stringResource(R.string.context_plays), track.playCount.toString())
        ContextRow(stringResource(R.string.context_skips), track.skipCount.toString())
        ContextRow(stringResource(R.string.context_skip_rate), skipRate)
        ContextRow(stringResource(R.string.context_last_played), lastPlayed)
        ContextRow(
            stringResource(R.string.context_file),
            track.uri.lastPathSegment ?: track.uri.toString(),
        )
        if (track.lyrics.isNotBlank()) {
            Text(
                text = stringResource(R.string.context_lyrics),
                color = MeridianTheme.colors.text,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 18.dp),
            )
            Text(
                text = track.lyrics,
                color = MeridianTheme.colors.muted,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(22.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AccentButton(
                text = stringResource(R.string.context_open_file),
                onClick = {
                    val view =
                        Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(track.uri, "audio/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    context.startActivity(Intent.createChooser(view, context.getString(R.string.context_open_file)))
                },
                modifier = Modifier.weight(1f),
            )
            AccentButton(
                text = stringResource(R.string.context_share),
                onClick = {
                    val share =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "${track.artist} — ${track.title}\n${track.uri}")
                        }
                    context.startActivity(Intent.createChooser(share, context.getString(R.string.context_share)))
                },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AccentButton(
                text = stringResource(R.string.context_open_mood_map),
                onClick = onOpenMoodMap,
                modifier = Modifier.weight(1f),
            )
            AccentButton(
                text = stringResource(R.string.context_open_why),
                onClick = onOpenWhy,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ContextRow(
    label: String,
    value: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = MeridianTheme.colors.muted, fontSize = 13.sp)
        Text(text = value, color = MeridianTheme.colors.text, fontSize = 13.sp)
    }
}
