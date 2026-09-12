package io.github.dark1ltg.meridian.ui

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.dark1ltg.meridian.data.Engine
import io.github.dark1ltg.meridian.data.EngineSnapshot
import io.github.dark1ltg.meridian.data.FolderPaths
import io.github.dark1ltg.meridian.data.JobProgress
import io.github.dark1ltg.meridian.data.MediaStoreMusic
import io.github.dark1ltg.meridian.data.SafIngest
import io.github.dark1ltg.meridian.data.emptySnapshot
import io.github.dark1ltg.meridian.playback.LocalPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

class SessionViewModel(app: Application) : AndroidViewModel(app) {
    private val engine = Engine()
    val player = LocalPlayer(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state
    private var work: Job? = null
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
                        call("snapshot")
                    }
                },
            )
            _state.update { it.copy(playing = p.isPlaying, volume = p.volume) }
        }
        viewModelScope.launch {
            while (true) {
                delay(250)
                val p = player.player
                val pos = p?.currentPosition?.coerceAtLeast(0L) ?: 0L
                val dur = p?.duration?.let { if (it > 0) it else 0L } ?: 0L
                _state.update { it.copy(positionMs = pos, durationMs = dur) }
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
            val music = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath
            val snap = withContext(Dispatchers.IO) {
                engine.call(
                    "initialize",
                    JSONObject().put("data_dir", data).put("default_music", music),
                )
            }
            applySnap(snap)
            booted = snap.ok
        }
    }

    fun setLens(x: Float, y: Float, radius: Float) {
        call(
            "set_lens",
            JSONObject().put("x", x.toDouble()).put("y", y.toDouble()).put("radius", radius.toDouble()),
        )
    }

    fun setMode(mode: String) = call("set_mode", JSONObject().put("mode", mode))

    fun play(id: Int) {
        call(
            "play",
            JSONObject().put("id", id).put("position_ms", _state.value.positionMs),
        ) { snap ->
            snap.current?.let { player.play(it) }
        }
    }

    fun skip() {
        player.skip()
    }

    fun previous() {
        player.previous()
    }

    fun seek(ms: Long) {
        player.seek(ms)
        _state.update { it.copy(positionMs = ms) }
    }

    fun setVolume(value: Float) {
        val v = value.coerceIn(0f, 1f)
        player.setVolume(v)
        _state.update { it.copy(volume = v) }
    }

    fun playContext() {
        val snap = _state.value.snapshot
        val start = snap.queue.getOrNull(snap.queueIndex)
            ?: snap.queue.firstOrNull()
            ?: snap.now.firstOrNull()
            ?: snap.stars.firstOrNull()
            ?: return
        play(start.id)
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

    fun search(query: String) = call("search", JSONObject().put("query", query))

    fun scan() = heavy("scan") { JSONObject() }

    fun analyze() = heavy("analyze") { JSONObject() }

    fun rescan() = heavy("rescan") { JSONObject() }

    fun cancelJob() {
        viewModelScope.launch(Dispatchers.IO) { engine.abort() }
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
        work?.cancel()
        work = viewModelScope.launch {
            ensureBoot()
            _state.update { it.copy(busy = true, message = "Looking for music…") }
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
                    found += MediaStoreMusic.directories(app)
                }
                if (!treeUri.isNullOrBlank()) {
                    val ingested = SafIngest.ingest(app, android.net.Uri.parse(treeUri))
                    found += ingested.folders
                    ingested.uriTracks.forEach { track ->
                        uriTracks += JSONObject().put("path", track.uri).put("title", track.title)
                    }
                }
                found.distinct()
            }
            if (folders.isEmpty() && uriTracks.isEmpty()) {
                _state.update {
                    it.copy(
                        busy = false,
                        message = "No playable folders there. Try Music, an SD card, or Index all audio.",
                    )
                }
                return@launch
            }
            if (folders.isNotEmpty()) {
                val snap = withContext(Dispatchers.IO) {
                    engine.call("add_folders", JSONObject().put("paths", org.json.JSONArray(folders)))
                }
                applySnap(snap)
                if (!snap.ok) {
                    _state.update { it.copy(busy = false, job = JobProgress()) }
                    return@launch
                }
                _state.update { it.copy(busy = true, message = "Scanning…") }
                val scanned = withContext(Dispatchers.IO) { engine.call("scan", JSONObject()) }
                applySnap(scanned)
            }
            if (uriTracks.isNotEmpty()) {
                _state.update { it.copy(busy = true, message = "Indexing shared storage…") }
                val uris = withContext(Dispatchers.IO) {
                    engine.call(
                        "add_uri_tracks",
                        JSONObject().put("tracks", org.json.JSONArray(uriTracks)),
                    )
                }
                applySnap(uris)
            }
            _state.update { it.copy(busy = false, job = JobProgress()) }
        }
    }

    fun removeFolder(path: String) = call("remove_folder", JSONObject().put("path", path))

    fun pickFolder() {
        onPickFolder?.invoke()
    }

    fun toggleSearch(open: Boolean) {
        _state.update { it.copy(searchOpen = open) }
        if (!open) search("")
    }

    private fun heavy(method: String, payload: () -> JSONObject) {
        work?.cancel()
        work = viewModelScope.launch {
            ensureBoot()
            _state.update { it.copy(busy = true, message = "Working…") }
            val poll = launch {
                while (isActive) {
                    delay(220)
                    val job = withContext(Dispatchers.IO) { engine.progress() }
                    _state.update {
                        it.copy(
                            job = job,
                            message = job.message.ifBlank { it.message },
                        )
                    }
                }
            }
            val snap = withContext(Dispatchers.IO) {
                engine.call(method, payload())
            }
            poll.cancel()
            applySnap(snap)
            _state.update { it.copy(busy = false, job = JobProgress()) }
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
        val msg = when {
            !snap.ok -> snap.error.ifBlank { "Something went wrong." }
            snap.status.isNotBlank() -> snap.status
            snap.trackCount == 0 -> "Add Music, then Scan. Files stay on this phone."
            else -> "${snap.trackCount} tracks · ${snap.bandLabel} · ${snap.mode.replaceFirstChar { it.uppercase() }}"
        }
        _state.update { it.copy(snapshot = snap, message = msg, job = snap.job) }
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }
}
