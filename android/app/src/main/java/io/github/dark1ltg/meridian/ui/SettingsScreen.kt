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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dark1ltg.meridian.data.FolderPaths
import io.github.dark1ltg.meridian.ui.theme.Card
import io.github.dark1ltg.meridian.ui.theme.Cream
import io.github.dark1ltg.meridian.ui.theme.Ink
import io.github.dark1ltg.meridian.ui.theme.Mute
import io.github.dark1ltg.meridian.ui.theme.Orange

@Composable
fun SettingsScreen(
    folders: List<String>,
    mode: String,
    onMode: (String) -> Unit,
    onScan: () -> Unit,
    onAnalyze: () -> Unit,
    onRescan: () -> Unit,
    onAddFolder: () -> Unit,
    onRemoveFolder: (String) -> Unit,
    busy: Boolean,
    onIndexAll: () -> Unit = {},
    onRecoverAudioAccess: () -> Unit = {},
    onOpen: (SettingsPage) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var advanced by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Text(
            "Settings",
            color = Cream,
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        SettingsRow(Icons.Outlined.Folder, "Library", "Locations & listening")
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 56.dp, bottom = 12.dp),
        ) {
            Button(
                onClick = onAnalyze,
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(22.dp),
            ) {
                Text("Listen", fontWeight = FontWeight.SemiBold)
            }
            Text(
                if (advanced) "Hide advanced" else "Advanced",
                color = Orange,
                fontSize = 13.sp,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clickable { advanced = !advanced },
            )
            if (advanced) {
                Row(
                    Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = onScan, enabled = !busy) { Text("Scan", color = Orange) }
                    TextButton(onClick = onRescan, enabled = !busy) { Text("Rescan", color = Orange) }
                }
            }
            if (folders.isEmpty()) {
                Text(
                    "No folders yet.",
                    color = Mute,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                folders.forEach { path ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(FolderPaths.displayName(path), color = Ink, fontSize = 14.sp)
                            Text(path, color = Mute, fontSize = 11.sp, maxLines = 2)
                        }
                        Text(
                            "Remove",
                            color = Orange,
                            fontSize = 12.sp,
                            // Always allow Remove — ViewModel cancels any busy job first.
                            modifier = Modifier.clickable { onRemoveFolder(path) },
                        )
                    }
                }
            }
            // Always allow Add folder so a stuck scan cannot freeze the picker.
            TextButton(onClick = onAddFolder) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = Orange, modifier = Modifier.size(18.dp))
                Text("  Add folder", color = Orange)
            }
            TextButton(onClick = onRecoverAudioAccess) {
                Text("Allow music access", color = Orange)
            }
            TextButton(onClick = onIndexAll, enabled = !busy) {
                Text("Index all audio on this phone", color = Orange)
            }
        }
        SettingsRow(Icons.Outlined.GraphicEq, "Audio", "Playback & sound")
        Row(
            Modifier
                .padding(start = 56.dp, bottom = 14.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("focus" to "Focus", "wander" to "Wander", "charge" to "Charge", "dim" to "Dim").forEach { (id, label) ->
                val on = mode == id
                Text(
                    label,
                    color = if (on) Color.White else Ink,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (on) Orange else Card)
                        .clickable { onMode(id) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        SettingsRow(Icons.Outlined.Palette, "Appearance", "Theme & display") { onOpen(SettingsPage.Appearance) }
        Text(
            "Dark nebula. Built for night listening.",
            color = Mute,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 56.dp, bottom = 14.dp),
        )
        SettingsRow(Icons.Outlined.Tune, "Listening mode", "Focus, Wander, Charge, Dim")
        SettingsRow(Icons.Outlined.Shield, "Privacy", "Nothing leaves this phone") { onOpen(SettingsPage.Privacy) }
        SettingsRow(Icons.Outlined.Info, "About", "Meridian 1.3.6") { onOpen(SettingsPage.About) }
        Spacer(Modifier.height(12.dp))
        Text(
            "Offline. Local. Yours.",
            color = Mute,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Card),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Orange)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, color = Ink, fontSize = 16.sp)
            Text(subtitle, color = Mute, fontSize = 12.sp)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Mute)
    }
}
