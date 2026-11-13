package io.github.dark1ltg.meridian.ui

import io.github.dark1ltg.meridian.data.JobProgress
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

/** Clock band as a spoken time-of-day word (Dawn → morning, Dusk → evening). */
fun clockBandPhrase(bandLabel: String): String = when (bandLabel.trim().lowercase()) {
    "dawn" -> "morning"
    "day" -> "daytime"
    "dusk" -> "evening"
    "night" -> "night"
    else -> bandLabel.trim().lowercase().ifBlank { "this hour" }
}

fun queueSourceLine(queueStale: Boolean, bandLabel: String): String {
    return if (queueStale) {
        "Lens moved · Renew or Play Context to sync"
    } else {
        "From where you aimed · ${clockBandPhrase(bandLabel)}"
    }
}

fun emptyMatrixCopy(quadrant: String): String = when (quadrant.trim().uppercase()) {
    "NOW" -> "Nothing closest yet"
    "DEEP" -> "Nothing to sit with yet"
    "FILL" -> "Nothing filling yet"
    "SHELF" -> "Nothing parked yet"
    else -> "Nothing here yet"
}

fun jobChipLabel(job: JobProgress, fallback: String = ""): String {
    val blob = "${job.kind} ${job.message} $fallback".lowercase()
    val verb = when {
        "listen" in blob || "analyze" in blob -> "Listening"
        "rescan" in blob -> "Rescanning"
        else -> "Scanning"
    }
    return if (job.total > 0) "$verb ${job.current} / ${job.total}" else verb
}

data class WhyCard(val title: String)

fun moodSoundPhrase(valence: Float, energy: Float): String {
    val (glow, kinetic) = moodWord(valence, energy)
    return when {
        glow == "Glow" && kinetic == "Kinetic" -> "Sounds bright and moving"
        glow == "Glow" -> "Sounds bright and calm"
        kinetic == "Kinetic" -> "Sounds dark and moving"
        else -> "Sounds calm and a bit dark"
    }
}

private val TAG_IN_NOTE = Regex("""(?:tag|path):([a-z0-9][a-z0-9 &+-]*)""", RegexOption.IGNORE_CASE)

internal fun tagHintPhrase(track: TrackRow): String? {
    val blob = "${track.confidenceNote} ${track.why}"
    val key = TAG_IN_NOTE.find(blob)?.groupValues?.get(1)?.lowercase()?.trim() ?: return null
    val label = when (key) {
        "game", "video game", "soundtrack", "ost", "vgm", "score" -> "Game-soundtrack"
        "r&b" -> "R&B"
        "hip hop" -> "Hip-hop"
        "lofi", "lo-fi" -> "Lo-fi"
        else -> key.replaceFirstChar { it.titlecase() }
    }
    val weak = track.lowTrust || track.confidence < 0.45f
    return if (weak) "$label tag is only a weak hint" else null
}

fun whyFitCard(track: TrackRow): String {
    val fit = (track.fit * 100f).toInt().coerceIn(0, 100)
    return when (track.quadrant.lowercase()) {
        "now" -> "Closest to where you aimed ($fit% fit)"
        "deep" -> "Worth sitting with — just outside the lens"
        "fill" -> "Keeps the room going ($fit% fit)"
        "shelf" -> "Parked for later — outside where you aimed"
        else -> "Fits this neighborhood ($fit% fit)"
    }
}

fun whyHistoryCard(track: TrackRow): String {
    val head = when {
        track.loved && track.pinned -> "You loved this and pinned it on the map"
        track.loved && track.playCount == 0 -> "You loved this — Meridian is trying it fresh"
        track.loved -> "You loved this, so it ranks higher here"
        track.pinned -> "Pinned on the map, so it stays in this neighborhood"
        track.playCount == 0 -> "Has not been played yet — Meridian is trying it fresh"
        track.playCount >= 4 -> "Familiar — played ${track.playCount} times"
        else -> "Played ${track.playCount} times"
    }
    val skipped = track.skipCount > track.playCount && track.skipCount >= 3
    return if (skipped) "$head. Skipped often, so it ranks lower" else head
}

fun whyContextCard(track: TrackRow, bandLabel: String, mode: String): String {
    val band = clockBandPhrase(bandLabel)
    val modeWord = mode.trim().replaceFirstChar { it.titlecase() }.ifBlank { "Wander" }
    val tag = tagHintPhrase(track)
    val parts = mutableListOf(
        "Picked for $band $modeWord",
        moodSoundPhrase(track.valence, track.energy),
    )
    if (tag != null) {
        parts += tag
    } else if (!track.pinned && (track.lowTrust || track.confidence < 0.45f)) {
        parts += "The listen is only a soft hint"
    }
    return parts.joinToString(". ") + "."
}

fun whyCards(track: TrackRow, bandLabel: String, mode: String): List<WhyCard> = listOf(
    WhyCard(whyFitCard(track)),
    WhyCard(whyHistoryCard(track)),
    WhyCard(whyContextCard(track, bandLabel, mode)),
)
