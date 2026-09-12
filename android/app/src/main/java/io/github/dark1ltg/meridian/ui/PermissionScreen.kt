package io.github.dark1ltg.meridian.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontWeight
import io.github.dark1ltg.meridian.ui.theme.UbuntuSans
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dark1ltg.meridian.ui.theme.Card
import io.github.dark1ltg.meridian.ui.theme.Cream
import io.github.dark1ltg.meridian.ui.theme.Ink
import io.github.dark1ltg.meridian.ui.theme.Mute
import io.github.dark1ltg.meridian.ui.theme.Orange

@Composable
fun PermissionScreen(
    onContinue: (music: Boolean, downloads: Boolean, other: Boolean, allAudio: Boolean) -> Unit,
    onNotNow: () -> Unit,
) {
    var music by remember { mutableStateOf(true) }
    var downloads by remember { mutableStateOf(false) }
    var other by remember { mutableStateOf(false) }
    var allAudio by remember { mutableStateOf(true) }
    Box(Modifier.fillMaxSize()) {
        Sky(seed = 19)
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0x33FF7A1A)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.LibraryMusic, contentDescription = null, tint = Orange, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Allow Meridian to access your music",
                color = Cream,
                fontSize = 24.sp,
                fontFamily = UbuntuSans,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "Meridian needs access to your music files and folders to build your library and run analysis. You can choose which folders to share.",
                color = Mute,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 10.dp, bottom = 24.dp),
            )
            FolderToggle("Music", music) { music = it }
            FolderToggle("Downloads", downloads) { downloads = it }
            FolderToggle("All audio on this phone", allAudio) { allAudio = it }
            FolderToggle("Other folders", other) { other = it }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { onContinue(music, downloads, other, allAudio) },
                colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(28.dp),
            ) {
                Text("Continue", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            TextButton(onClick = onNotNow) {
                Text("Not now", color = Mute)
            }
        }
    }
}

@Composable
private fun FolderToggle(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Ink, fontSize = 16.sp)
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Orange,
                uncheckedTrackColor = Color(0xFF2A3348),
            ),
        )
    }
}
