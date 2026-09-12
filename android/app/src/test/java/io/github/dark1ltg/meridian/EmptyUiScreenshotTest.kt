package io.github.dark1ltg.meridian

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.dark1ltg.meridian.ui.ArtworkPlaceholder
import io.github.dark1ltg.meridian.ui.MeridianTheme
import io.github.dark1ltg.meridian.ui.MeridianThemeProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class EmptyUiScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val roborazziRule =
        RoborazziRule(
            options =
                RoborazziRule.Options(
                    outputDirectoryPath = "src/test/snapshots/roborazzi",
                    outputFileProvider = { description, outputDirectory, fileExtension ->
                        File(outputDirectory, "${description.methodName}.$fileExtension")
                    },
                ),
        )

    @Test
    fun emptyLibraryArtworkPlaceholder() {
        composeRule.setContent {
            MeridianThemeProvider {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(MeridianTheme.colors.bg),
                ) {
                    ArtworkPlaceholder(
                        artist = "Unknown Artist",
                        title = "Untitled",
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage()
    }
}
