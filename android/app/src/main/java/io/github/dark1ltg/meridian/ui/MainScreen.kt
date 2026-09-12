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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import io.github.dark1ltg.meridian.ui.theme.UbuntuSans
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dark1ltg.meridian.data.TrackRow
import io.github.dark1ltg.meridian.ui.theme.Card
import io.github.dark1ltg.meridian.ui.theme.Cream
import io.github.dark1ltg.meridian.ui.theme.Ink
import io.github.dark1ltg.meridian.ui.theme.Mute
import io.github.dark1ltg.meridian.ui.theme.Orange

enum class AppTab { Map, Queue, Context, More }

sealed class Overlay {
    data object None : Overlay()
    data object Search : Overlay()
    data object NowPlaying : Overlay()
    data class Why(val id: Int) : Overlay()
    data class Settings(val page: SettingsPage) : Overlay()
}

@Composable
fun MainScreen(vm: SessionViewModel, ui: UiState) {
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
    initialTab: AppTab = AppTab.Map,
    initialOverlay: Overlay = Overlay.None,
) {
    var tab by remember { mutableStateOf(initialTab) }
    var overlay by remember { mutableStateOf(initialOverlay) }
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

    Box(Modifier.fillMaxSize()) {
        if (tab != AppTab.Map) Sky()
        if (tab == AppTab.Map) {
            MapTab(
                ui = ui,
                onLens = onLens,
                onPlay = onPlay,
                onPin = onPin,
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
            )
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                if (ui.busy) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Orange,
                        trackColor = Color(0x22FFFFFF),
                    )
                }
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        AppTab.Map -> Unit
                        AppTab.Queue -> QueueScreen(
                            tracks = snap.queue,
                            currentId = snap.current?.id,
                            queueIndex = snap.queueIndex,
                            onPlay = onPlay,
                            onLove = onLove,
                            onOpenWhy = { overlay = Overlay.Why(it) },
                        )
                        AppTab.Context -> ContextScreen(
                            snapshot = snap,
                            onPlay = onPlay,
                            onPlayContext = onPlayContext,
                            onOpenWhy = { overlay = Overlay.Why(it) },
                            onLens = onLens,
                            onPin = onPin,
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
                            onCancel = onCancelJob,
                            busy = ui.busy,
                            job = ui.job,
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
                query = snap.searchQuery,
                rows = snap.search,
                currentId = snap.current?.id,
                onQuery = onSearch,
                onPlay = onPlay,
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
                onLove = { snap.current?.id?.let(onLove) },
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
    }
}

@Composable
private fun MapTopBar(onMenu: () -> Unit, onSearch: () -> Unit) {
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
) {
    val snap = ui.snapshot
    val nearby = snap.stars.count { inLens(it, snap.lensX, snap.lensY, snap.lensRadius) }
        .coerceAtLeast(snap.queue.size)
    val (glow, kinetic) = moodWord(snap.lensX, snap.lensY)
    var skyHint by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize()) {
        MoodMap(
            snapshot = snap,
            onLens = onLens,
            onPlay = onPlay,
            onPin = onPin,
            onHover = { skyHint = it },
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            MapTopBar(onMenu = onMenu, onSearch = onSearch)
            if (ui.busy || ui.job.running) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    if (ui.job.total > 0) {
                        LinearProgressIndicator(
                            progress = { ui.job.fraction },
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
                    ) {
                        Text(
                            ui.job.message.ifBlank { ui.message },
                            color = Mute,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "Cancel",
                            color = Orange,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable(onClick = onCancelJob),
                        )
                    }
                }
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
                    .padding(top = 36.dp),
            ) {
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Shadow   ·   Glow", color = Mute, fontSize = 12.sp)
                        Text("Still   ·   Kinetic", color = Mute, fontSize = 12.sp)
                    }
                    Text(
                        when {
                            skyHint.isNotBlank() -> skyHint
                            snap.trackCount == 0 -> ui.message
                            else -> "$nearby tracks nearby"
                        },
                        color = Ink,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                    Text(
                        listOfNotNull(snap.bandLabel.ifBlank { null }, glow, kinetic)
                            .joinToString("  ·  "),
                        color = Mute,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                    )
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
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
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
                contentDescription = "Play",
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
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xCC0C1220))
            .padding(vertical = 8.dp),
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
    query: String,
    rows: List<TrackRow>,
    currentId: Int?,
    onQuery: (String) -> Unit,
    onPlay: (Int) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xEE070B18))
            .statusBarsPadding()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
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
            if (query.isNotBlank() && rows.isEmpty()) {
                item { Text("No matches.", color = Mute) }
            }
            items(rows, key = { it.id }) { row ->
                QueueRow(
                    index = rows.indexOf(row) + 1,
                    row = row,
                    active = row.id == currentId,
                    onPlay = { onPlay(row.id) },
                    onLove = {},
                    onOpenWhy = { onPlay(row.id) },
                )
            }
        }
    }
}
