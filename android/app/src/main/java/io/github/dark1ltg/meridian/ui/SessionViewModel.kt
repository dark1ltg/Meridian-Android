package io.github.dark1ltg.meridian.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.dark1ltg.meridian.MeridianApp
import io.github.dark1ltg.meridian.data.Engine
import io.github.dark1ltg.meridian.data.EngineSnapshot
import io.github.dark1ltg.meridian.data.FolderPaths
import io.github.dark1ltg.meridian.data.JobProgress
import io.github.dark1ltg.meridian.data.MediaStoreMusic
import io.github.dark1ltg.meridian.data.PathProbe
import io.github.dark1ltg.meridian.data.SafIngest
import io.github.dark1ltg.meridian.data.TrackRow
import io.github.dark1ltg.meridian.data.TreeGrants
import io.github.dark1ltg.meridian.data.emptySnapshot
import io.github.dark1ltg.meridian.playback.LocalPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

data class UiState(
    val snapshot: EngineSnapshot = emptySnapshot(),
    val playing: Boolean = false,
    val busy: Boolean = false,
    val message: String = "Your library is a night sky.",
    val searchOpen: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 1f,
    val job: JobProgress = JobProgress(),
)

/** Playback clock — separate from [UiState] so the map shell does not recompose 4×/s. */
data class PlaybackClock(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

class SessionViewModel(app: Application) : AndroidViewModel(app) {
    private val engine = Engine()
    val player = LocalPlayer(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state
    private val _clock = MutableStateFlow(PlaybackClock())
    val clock: StateFlow<PlaybackClock> = _clock
    private var work: Job? = null
    private var lensFlush: Job? = null
    private var searchJob: Job? = null
    private var playJob: Job? = null
    private var playGeneration = 0
    /** Serializes engine.play so a cancelled older tap cannot finish after a newer one. */
    private val playGate = Any()
    private var pendingLens: Triple<Float, Float, Float>? = null
    private val bootMutex = Mutex()
    private var booted = false
    var onPickFolder: (() -> Unit)? = null

    init {
        player.whenReady { p ->
            p.addListener(
                object : androidx.media3.common.Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _state.update { it.copy(playing = isPlaying) }
                    }

                    override fun onMediaItemTransition(
                        mediaItem: androidx.media3.common.MediaItem?,
                        reason: Int,
                    ) {
                        // PlaybackService already advanced the Python queue; only sync the
                        // UI shell from the cached row (no full classify per transition).
                        syncNowPlayingFromMedia(mediaItem)
                    }
                },
            )
            _state.update { it.copy(playing = p.isPlaying, volume = p.volume) }
            syncNowPlayingFromMedia(p.currentMediaItem)
        }
        viewModelScope.launch {
            var lastPos = -1L
            var lastDur = -1L
            while (true) {
                delay(500)
                val p = player.player
                val pos = p?.currentPosition?.coerceAtLeast(0L) ?: 0L
                val dur = p?.duration?.let { if (it > 0) it else 0L } ?: 0L
                if (abs(pos - lastPos) < 400 && dur == lastDur) continue
                lastPos = pos
                lastDur = dur
                _clock.value = PlaybackClock(pos, dur)
            }
        }
    }

    fun boot() {
        viewModelScope.launch { ensureBoot() }
    }

    private suspend fun ensureBoot() {
        bootMutex.withLock {
            if (booted) return
            val data = File(getApplication<Application>().filesDir, "meridian").absolutePath
            // ensure() reuses a live session (PlaybackService may already hold it)
            // and never auto-registers an empty Music folder before Allow.
            val snap = withContext(Dispatchers.IO) {
                (getApplication<Application>() as? MeridianApp)?.ensurePython()
                engine.call("ensure", JSONObject().put("data_dir", data))
            }
            applySnap(snap)
            booted = snap.ok
        }
    }

    /** Stop any in-flight Scan/Listen so the next action can take the engine lock. */
    private fun stopWork(message: String? = null) {
        work?.cancel()
        work = null
        viewModelScope.launch(Dispatchers.IO) { runCatching { engine.abort() } }
        _state.update {
            it.copy(
                busy = false,
                job = JobProgress(),
                message = message ?: it.message,
            )
        }
    }

    fun setLens(x: Float, y: Float, radius: Float) {
        val nx = x.coerceIn(0f, 1f)
        val ny = y.coerceIn(0f, 1f)
        val nr = radius
        pendingLens = Triple(nx, ny, nr)
        _state.update { it.copy(snapshot = it.snapshot.copy(lensX = nx, lensY = ny, lensRadius = nr)) }
        if (lensFlush?.isActive == true) return
        lensFlush = viewModelScope.launch {
            ensureBoot()
            while (isActive) {
                val next = pendingLens ?: break
                pendingLens = null
                val snap = withContext(Dispatchers.IO) {
                    engine.call(
                        "set_lens",
                        JSONObject()
                            .put("x", next.first.toDouble())
                            .put("y", next.second.toDouble())
                            .put("radius", next.third.toDouble()),
                    )
                }
                applySnap(snap)
            }
        }
    }

    fun setMode(mode: String) = call("set_mode", JSONObject().put("mode", mode))

    fun play(id: Int) {
        val local = findTrack(id)
        // Single-flight: a newer tap cancels an in-flight older play.
        val gen = ++playGeneration
        playJob?.cancel()
        playJob = viewModelScope.launch {
            ensureBoot()
            if (gen != playGeneration) return@launch
            // Capture position before any deck swap so skip/hate credit is accurate.
            val positionMs = _clock.value.positionMs
            // Arm engine.play before setMediaItem so hardCut → fade_settled sees pending credit.
            // NonCancellable + playGate: cancelled older IO cannot commit after a newer tap.
            val snap = withContext(Dispatchers.IO + NonCancellable) {
                synchronized(playGate) {
                    if (gen != playGeneration) return@withContext null
                    engine.call(
                        "play",
                        JSONObject().put("id", id).put("position_ms", positionMs),
                    )
                }
            } ?: return@launch
            if (gen != playGeneration) return@launch
            applySnap(snap)
            val track = snap.current ?: return@launch
            val toPlay = when {
                local != null && track.id == local.id && track.path == local.path -> local
                else -> track
            }
            val reachable = withContext(Dispatchers.IO) {
                PathProbe(getApplication()).reachable(toPlay.path)
            }
            if (gen != playGeneration) return@launch
            if (!reachable) {
                _state.update { it.copy(message = "Can’t open that file — trying the next track…") }
                val next = withContext(Dispatchers.IO + NonCancellable) {
                    synchronized(playGate) {
                        if (gen != playGeneration) return@withContext null
                        engine.call("playback_failed", JSONObject().put("id", toPlay.id))
                    }
                } ?: return@launch
                if (gen != playGeneration) return@launch
                applySnap(next)
                next.current?.let { cand ->
                    val ok = withContext(Dispatchers.IO) {
                        PathProbe(getApplication()).reachable(cand.path)
                    }
                    if (gen == playGeneration && ok) player.play(cand)
                }
                return@launch
            }
            player.play(toPlay)
        }
    }

    private fun findTrack(id: Int): TrackRow? {
        val snap = _state.value.snapshot
        return snap.current?.takeIf { it.id == id }
            ?: snap.queue.find { it.id == id }
            ?: snap.now.find { it.id == id }
            ?: snap.deep.find { it.id == id }
            ?: snap.fill.find { it.id == id }
            ?: snap.shelf.find { it.id == id }
            ?: snap.stars.find { it.id == id }
            ?: snap.search.find { it.id == id }
    }

    /** Keep Now Playing title/art/queue highlight in sync when DualDeck advances. */
    private fun syncNowPlayingFromMedia(mediaItem: androidx.media3.common.MediaItem?) {
        val id = mediaItem?.mediaId?.toIntOrNull() ?: return
        val snap = _state.value.snapshot
        if (snap.current?.id == id) return
        val row = findTrack(id)
        if (row != null) {
            _state.update { it.copy(snapshot = it.snapshot.withPlayingTrack(row)) }
            val p = player.player
            _clock.value = PlaybackClock(
                positionMs = p?.currentPosition?.coerceAtLeast(0L) ?: 0L,
                durationMs = p?.duration?.takeIf { it > 0 } ?: row.durationMs,
            )
        } else {
            // Rare: track not in the last UI shell — pull engine state once.
            call("snapshot")
        }
    }

    fun skip() {
        // Drive engine + setMediaItem here. MediaController.seekToNext() is often a
        // no-op with our single-item DualDeck timeline even when QueuePlayer overrides it.
        viewModelScope.launch {
            ensureBoot()
            val positionMs = _clock.value.positionMs
            val snap = withContext(Dispatchers.IO) {
                engine.call("skip", JSONObject().put("position_ms", positionMs))
            }
            applySnap(snap)
            playSnapCurrent(snap)
        }
    }

    fun previous() {
        viewModelScope.launch {
            ensureBoot()
            val positionMs = _clock.value.positionMs
            // Same >4s rule as lockscreen / PlaybackService — restart before true previous.
            if (positionMs > 4_000L) {
                player.seek(0L)
                _clock.value = _clock.value.copy(positionMs = 0L)
                return@launch
            }
            val snap = withContext(Dispatchers.IO) {
                engine.call("previous")
            }
            applySnap(snap)
            playSnapCurrent(snap)
        }
    }

    private suspend fun playSnapCurrent(snap: EngineSnapshot) {
        val track = snap.current ?: return
        val reachable = withContext(Dispatchers.IO) {
            PathProbe(getApplication()).reachable(track.path)
        }
        if (!reachable) {
            _state.update { it.copy(message = "Can’t open that file — trying the next track…") }
            val next = withContext(Dispatchers.IO) {
                engine.call("playback_failed", JSONObject().put("id", track.id))
            }
            applySnap(next)
            next.current?.let { cand ->
                val ok = withContext(Dispatchers.IO) {
                    PathProbe(getApplication()).reachable(cand.path)
                }
                if (ok) player.play(cand)
            }
            return
        }
        player.play(track)
        _clock.value = PlaybackClock(positionMs = 0L, durationMs = track.durationMs)
    }

    fun seek(ms: Long) {
        player.seek(ms)
        _clock.value = _clock.value.copy(positionMs = ms)
    }

    fun setVolume(value: Float) {
        val v = value.coerceIn(0f, 1f)
        player.setVolume(v)
        _state.update { it.copy(volume = v) }
    }

    fun playContext() {
        viewModelScope.launch {
            ensureBoot()
            flushPendingLens()
            val synced = withContext(Dispatchers.IO) { engine.call("play_context") }
            applySnap(synced)
            val start = synced.queue.getOrNull(synced.queueIndex)
                ?: synced.queue.firstOrNull()
                ?: synced.now.firstOrNull()
                ?: synced.stars.firstOrNull()
                ?: return@launch
            play(start.id)
        }
    }

    /** Fresh context queue from the current listen matrix (desktop Renew Queue). */
    fun renewQueue(after: (EngineSnapshot) -> Unit = {}) {
        viewModelScope.launch {
            ensureBoot()
            flushPendingLens()
            val snap = withContext(Dispatchers.IO) { engine.call("renew_queue") }
            applySnap(snap)
            after(snap)
        }
    }

    /**
     * Apply any debounced lens move before queue work so Renew/Play Context match the map.
     * Cancels an in-flight flush and pushes the latest coords so a mid-call null does not
     * drop a newer aim.
     */
    private suspend fun flushPendingLens() {
        lensFlush?.cancel()
        lensFlush = null
        // Loop until stable: a setLens during the IO call may refill pendingLens.
        while (true) {
            val next = pendingLens ?: return
            pendingLens = null
            val snap = withContext(Dispatchers.IO) {
                engine.call(
                    "set_lens",
                    JSONObject()
                        .put("x", next.first.toDouble())
                        .put("y", next.second.toDouble())
                        .put("radius", next.third.toDouble()),
                )
            }
            applySnap(snap)
            if (pendingLens == null) return
        }
    }

    fun togglePlay() {
        val current = _state.value.snapshot.current
        if (current == null) {
            val first = _state.value.snapshot.queue.firstOrNull() ?: return
            play(first.id)
        } else {
            player.toggle()
        }
    }

    fun pin(id: Int, valence: Float, energy: Float) {
        call(
            "pin",
            JSONObject().put("id", id).put("valence", valence.toDouble()).put("energy", energy.toDouble()),
        )
    }

    fun love(id: Int) = call("love", JSONObject().put("id", id))

    /**
     * Debounced library search. The TextField must keep its own draft string —
     * binding it to [EngineSnapshot.searchQuery] makes every keystroke wait on
     * Chaquopy and late snapshots yank the cursor backward.
     */
    fun search(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(140)
            ensureBoot()
            val snap = withContext(Dispatchers.IO) {
                engine.call("search", JSONObject().put("query", query))
            }
            applySnap(snap)
        }
    }

    /** Scan then Listen — matches desktop: import tags, then waveform mood. */
    fun scan() = scanThenListen()

    fun analyze() = heavy("analyze") { JSONObject() }

    fun rescan() = heavy("rescan") { JSONObject() }

    private fun scanThenListen() {
        stopWork("Scanning…")
        work = viewModelScope.launch {
            ensureBoot()
            _state.update { it.copy(busy = true, message = "Scanning…") }
            try {
                val scanned = runEngineJob("scan", JSONObject())
                applySnap(scanned)
                if (!scanned.ok) return@launch
                _state.update { it.copy(busy = true, message = "Listening…") }
                val listened = runEngineJob("analyze", JSONObject())
                applySnap(listened)
            } finally {
                _state.update { it.copy(busy = false, job = JobProgress()) }
            }
        }
    }

    fun cancelJob() {
        stopWork("Stopped.")
    }

    fun addFolder(path: String) {
        ingestLibrary(listOf(path), treeUri = null)
    }

    fun ingestTree(uri: String) {
        ingestLibrary(emptyList(), treeUri = uri)
    }

    fun indexAllDeviceAudio() {
        ingestLibrary(emptyList(), treeUri = null, includeMediaStore = true)
    }

    fun importSources(paths: List<String>, includeMediaStore: Boolean) {
        ingestLibrary(paths, treeUri = null, includeMediaStore = includeMediaStore)
    }

    private fun ingestLibrary(
        extraPaths: List<String>,
        treeUri: String?,
        includeMediaStore: Boolean = false,
    ) {
        stopWork("Looking for music…")
        work = viewModelScope.launch {
            ensureBoot()
            _state.update { it.copy(busy = true, message = "Looking for music…") }
            try {
                val app = getApplication<Application>()
                val uriTracks = mutableListOf<org.json.JSONObject>()
                val folders = withContext(Dispatchers.IO) {
                    val found = mutableListOf<String>()
                    extraPaths.forEach { raw ->
                        val resolved = FolderPaths.fromTreeUri(raw) ?: raw
                        val dir = File(resolved)
                        if (dir.isDirectory) found += dir.absolutePath
                    }
                    if (includeMediaStore) {
                        // Prefer MediaStore rows directly — scoped storage often
                        // cannot os.walk Music even after READ_MEDIA_AUDIO.
                        MediaStoreMusic.tracks(app).forEach { track ->
                            if (track.playPath.startsWith("content:")) {
                                uriTracks += JSONObject()
                                    .put("path", track.playPath)
                                    .put("title", track.title)
                                    .put("duration_ms", track.durationMs)
                            } else {
                                File(track.playPath).parentFile?.absolutePath?.let { found += it }
                            }
                        }
                        if (uriTracks.isNotEmpty()) {
                            found += "content://media/external/audio/media"
                        }
                    }
                    if (!treeUri.isNullOrBlank()) {
                        val ingested = SafIngest.ingest(app, android.net.Uri.parse(treeUri))
                        found += ingested.folders
                        ingested.uriTracks.forEach { track ->
                            uriTracks += JSONObject().put("path", track.uri).put("title", track.title)
                        }
                        // Persist grant ↔ folder key so Remove can drop access.
                        val keys = if (ingested.folders.isNotEmpty()) {
                            ingested.folders
                        } else {
                            listOf(TreeGrants.folderKeyForTree(treeUri))
                        }
                        keys.forEach { key -> TreeGrants.remember(app, key, treeUri) }
                    }
                    MediaStoreMusic.dropNested(found.distinct())
                }
                if (folders.isEmpty() && uriTracks.isEmpty()) {
                    _state.update {
                        it.copy(
                            message = "No playable music found. Allow access, Add folder, or Index all audio.",
                        )
                    }
                    return@launch
                }
                if (folders.isNotEmpty()) {
                    val snap = withContext(Dispatchers.IO) {
                        engine.call("add_folders", JSONObject().put("paths", org.json.JSONArray(folders)))
                    }
                    applySnap(snap)
                    if (!snap.ok) return@launch
                    // Filesystem folders: scan. content:// folder keys are markers only —
                    // their audio arrives via add_uri_tracks.
                    val walkable = folders.filter { !it.startsWith("content:") }
                    if (walkable.isNotEmpty()) {
                        _state.update { it.copy(message = "Scanning…") }
                        val scanned = runEngineJob("scan", JSONObject())
                        applySnap(scanned)
                        if (!scanned.ok) return@launch
                    }
                }
                if (uriTracks.isNotEmpty()) {
                    _state.update { it.copy(message = "Indexing shared storage…") }
                    val uris = withContext(Dispatchers.IO) {
                        engine.call(
                            "add_uri_tracks",
                            JSONObject().put("tracks", org.json.JSONArray(uriTracks)),
                        )
                    }
                    applySnap(uris)
                    if (!uris.ok) return@launch
                }
                // Desktop starts waveform Listen immediately after a successful
                // scan/import — without this, permission grant only tags files.
                _state.update { it.copy(message = "Listening…") }
                val listened = runEngineJob("analyze", JSONObject())
                applySnap(listened)
            } finally {
                _state.update { it.copy(busy = false, job = JobProgress()) }
            }
        }
    }

    fun removeFolder(path: String) {
        stopWork("Removing folder…")
        work = viewModelScope.launch {
            ensureBoot()
            _state.update { it.copy(busy = true, message = "Removing folder…") }
            try {
                val app = getApplication<Application>()
                withContext(Dispatchers.IO) { TreeGrants.release(app, path) }
                val snap = withContext(Dispatchers.IO) {
                    engine.call("remove_folder", JSONObject().put("path", path))
                }
                applySnap(snap)
            } finally {
                _state.update { it.copy(busy = false, job = JobProgress()) }
            }
        }
    }

    fun pickFolder() {
        // Folder picker must work even if a prior scan left busy stuck or mid-job.
        stopWork("Choose a folder…")
        val launch = onPickFolder
        if (launch == null) {
            _state.update { it.copy(message = "Folder picker isn’t ready yet — try again.") }
            return
        }
        launch.invoke()
    }

    fun clearFolderPickMessage() {
        _state.update {
            it.copy(
                message = when {
                    it.snapshot.trackCount == 0 -> "Add Music, then Scan. Files stay on this phone."
                    else -> it.message
                },
            )
        }
    }

    fun toggleSearch(open: Boolean) {
        _state.update { it.copy(searchOpen = open) }
        if (!open) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch {
                ensureBoot()
                val snap = withContext(Dispatchers.IO) {
                    engine.call("search", JSONObject().put("query", ""))
                }
                applySnap(snap)
            }
        }
    }

    private fun heavy(method: String, payload: () -> JSONObject) {
        stopWork("Working…")
        work = viewModelScope.launch {
            ensureBoot()
            _state.update { it.copy(busy = true, message = "Working…") }
            try {
                val snap = runEngineJob(method, payload())
                applySnap(snap)
            } finally {
                _state.update { it.copy(busy = false, job = JobProgress()) }
            }
        }
    }

    /** Run an engine job while polling live filename / N-of-total progress. */
    private suspend fun runEngineJob(method: String, payload: JSONObject): EngineSnapshot {
        val poll = viewModelScope.launch {
            while (isActive) {
                delay(180)
                val job = withContext(Dispatchers.IO) { engine.progress() }
                _state.update {
                    it.copy(
                        job = job,
                        message = job.message.ifBlank { it.message },
                    )
                }
            }
        }
        return try {
            withContext(Dispatchers.IO) { engine.call(method, payload) }
        } finally {
            poll.cancel()
        }
    }

    private fun call(
        method: String,
        payload: JSONObject = JSONObject(),
        after: (EngineSnapshot) -> Unit = {},
    ) {
        viewModelScope.launch {
            ensureBoot()
            val snap = withContext(Dispatchers.IO) { engine.call(method, payload) }
            applySnap(snap)
            after(snap)
        }
    }

    private fun applySnap(snap: EngineSnapshot) {
        val newer = pendingLens
        val merged = if (newer != null) {
            snap.copy(lensX = newer.first, lensY = newer.second, lensRadius = newer.third)
        } else {
            snap
        }
        val msg = when {
            !merged.ok -> merged.error.ifBlank { "Something went wrong." }
            merged.status.isNotBlank() -> merged.status
            merged.trackCount == 0 -> "Add Music, then Scan. Files stay on this phone."
            else -> "${merged.trackCount} tracks · ${merged.bandLabel} · ${merged.mode.replaceFirstChar { it.uppercase() }}"
        }
        _state.update { it.copy(snapshot = merged, message = msg, job = merged.job) }
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }
}
