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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
    onRecoverAudioAccess: () -> Unit = {},
) {
    val clock by vm.clock.collectAsState()
    MainScreen(
        ui = ui,
        clock = clock,
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
        onRenewQueue = { done -> vm.renewQueue { done(it.ok) } },
        onAddFolder = vm::pickFolder,
        onRemoveFolder = vm::removeFolder,
        onCancelJob = vm::cancelJob,
        onVolume = vm::setVolume,
        onIndexAll = vm::indexAllDeviceAudio,
        onLeaveApp = onLeaveApp,
        onRecoverAudioAccess = onRecoverAudioAccess,
    )
}

@Composable
fun MainScreen(
    ui: UiState,
    clock: PlaybackClock = PlaybackClock(),
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
    onRenewQueue: (done: (Boolean) -> Unit) -> Unit = { it(true) },
    onAddFolder: () -> Unit = {},
    onRemoveFolder: (String) -> Unit = {},
    onCancelJob: () -> Unit = {},
    onVolume: (Float) -> Unit = {},
    onIndexAll: () -> Unit = {},
    onLeaveApp: () -> Unit = {},
    onRecoverAudioAccess: () -> Unit = {},
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
                onSkip = onSkip,
                onPrevious = onPrevious,
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
                onRecoverAudioAccess = onRecoverAudioAccess,
            )
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                if (showJob) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        JobChip(ui = ui, onCancel = onCancelJob)
                    }
                }
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        AppTab.Map -> Unit
                        AppTab.Queue -> QueueScreen(
                            tracks = snap.queue,
                            currentId = snap.current?.id,
                            queueIndex = snap.queueIndex,
                            queueStale = snap.queueStale,
                            bandLabel = snap.bandLabel,
                            renewEnabled = snap.trackCount > 0 && !ui.busy && !ui.job.running,
                            onPlay = onPlay,
                            onLove = loveWithFlash,
                            onOpenWhy = { overlay = Overlay.Why(it) },
                            onRenewQueue = {
                                onRenewQueue { ok -> if (ok) flash("Queue renewed") }
                            },
                        )
                        AppTab.Context -> ContextScreen(
                            snapshot = snap,
                            renewEnabled = snap.trackCount > 0 && !ui.busy && !ui.job.running,
                            playContextEnabled = !ui.busy && !ui.job.running,
                            onPlay = onPlay,
                            onPlayContext = onPlayContext,
                            onOpenWhy = { overlay = Overlay.Why(it) },
                            onRenewQueue = {
                                onRenewQueue { ok -> if (ok) flash("Queue renewed") }
                            },
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
                            onRecoverAudioAccess = onRecoverAudioAccess,
                            onOpen = { overlay = Overlay.Settings(it) },
                        )
                    }
                }
                if (tab == AppTab.Queue) {
                    MiniPlayer(
                        track = snap.current,
                        playing = ui.playing,
                        onOpen = { overlay = Overlay.NowPlaying },
                        onToggle = onTogglePlay,
                        onSkip = onSkip,
                        onPrevious = onPrevious,
                    )
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
                positionMs = clock.positionMs,
                durationMs = clock.durationMs.coerceAtLeast(snap.current?.durationMs ?: 0L),
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
internal fun JobChip(ui: UiState, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val job = ui.job
    val label = jobChipLabel(job, ui.message)
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xCC121A32))
            .padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (job.total > 0) {
            CircularProgressIndicator(
                progress = { job.fraction },
                modifier = Modifier.size(14.dp),
                color = Orange,
                trackColor = Color(0x33FFFFFF),
                strokeWidth = 2.dp,
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = Orange,
                trackColor = Color(0x33FFFFFF),
                strokeWidth = 2.dp,
            )
        }
        Text(
            label,
            color = Cream,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Cancel",
                tint = Mute,
                modifier = Modifier.size(14.dp),
            )
        }
    }
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
        Row(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onSearch)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Search", color = Mute, fontSize = 11.sp)
            Spacer(Modifier.width(2.dp))
            Icon(
                Icons.Outlined.Search,
                contentDescription = "Search",
                tint = Ink,
                modifier = Modifier.size(22.dp),
            )
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
    onSkip: () -> Unit,
    onPrevious: () -> Unit,
    onMenu: () -> Unit,
    onSearch: () -> Unit,
    onTab: (AppTab) -> Unit,
    onCancelJob: () -> Unit,
    onScan: () -> Unit,
    onAnalyze: () -> Unit,
    onAddFolder: () -> Unit,
    onIndexAll: () -> Unit,
    onRecoverAudioAccess: () -> Unit,
) {
    val snap = ui.snapshot
    val lensAim = remember { LensAim(snap.lensX, snap.lensY, snap.lensRadius) }
    // Avoid O(stars) on the UI thread at pointer rate while dragging.
    var nearby by remember { mutableIntStateOf(snap.queue.size) }
    LaunchedEffect(lensAim.x, lensAim.y, lensAim.radius, snap.stars, lensAim.dragging) {
        if (lensAim.dragging) {
            delay(80)
        }
        nearby = snap.stars.count { inLens(it, lensAim.x, lensAim.y, lensAim.radius) }
            .coerceAtLeast(snap.queue.size)
    }
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
                            TextButton(onClick = onRecoverAudioAccess) { Text("Allow", color = Orange) }
                            TextButton(onClick = onIndexAll) { Text("Index", color = Orange) }
                            TextButton(onClick = onAddFolder) { Text("Folder", color = Orange) }
                            TextButton(onClick = onAnalyze) { Text("Listen", color = Orange) }
                        }
                    }
                    MiniPlayer(
                        track = snap.current,
                        playing = ui.playing,
                        onOpen = onOpenPlayer,
                        onToggle = onTogglePlay,
                        onSkip = onSkip,
                        onPrevious = onPrevious,
                    )
                    Button(
                        onClick = onPlayContext,
                        enabled = !ui.busy && !ui.job.running &&
                            (snap.queue.isNotEmpty() || snap.stars.isNotEmpty()),
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
        if (ui.busy || ui.job.running) {
            JobChip(
                ui = ui,
                onCancel = onCancelJob,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 52.dp, end = 12.dp),
            )
        }
    }
}

@Composable
internal fun MiniPlayer(
    track: TrackRow?,
    playing: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onSkip: () -> Unit,
    onPrevious: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Card)
            .clickable(onClick = onOpen)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackArt(
            track?.path.orEmpty(),
            track?.title ?: "Meridian",
            Modifier.size(44.dp),
            corner = 10.dp,
            useAlbumArt = false,
        )
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
            Text(
                track?.title ?: "Nothing playing",
                color = Ink,
                fontSize = 14.sp,
                lineHeight = 17.sp,
                maxLines = 2,
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    tint = Cream,
                    modifier = Modifier.size(22.dp),
                )
            }
            IconButton(onClick = onToggle, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = Cream,
                    modifier = Modifier.size(22.dp),
                )
            }
            IconButton(onClick = onSkip, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = Cream,
                    modifier = Modifier.size(22.dp),
                )
            }
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
