package io.github.dark1ltg.meridian.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dark1ltg.meridian.data.JobProgress
import io.github.dark1ltg.meridian.data.TrackRow
import io.github.dark1ltg.meridian.ui.theme.Card
import io.github.dark1ltg.meridian.ui.theme.Cream
import io.github.dark1ltg.meridian.ui.theme.Ink
import io.github.dark1ltg.meridian.ui.theme.Mute
import io.github.dark1ltg.meridian.ui.theme.Orange
import io.github.dark1ltg.meridian.ui.theme.UbuntuSans
import kotlinx.coroutines.delay

enum class AppTab { Map, Queue, Context, More }

sealed class Overlay {
    data object None : Overlay()
    data object Search : Overlay()
    data object NowPlaying : Overlay()
    data class Why(val id: Int) : Overlay()
    data class Settings(val page: SettingsPage) : Overlay()
}

@Composable
fun MainScreen(
    vm: SessionViewModel,
    ui: UiState,
    onLeaveApp: () -> Unit = {},
) {
    MainScreen(
        ui = ui,
        onToggleSearch = { vm.toggleSearch(!ui.searchOpen) },
        onMode = vm::setMode,
        onSearch = vm::search,
        onLens = vm::setLens,
        onPlay = vm::play,
        onPin = vm::pin,
        onLove = vm::love,
        onTogglePlay = vm::togglePlay,
        onSkip = vm::skip,
        onPrevious = vm::previous,
        onSeek = vm::seek,
        onScan = vm::scan,
        onAnalyze = vm::analyze,
        onRescan = vm::rescan,
        onPlayContext = vm::playContext,
        onAddFolder = vm::pickFolder,
        onRemoveFolder = vm::removeFolder,
        onCancelJob = vm::cancelJob,
        onVolume = vm::setVolume,
        onIndexAll = vm::indexAllDeviceAudio,
        onLeaveApp = onLeaveApp,
    )
}

