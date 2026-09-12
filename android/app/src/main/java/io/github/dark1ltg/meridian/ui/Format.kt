package io.github.dark1ltg.meridian.ui

import io.github.dark1ltg.meridian.data.TrackRow
import io.github.dark1ltg.meridian.ui.theme.Deep
import io.github.dark1ltg.meridian.ui.theme.Fill
import io.github.dark1ltg.meridian.ui.theme.Now
import io.github.dark1ltg.meridian.ui.theme.Shelf
import kotlin.math.hypot

fun formatMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val total = (ms / 1000L).toInt()
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}

fun moodWord(valence: Float, energy: Float): Pair<String, String> {
    val glow = if (valence >= 0.5f) "Glow" else "Shadow"
    val kinetic = if (energy >= 0.5f) "Kinetic" else "Still"
    return glow to kinetic
}

fun trackTags(row: TrackRow): List<Pair<String, androidx.compose.ui.graphics.Color>> {
    val (glow, kinetic) = moodWord(row.valence, row.energy)
    return listOf(
        glow to if (row.valence >= 0.5f) Now else Deep,
        kinetic to if (row.energy >= 0.5f) Fill else Shelf,
        when {
            row.confidence >= 0.75f -> "Clear"
            row.lowTrust -> "Soft"
            else -> "Listen"
        } to Deep,
    )
}

fun inLens(row: TrackRow, lx: Float, ly: Float, r: Float): Boolean {
    return hypot((row.valence - lx).toDouble(), (row.energy - ly).toDouble()) <= r
}
