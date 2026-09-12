package io.github.dark1ltg.meridian.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.github.dark1ltg.meridian.R

/** Same standalone TTFs as desktop `resources/fonts/` (Ubuntu Font Licence 1.0). */
@OptIn(ExperimentalTextApi::class)
private fun ubuntuSans(weight: FontWeight) = Font(
    resId = R.font.ubuntu_sans,
    weight = weight,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
    ),
)

val UbuntuSans = FontFamily(
    ubuntuSans(FontWeight.Light),
    ubuntuSans(FontWeight.Normal),
    ubuntuSans(FontWeight.Medium),
    ubuntuSans(FontWeight.SemiBold),
    ubuntuSans(FontWeight.Bold),
)

val UbuntuCondensed = FontFamily(
    Font(R.font.ubuntu_condensed, FontWeight.Normal),
)

fun meridianTypography(): Typography {
    val base = Typography()
    fun TextStyle.ubuntu() = copy(fontFamily = UbuntuSans)
    return base.copy(
        displayLarge = base.displayLarge.ubuntu(),
        displayMedium = base.displayMedium.ubuntu(),
        displaySmall = base.displaySmall.ubuntu(),
        headlineLarge = base.headlineLarge.ubuntu(),
        headlineMedium = base.headlineMedium.ubuntu(),
        headlineSmall = base.headlineSmall.ubuntu(),
        titleLarge = base.titleLarge.ubuntu(),
        titleMedium = base.titleMedium.ubuntu(),
        titleSmall = base.titleSmall.ubuntu(),
        bodyLarge = base.bodyLarge.ubuntu(),
        bodyMedium = base.bodyMedium.ubuntu(),
        bodySmall = base.bodySmall.ubuntu(),
        labelLarge = base.labelLarge.ubuntu(),
        labelMedium = base.labelMedium.ubuntu(),
        labelSmall = base.labelSmall.ubuntu(),
    )
}