@Composable
fun MainScreen(
    ui: UiState,
    onToggleSearch: () -> Unit = {},
    onMode: (String) -> Unit = {},
    onSearch: (String) -> Unit = {},
    onLens: (Float, Float, Float) -> Unit = { _, _, _ -> },
    onPlay: (Int) -> Unit = {},
    onPin: (Int, Float, Float) -> Unit = { _, _, _ -> },
    onLove: (Int) -> Unit = {},
    onTogglePlay: () -> Unit = {},
    onSkip: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onSeek: (Long) -> Unit = {},
    onScan: () -> Unit = {},
    onAnalyze: () -> Unit = {},
    onRescan: () -> Unit = {},
    onPlayContext: () -> Unit = {},
    onAddFolder: () -> Unit = {},
    onRemoveFolder: (String) -> Unit = {},
    onCancelJob: () -> Unit = {},
    onVolume: (Float) -> Unit = {},
    onIndexAll: () -> Unit = {},
    onLeaveApp: () -> Unit = {},
    initialTab: AppTab = AppTab.Map,
    initialOverlay: Overlay = Overlay.None,
) {
    var tab by remember { mutableStateOf(initialTab) }
    var overlay by remember { mutableStateOf(initialOverlay) }
    var toast by remember { mutableStateOf<String?>(null) }
    val searchOpen = overlay is Overlay.Search || ui.searchOpen
    BackHandler {
        when {
            overlay !is Overlay.None || searchOpen -> {
                if (searchOpen) onToggleSearch()
                overlay = Overlay.None
            }
            tab != AppTab.Map -> tab = AppTab.Map
            else -> onLeaveApp()
        }
    }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(1400)
            toast = null
        }
    }
    val flash: (String) -> Unit = { toast = it }
    val pinWithFlash: (Int, Float, Float) -> Unit = { id, v, e ->
        onPin(id, v, e)
        flash("Pinned")
    }
    val loveWithFlash: (Int) -> Unit = { id ->
        onLove(id)
        flash("Loved")
    }
    val snap = ui.snapshot
    val whyTrack = (overlay as? Overlay.Why)?.id?.let { id ->
        snap.queue.find { it.id == id }
            ?: snap.now.find { it.id == id }
            ?: snap.deep.find { it.id == id }
            ?: snap.fill.find { it.id == id }
            ?: snap.shelf.find { it.id == id }
            ?: snap.stars.find { it.id == id }
            ?: snap.current?.takeIf { it.id == id }
    }
    val showJob = ui.busy || ui.job.running

    Box(Modifier.fillMaxSize()) {
        if (tab != AppTab.Map) Sky()
        if (tab == AppTab.Map) {
            MapTab(
                ui = ui,
                onLens = onLens,
                onPlay = onPlay,
                onPin = pinWithFlash,
                onPlayContext = onPlayContext,
                onOpenPlayer = { overlay = Overlay.NowPlaying },
                onTogglePlay = onTogglePlay,
                onMenu = { tab = AppTab.More },
                onSearch = {
                    overlay = Overlay.Search
                    onToggleSearch()
                },
                onTab = { tab = it },
                onCancelJob = onCancelJob,
                onScan = onScan,
                onAnalyze = onAnalyze,
                onAddFolder = onAddFolder,
                onIndexAll = onIndexAll,
            )
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                if (showJob) {
                    JobStrip(ui = ui, onCancel = onCancelJob)
                }
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        AppTab.Map -> Unit
                        AppTab.Queue -> QueueScreen(
                            tracks = snap.queue,
                            currentId = snap.current?.id,
                            queueIndex = snap.queueIndex,
                            onPlay = onPlay,
                            onLove = loveWithFlash,
                            onOpenWhy = { overlay = Overlay.Why(it) },
                        )
                        AppTab.Context -> ContextScreen(
                            snapshot = snap,
                            onPlay = onPlay,
                            onPlayContext = onPlayContext,
                            onOpenWhy = { overlay = Overlay.Why(it) },
                        )
                        AppTab.More -> SettingsScreen(
                            folders = snap.folders,
                            mode = snap.mode,
                            onMode = onMode,
                            onScan = onScan,
                            onAnalyze = onAnalyze,
                            onRescan = onRescan,
                            onAddFolder = onAddFolder,
                            onRemoveFolder = onRemoveFolder,
                            busy = ui.busy,
                            onIndexAll = onIndexAll,
                            onOpen = { overlay = Overlay.Settings(it) },
                        )
                    }
                }
                BottomNav(tab) { tab = it }
            }
        }
        if (overlay is Overlay.Search || ui.searchOpen) {
            SearchOverlay(
                initialQuery = snap.searchQuery,
                rows = snap.search,
                currentId = snap.current?.id,
                onQuery = onSearch,
                onPlay = onPlay,
                onLove = loveWithFlash,
                onOpenWhy = { overlay = Overlay.Why(it) },
                onClose = {
                    overlay = Overlay.None
                    onToggleSearch()
                },
            )
        }
        if (overlay is Overlay.NowPlaying) {
            NowPlayingScreen(
                track = snap.current,
                playing = ui.playing,
                positionMs = ui.positionMs,
                durationMs = ui.durationMs.coerceAtLeast(snap.current?.durationMs ?: 0L),
                volume = ui.volume,
                onClose = { overlay = Overlay.None },
                onToggle = onTogglePlay,
                onSkip = onSkip,
                onPrevious = onPrevious,
                onSeek = onSeek,
                onLove = { snap.current?.id?.let(loveWithFlash) },
                onVolume = onVolume,
            )
        }
        if (overlay is Overlay.Settings) {
            SettingsDetailScreen(
                page = (overlay as Overlay.Settings).page,
                onBack = { overlay = Overlay.None },
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            )
        }
        if (whyTrack != null) {
            WhyTrackScreen(
                track = whyTrack,
                snapshot = snap,
                onPlay = {
                    onPlay(whyTrack.id)
                    overlay = Overlay.NowPlaying
                },
                onViewInMap = {
                    onLens(whyTrack.valence, whyTrack.energy, snap.lensRadius)
                    overlay = Overlay.None
                    tab = AppTab.Map
                },
            )
        }
        toast?.let { label ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = 96.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Text(
                    label,
                    color = Cream,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xEE121A32))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
