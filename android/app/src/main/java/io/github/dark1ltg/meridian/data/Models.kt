package io.github.dark1ltg.meridian.data

import org.json.JSONArray
import org.json.JSONObject

data class TrackRow(
    val id: Int,
    val path: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val valence: Float,
    val energy: Float,
    val confidence: Float,
    val lowTrust: Boolean,
    val pinned: Boolean,
    val loved: Boolean,
    val quadrant: String,
    val fit: Float = 0f,
    val importance: Float = 0f,
    val playCount: Int = 0,
    val skipCount: Int = 0,
    val confidenceNote: String = "",
    val why: String = "",
)

data class JobProgress(
    val kind: String = "",
    val message: String = "",
    val current: Int = 0,
    val total: Int = 0,
    val running: Boolean = false,
) {
    val fraction: Float
        get() = if (total > 0) (current.toFloat() / total).coerceIn(0f, 1f) else 0f
}

data class EngineSnapshot(
    val ok: Boolean,
    val error: String,
    val status: String,
    val trackCount: Int,
    val folders: List<String>,
    val mode: String,
    val bandLabel: String,
    val skipPressure: Float,
    val lensX: Float,
    val lensY: Float,
    val lensRadius: Float,
    val queue: List<TrackRow>,
    val queueIndex: Int,
    val now: List<TrackRow>,
    val deep: List<TrackRow>,
    val fill: List<TrackRow>,
    val shelf: List<TrackRow>,
    val stars: List<TrackRow>,
    val current: TrackRow?,
    val search: List<TrackRow>,
    val searchQuery: String,
    val job: JobProgress = JobProgress(),
)

fun emptySnapshot() = EngineSnapshot(
    ok = true,
    error = "",
    status = "",
    trackCount = 0,
    folders = emptyList(),
    mode = "wander",
    bandLabel = "Night",
    skipPressure = 0f,
    lensX = 0.5f,
    lensY = 0.5f,
    lensRadius = 0.22f,
    queue = emptyList(),
    queueIndex = 0,
    now = emptyList(),
    deep = emptyList(),
    fill = emptyList(),
    shelf = emptyList(),
    stars = emptyList(),
    current = null,
    search = emptyList(),
    searchQuery = "",
    job = JobProgress(),
)

fun JSONObject.trackList(key: String): List<TrackRow> {
    val arr = optJSONArray(key) ?: JSONArray()
    return (0 until arr.length()).map { arr.getJSONObject(it).toTrack() }
}

fun JSONObject.toTrack(): TrackRow = TrackRow(
    id = optInt("id"),
    path = optString("path"),
    title = optString("title"),
    artist = optString("artist"),
    album = optString("album"),
    durationMs = optLong("duration_ms"),
    valence = optDouble("valence").toFloat(),
    energy = optDouble("energy").toFloat(),
    confidence = optDouble("confidence").toFloat(),
    lowTrust = optBoolean("low_trust"),
    pinned = optBoolean("pinned"),
    loved = optBoolean("loved"),
    quadrant = optString("quadrant"),
    fit = optDouble("fit").toFloat(),
    importance = optDouble("importance").toFloat(),
    playCount = optInt("play_count"),
    skipCount = optInt("skip_count"),
    confidenceNote = optString("confidence_note"),
    why = optString("why"),
)

fun JSONObject.toJob(): JobProgress {
    val job = optJSONObject("job") ?: this
    return JobProgress(
        kind = job.optString("kind"),
        message = job.optString("message"),
        current = job.optInt("current"),
        total = job.optInt("total"),
        running = job.optBoolean("running"),
    )
}

fun JSONObject.toSnapshot(): EngineSnapshot {
    val lens = optJSONObject("lens") ?: JSONObject()
    val currentObj = optJSONObject("current")
    val foldersArr = optJSONArray("folders") ?: JSONArray()
    val folders = (0 until foldersArr.length()).map { foldersArr.getString(it) }
    return EngineSnapshot(
        ok = optBoolean("ok", true),
        error = optString("error"),
        status = optString("status"),
        trackCount = optInt("track_count"),
        folders = folders,
        mode = optString("mode", "wander"),
        bandLabel = optString("band_label", ""),
        skipPressure = optDouble("skip_pressure").toFloat(),
        lensX = lens.optDouble("x", 0.5).toFloat(),
        lensY = lens.optDouble("y", 0.5).toFloat(),
        lensRadius = lens.optDouble("radius", 0.22).toFloat(),
        queue = trackList("queue"),
        queueIndex = optInt("queue_index"),
        now = trackList("now"),
        deep = trackList("deep"),
        fill = trackList("fill"),
        shelf = trackList("shelf"),
        stars = trackList("stars"),
        current = currentObj?.toTrack(),
        search = trackList("search"),
        searchQuery = optString("search_query"),
        job = toJob(),
    )
}
