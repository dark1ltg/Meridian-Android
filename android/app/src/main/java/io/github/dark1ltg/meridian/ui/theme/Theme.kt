package io.github.dark1ltg.meridian.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Night = Color(0xFF070B18)
val NebulaDeep = Color(0xFF14082C)
val Panel = Color(0xE6121A32)
val Card = Color(0xCC161E36)
val Orange = Color(0xFFFF7A1A)
val Gold = Color(0xFFE8B86D)
val Cream = Color(0xFFF6F0E8)
val Ink = Color(0xFFE8EEF8)
val Mute = Color(0xFF8B95AD)
val Now = Color(0xFFFFBF00)
val Deep = Color(0xFF5CE1FF)
val Fill = Color(0xFFFF4D9A)
val Shelf = Color(0xFF9DFF5C)
val LowTrust = Color(0xFF8B9BB8)
val Lens = Color(0xFFB8F0FF)

private val scheme = darkColorScheme(
    primary = Orange,
    onPrimary = Color.White,
    background = Night,
    onBackground = Ink,
    surface = Panel,
    onSurface = Ink,
    secondary = Deep,
    tertiary = Fill,
)

@Composable
fun MeridianTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = meridianTypography(), content = content)
}

fun quadrantColor(quadrant: String) = when (quadrant) {
    "now" -> Now
    "deep" -> Deep
    "fill" -> Fill
    "shelf" -> Shelf
    else -> Gold
}