internal fun JobStrip(ui: UiState, onCancel: () -> Unit) {
    val job = ui.job
    val label = jobLabel(job, ui.message)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        if (job.total > 0) {
            LinearProgressIndicator(
                progress = { job.fraction },
                modifier = Modifier.fillMaxWidth(),
                color = Orange,
                trackColor = Color(0x22FFFFFF),
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = Orange,
                trackColor = Color(0x22FFFFFF),
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    label,
                    color = Mute,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (job.total > 0 && job.current > 0) {
                    Text(
                        "${job.current} of ${job.total}",
                        color = Cream,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Text(
                "Cancel",
                color = Orange,
                fontSize = 12.sp,
                modifier = Modifier.clickable(onClick = onCancel),
            )
        }
    }
}

internal fun jobLabel(job: JobProgress, fallback: String): String {
    val raw = job.message.ifBlank { fallback }.trim()
    if (raw.isEmpty()) {
        return when (job.kind) {
            "listen" -> "Listening…"
            "rescan" -> "Rescanning…"
            else -> "Scanning…"
        }
    }
    // Engine may already embed "name — N of T"; keep the filename line clean.
    val cut = raw.indexOf(" — ")
    return if (cut > 0) raw.substring(0, cut) else raw
}

@Composable
private fun MapTopBar(onMenu: () -> Unit, onSearch: () -> Unit, mode: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenu) {
            Icon(Icons.Outlined.Menu, contentDescription = "Menu", tint = Ink)
        }
        Text(
            "MERIDIAN",
            color = Cream,
            fontSize = 18.sp,
            fontFamily = UbuntuSans,
            fontWeight = FontWeight.Medium,
            letterSpacing = 4.sp,
            modifier = Modifier.weight(1f),
        )
        if (mode.isNotBlank()) {
            Text(
                mode.replaceFirstChar { it.titlecase() },
                color = Mute,
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x66121A32))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        IconButton(onClick = onSearch) {
            Icon(Icons.Outlined.Search, contentDescription = "Search", tint = Ink)
        }
    }
}

@Composable
private fun MapTab(
    ui: UiState,
    onLens: (Float, Float, Float) -> Unit,
    onPlay: (Int) -> Unit,
    onPin: (Int, Float, Float) -> Unit,
    onPlayContext: () -> Unit,
    onOpenPlayer: () -> Unit,
    onTogglePlay: () -> Unit,
    onMenu: () -> Unit,
    onSearch: () -> Unit,
    onTab: (AppTab) -> Unit,
    onCancelJob: () -> Unit,
    onScan: () -> Unit,
    onAnalyze: () -> Unit,
    onAddFolder: () -> Unit,
    onIndexAll: () -> Unit,
) {
    val snap = ui.snapshot
    val lensAim = remember { LensAim(snap.lensX, snap.lensY, snap.lensRadius) }
    val nearby = snap.stars.count { inLens(it, lensAim.x, lensAim.y, lensAim.radius) }
        .coerceAtLeast(snap.queue.size)
    val (glow, kinetic) = moodWord(lensAim.x, lensAim.y)
    val skyHintState = remember { mutableStateOf("") }
    val skyHint = skyHintState.value
    val onHover = remember {
        { text: String -> skyHintState.value = text }
    }
    val hudAlpha by animateFloatAsState(
        targetValue = if (lensAim.dragging) 0.12f else 1f,
        animationSpec = tween(160),
        label = "mapHud",
    )
    val emptyLibrary = snap.trackCount == 0
    Box(Modifier.fillMaxSize()) {
        MoodMap(
            snapshot = snap,
            onLens = onLens,
            onPlay = onPlay,
            onPin = onPin,
            onHover = onHover,
            lensAim = lensAim,
        )
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            MapTopBar(onMenu = onMenu, onSearch = onSearch, mode = snap.mode)
            if (ui.busy || ui.job.running) {
                JobStrip(ui = ui, onCancel = onCancelJob)
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xE6080614)),
                        ),
                    )
                    .padding(top = 24.dp),
            ) {
                Column {
                    Column(Modifier.alpha(hudAlpha)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Shadow   ·   Glow", color = Mute, fontSize = 11.sp)
                            Text("Still   ·   Kinetic", color = Mute, fontSize = 11.sp)
                        }
                        Text(
                            when {
                                skyHint.isNotBlank() -> skyHint
                                emptyLibrary -> ui.message
                                else -> "$nearby nearby"
                            },
                            color = Ink,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        Text(
                            listOfNotNull(snap.bandLabel.ifBlank { null }, glow, kinetic)
                                .joinToString("  ·  "),
                            color = Mute,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
                        )
                    }
                    if (emptyLibrary) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            TextButton(onClick = onIndexAll) { Text("Index", color = Orange) }
                            TextButton(onClick = onAddFolder) { Text("Folder", color = Orange) }
                            TextButton(onClick = onScan) { Text("Scan", color = Orange) }
                            TextButton(onClick = onAnalyze) { Text("Listen", color = Orange) }
                        }
                    }
                    MiniPlayer(
                        track = snap.current,
                        playing = ui.playing,
                        onOpen = onOpenPlayer,
                        onToggle = onTogglePlay,
                    )
                    Button(
                        onClick = onPlayContext,
                        enabled = snap.queue.isNotEmpty() || snap.stars.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .height(44.dp),
                        shape = RoundedCornerShape(22.dp),
                    ) {
                        Text("Play Context", fontWeight = FontWeight.SemiBold)
                    }
                    BottomNav(AppTab.Map, onTab)
                }
            }
        }
    }
}

