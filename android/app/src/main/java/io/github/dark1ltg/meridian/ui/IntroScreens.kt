package io.github.dark1ltg.meridian.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import io.github.dark1ltg.meridian.ui.theme.UbuntuSans
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dark1ltg.meridian.ui.theme.Cream
import io.github.dark1ltg.meridian.ui.theme.Ink
import io.github.dark1ltg.meridian.ui.theme.Mute
import io.github.dark1ltg.meridian.ui.theme.Orange

@Composable
fun SplashScreen(onContinue: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Sky(seed = 3)
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            OrbitMark()
            Spacer(Modifier.height(28.dp))
            Text(
                "MERIDIAN",
                color = Cream,
                fontSize = 34.sp,
                fontFamily = UbuntuSans,
                letterSpacing = 6.sp,
                fontWeight = FontWeight.Normal,
            )
            Text(
                "Your music. In context.",
                color = Ink.copy(alpha = 0.75f),
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(listOf(Orange, Color(0xFFFFC14D), Color.Transparent)),
                    ),
            )
            Text(
                "Your music as a night sky.\nNavigate by feel.",
                color = Mute,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp, bottom = 36.dp),
            )
            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text("Enter", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
fun OnboardingScreen(onGetStarted: () -> Unit, onSkip: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Sky(seed = 11)
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            Spacer(Modifier.height(36.dp))
            Text(
                "Welcome to Meridian",
                color = Cream,
                fontSize = 28.sp,
                fontFamily = UbuntuSans,
            )
            Text(
                "Your music as a night sky.\nNavigate by feel.",
                color = Mute,
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 28.dp),
            )
            OnboardStep("Explore your music", "Move through your universe by sound, mood and emotion.")
            OnboardStep("Use the lens", "Zoom in, find what feels right.")
            OnboardStep("Build your context", "Let Meridian create a queue around your current moment.")
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onGetStarted,
                colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text("Get Started", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            TextButton(onClick = onSkip, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Skip", color = Mute)
            }
        }
    }
}

@Composable
private fun OnboardStep(title: String, body: String) {
    Row(
        Modifier.padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color(0x33FF7A1A)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Orange),
            )
        }
        Column {
            Text(title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(body, color = Mute, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun OrbitMark() {
    Canvas(Modifier.size(220.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val maxR = size.minDimension / 2f
        listOf(0.42f, 0.62f, 0.86f).forEach { t ->
            drawCircle(Color.White.copy(alpha = 0.18f), maxR * t, c, style = Stroke(1.2f))
        }
        drawCircle(Color(0xFFFF7A1A).copy(alpha = 0.55f), 16f, c)
        drawCircle(Color(0xFFFFC14D), 7f, Offset(c.x + maxR * 0.42f, c.y - 8f))
        drawCircle(Color(0xFF5CE1FF), 5f, Offset(c.x - maxR * 0.28f, c.y + maxR * 0.18f))
        drawCircle(Color(0xFFFF4D9A), 6f, Offset(c.x + 12f, c.y + maxR * 0.5f))
        drawLine(
            Color(0xFFFF7A1A),
            Offset(c.x - maxR * 0.5f, c.y + maxR * 0.72f),
            Offset(c.x + maxR * 0.2f, c.y + maxR * 0.72f),
            4f,
            StrokeCap.Round,
        )
    }
}
