package io.github.dark1ltg.meridian

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import io.github.dark1ltg.meridian.data.TrackRow
import io.github.dark1ltg.meridian.data.emptySnapshot
import io.github.dark1ltg.meridian.ui.AppTab
import io.github.dark1ltg.meridian.ui.ContextScreen
import io.github.dark1ltg.meridian.ui.MainScreen
import io.github.dark1ltg.meridian.ui.MoodMap
import io.github.dark1ltg.meridian.ui.SettingsDetailScreen
import io.github.dark1ltg.meridian.ui.SettingsPage
import io.github.dark1ltg.meridian.ui.OnboardingScreen
import io.github.dark1ltg.meridian.ui.Overlay
import io.github.dark1ltg.meridian.ui.PermissionScreen
import io.github.dark1ltg.meridian.ui.SplashScreen
import io.github.dark1ltg.meridian.ui.UiState
import io.github.dark1ltg.meridian.ui.WhyTrackScreen
import io.github.dark1ltg.meridian.ui.theme.MeridianTheme
import org.junit.Rule
import org.junit.Test

class EmptyUiScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.NoActionBar",
        showSystemUi = false,
    )

    private val longNow = TrackRow(
        id = 1,
        path = "",
        title = "The Long Now",
        artist = "Dead Can Dance",
        album = "Into the Labyrinth",
        durationMs = 455_000,
        valence = 0.62f,
        energy = 0.48f,
        confidence = 0.81f,
        lowTrust = false,
        pinned = false,
        loved = true,
        quadrant = "now",
        fit = 0.82f,
        importance = 0.61f,
        playCount = 6,
        why = "Dead Can Dance — The Long Now\nNOW — closest to the lens (82% fit), high importance (61%)\nWhy: ♥ loved — boosted importance · clock band: Night · mode: Wander",
    )

    private val populated = UiState(
        snapshot = emptySnapshot().copy(
            bandLabel = "Night",
            mode = "wander",
            trackCount = 32,
            lensX = 0.52f,
            lensY = 0.58f,
            lensRadius = 0.34f,
            current = longNow,
            queue = listOf(
                longNow,
                longNow.copy(id = 2, title = "Cantara", durationMs = 342_000, valence = 0.7f, energy = 0.6f, quadrant = "fill", loved = false),
                longNow.copy(id = 3, title = "Desert Song", artist = "Dead Can Dance", durationMs = 381_000, valence = 0.4f, energy = 0.35f, quadrant = "deep", loved = false),
            ),
            stars = buildList {
                add(longNow)
                val quadrants = listOf("now", "deep", "fill", "shelf")
                var i = 2
                while (i <= 48) {
                    val t = i * 1.6180339887f
                    val v = ((t * 0.37f) % 1f)
                    val e = ((t * 0.21f + 0.13f) % 1f)
                    add(
                        longNow.copy(
                            id = i,
                            title = "Star $i",
                            valence = 0.08f + v * 0.84f,
                            energy = 0.08f + e * 0.84f,
                            quadrant = quadrants[i % 4],
                            loved = false,
                            confidence = 0.55f + (i % 5) * 0.08f,
                        ),
                    )
                    i++
                }
            },
            now = listOf(longNow),
            deep = listOf(longNow.copy(id = 3, title = "Desert Song", valence = 0.4f, energy = 0.35f, quadrant = "deep", loved = false, why = "DEEP — important but just outside the lens core")),
            fill = listOf(longNow.copy(id = 2, title = "Cantara", valence = 0.7f, energy = 0.6f, quadrant = "fill", loved = false)),
            shelf = listOf(longNow.copy(id = 8, title = "The Ubiquitous Mr. Lovegrove", valence = 0.2f, energy = 0.2f, quadrant = "shelf", loved = false)),
            folders = listOf("/storage/emulated/0/Music", "/storage/emulated/0/Download"),
            job = io.github.dark1ltg.meridian.data.JobProgress(),
        ),
        playing = true,
        positionMs = 204_000,
        durationMs = 455_000,
    )

    @Test
    fun splash() {
        paparazzi.snapshot("splash") { MeridianTheme { SplashScreen(onContinue = {}) } }
    }

    @Test
    fun onboarding() {
        paparazzi.snapshot("onboarding") {
            MeridianTheme { OnboardingScreen(onGetStarted = {}, onSkip = {}) }
        }
    }

    @Test
    fun permission() {
        paparazzi.snapshot("permission") {
            MeridianTheme { PermissionScreen(onContinue = { _, _, _, _ -> }, onNotNow = {}) }
        }
    }

    @Test
    fun mapPopulated() {
        paparazzi.snapshot("map_populated") {
            MeridianTheme { MainScreen(ui = populated, initialTab = AppTab.Map) }
        }
    }

    @Test
    fun queuePopulated() {
        paparazzi.snapshot("queue_populated") {
            MeridianTheme { MainScreen(ui = populated, initialTab = AppTab.Queue) }
        }
    }

    @Test
    fun contextPopulated() {
        paparazzi.snapshot("context_populated") {
            MeridianTheme {
                ContextScreen(
                    snapshot = populated.snapshot,
                    onPlay = {},
                    onPlayContext = {},
                    onOpenWhy = {},
                    onLens = { _, _, _ -> },
                    onPin = { _, _, _ -> },
                )
            }
        }
    }

    @Test
    fun settingsAbout() {
        paparazzi.snapshot("settings_about") {
            MeridianTheme { SettingsDetailScreen(page = SettingsPage.About, onBack = {}) }
        }
    }

    @Test
    fun settings() {
        paparazzi.snapshot("settings") {
            MeridianTheme { MainScreen(ui = populated, initialTab = AppTab.More) }
        }
    }

    @Test
    fun nowPlaying() {
        paparazzi.snapshot("now_playing") {
            MeridianTheme {
                MainScreen(ui = populated, initialTab = AppTab.Map, initialOverlay = Overlay.NowPlaying)
            }
        }
    }

    @Test
    fun whyTrack() {
        paparazzi.snapshot("why_track") {
            MeridianTheme {
                WhyTrackScreen(
                    track = longNow,
                    snapshot = populated.snapshot,
                    onPlay = {},
                    onViewInMap = {},
                )
            }
        }
    }

    @Test
    fun mapZoomedLabels() {
        paparazzi.snapshot("map_zoomed") {
            MeridianTheme {
                MoodMap(
                    snapshot = populated.snapshot,
                    onLens = { _, _, _ -> },
                    onPlay = {},
                    onPin = { _, _, _ -> },
                    previewZoom = 1.8f,
                )
            }
        }
    }

    @Test
    fun lockscreenNotification() {
        paparazzi.snapshot("lockscreen_notification") {
            MeridianTheme { LockscreenMediaFixture(title = longNow.title, artist = longNow.artist) }
        }
    }
}

@Composable
private fun LockscreenMediaFixture(title: String, artist: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF05060A)),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 48.dp),
        ) {
            Text("12:41", color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Light)
            Text("Thu, Sep 10", color = Color(0xFFB0B8C8), fontSize = 18.sp)
            Spacer(Modifier.weight(1f))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xE6141C2E))
                    .padding(20.dp),
            ) {
                Text("MERIDIAN", color = Color(0xFF8B95AD), fontSize = 11.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(10.dp))
                Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Text(artist, color = Color(0xFF8B95AD), fontSize = 15.sp, maxLines = 1)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Text("⏮", color = Color.White, fontSize = 22.sp)
                    Text("⏸", color = Color.White, fontSize = 22.sp)
                    Text("⏭", color = Color.White, fontSize = 22.sp)
                }
            }
        }
    }
}
