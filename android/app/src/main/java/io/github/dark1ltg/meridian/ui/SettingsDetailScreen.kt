package io.github.dark1ltg.meridian.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dark1ltg.meridian.ui.theme.Cream
import io.github.dark1ltg.meridian.ui.theme.Ink
import io.github.dark1ltg.meridian.ui.theme.Mute
import io.github.dark1ltg.meridian.ui.theme.Night
import io.github.dark1ltg.meridian.ui.theme.Orange

enum class SettingsPage { Appearance, Privacy, About }

@Composable
fun SettingsDetailScreen(page: SettingsPage, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val (title, body) = when (page) {
        SettingsPage.Appearance -> "Appearance" to """
            Dark nebula. Cream type. Orange for the things you can touch.

            Meridian is built for night listening. There is no light theme and no extra chrome skins — the sky, the lens, and the matrix stay as they are so the map stays readable.
        """.trimIndent()
        SettingsPage.Privacy -> "Privacy" to """
            Nothing leaves this phone.

            There is no account, no cloud library, no scrobble, and no analytics. Scan and Listen read files you grant access to. The SQLite library and mood pins live in Meridian’s app data. Lockscreen controls show title and artist only — no album art.

            Opening Meridian from those controls still requires your device lock.
        """.trimIndent()
        SettingsPage.About -> "About" to """
            Meridian 1.3.5
            GPL-3.0-only

            Your library is a night sky. Navigate by feel.

            Engine: the same Python mood map, listen matrix, and context queue as the Linux app (Chaquopy). Playback: Media3 with a ~3s dual-deck crossfade. Listen decode: MediaCodec, then bundled FFmpeg JNI. Rhythm: aubio.

            Ubuntu Sans / Ubuntu Condensed — Ubuntu Font Licence 1.0.
        """.trimIndent()
    }
    Column(
        modifier
            .fillMaxSize()
            .background(Night)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(
            "Back",
            color = Orange,
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(bottom = 16.dp),
        )
        Text(title, color = Cream, fontSize = 28.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))
        Text(body, color = Ink, fontSize = 15.sp, lineHeight = 22.sp)
        Spacer(Modifier.height(24.dp))
        Text("Offline. Local. Yours.", color = Mute, fontSize = 13.sp)
    }
}
