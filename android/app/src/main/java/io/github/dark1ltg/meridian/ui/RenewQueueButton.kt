package io.github.dark1ltg.meridian.ui

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.dark1ltg.meridian.ui.theme.Cream
import io.github.dark1ltg.meridian.ui.theme.Ink
import io.github.dark1ltg.meridian.ui.theme.Mute
import io.github.dark1ltg.meridian.ui.theme.Orange

private const val PREFS = "meridian"
private const val RENEW_EXPLAINED = "renew_explained"

/** Desktop-style Renew Queue control with a one-time explainer. */
@Composable
fun RenewQueueButton(onRenewQueue: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var showExplain by remember { mutableStateOf(false) }

    if (showExplain) {
        AlertDialog(
            onDismissRequest = { showExplain = false },
            title = { Text("Renew the context queue?", color = Cream) },
            text = {
                Text(
                    "Renew picks a fresh set of songs from the neighborhood you’re looking " +
                        "at on the mood map (the listen matrix).\n\n" +
                        "This is not shuffle, and it does not count replaced songs as skips. " +
                        "What’s already playing stays put.\n\n" +
                        "Moving the lens updates suggestions in the matrix; the queue itself " +
                        "holds until it runs low or you Renew.",
                    color = Ink,
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        prefs.edit().putBoolean(RENEW_EXPLAINED, true).apply()
                        showExplain = false
                        onRenewQueue()
                    },
                ) {
                    Text("Renew", color = Orange)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExplain = false }) {
                    Text("Not now", color = Mute)
                }
            },
            containerColor = Color(0xFF121A32),
        )
    }

    TextButton(
        onClick = {
            if (prefs.getBoolean(RENEW_EXPLAINED, false)) {
                onRenewQueue()
            } else {
                showExplain = true
            }
        },
    ) {
        Text("Renew", color = Orange, fontWeight = FontWeight.SemiBold)
    }
}