@Composable
private fun MiniPlayer(
    track: TrackRow?,
    playing: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Card)
            .clickable(onClick = onOpen)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackArt(
            track?.path.orEmpty(),
            track?.title ?: "Meridian",
            Modifier.size(44.dp),
            corner = 10.dp,
            useAlbumArt = false,
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                track?.title ?: "Nothing playing",
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
            )
            Text(
                track?.artist ?: "Aim the lens, then play",
                color = Mute,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onToggle) {
            Icon(
                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (playing) "Pause" else "Play",
                tint = Cream,
            )
        }
    }
}

@Composable
private fun BottomNav(tab: AppTab, onTab: (AppTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xCC0C1220))
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavItem("Map", Icons.Outlined.Map, tab == AppTab.Map) { onTab(AppTab.Map) }
        NavItem("Queue", Icons.Outlined.QueueMusic, tab == AppTab.Queue) { onTab(AppTab.Queue) }
        NavItem("Context", Icons.Outlined.AutoAwesome, tab == AppTab.Context) { onTab(AppTab.Context) }
        NavItem("More", Icons.Outlined.MoreHoriz, tab == AppTab.More) { onTab(AppTab.More) }
    }
}

@Composable
private fun NavItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = if (selected) Orange else Mute, modifier = Modifier.size(22.dp))
        Text(label, color = if (selected) Orange else Mute, fontSize = 11.sp)
    }
}

@Composable
private fun SearchOverlay(
    initialQuery: String,
    rows: List<TrackRow>,
    currentId: Int?,
    onQuery: (String) -> Unit,
    onPlay: (Int) -> Unit,
    onLove: (Int) -> Unit,
    onOpenWhy: (Int) -> Unit,
    onClose: () -> Unit,
) {
    var draft by remember { mutableStateOf(initialQuery) }
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xEE070B18))
            .statusBarsPadding()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { next ->
                    draft = next
                    onQuery(next)
                },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Title, artist, album") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Orange,
                    focusedTextColor = Ink,
                    unfocusedTextColor = Ink,
                    cursorColor = Orange,
                ),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Close",
                color = Orange,
                modifier = Modifier.clickable(onClick = onClose),
            )
        }
        LazyColumn(Modifier.padding(top = 12.dp)) {
            if (draft.isNotBlank() && rows.isEmpty()) {
                item { Text("No matches.", color = Mute, modifier = Modifier.padding(top = 8.dp)) }
            }
            items(rows, key = { it.id }) { row ->
                QueueRow(
                    index = 0,
                    row = row,
                    active = row.id == currentId,
                    onPlay = { onPlay(row.id) },
                    onLove = { onLove(row.id) },
                    onOpenWhy = { onOpenWhy(row.id) },
                    showIndex = false,
                )
            }
        }
    }
}
