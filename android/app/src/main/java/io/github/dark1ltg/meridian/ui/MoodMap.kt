package io.github.dark1ltg.meridian.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import android.graphics.Paint as AndroidPaint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import io.github.dark1ltg.meridian.data.EngineSnapshot
import io.github.dark1ltg.meridian.data.TrackRow
import io.github.dark1ltg.meridian.ui.theme.LowTrust
import io.github.dark1ltg.meridian.ui.theme.quadrantColor
import kotlin.math.hypot
import kotlin.math.min

@Composable
fun MoodMap(
    snapshot: EngineSnapshot,
    onLens: (Float, Float, Float) -> Unit,
    onPlay: (Int) -> Unit,
    onPin: (Int, Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    showSky: Boolean = true,
    compact: Boolean = false,
    previewZoom: Float = 1f,
    onHover: (String) -> Unit = {},
) {
    val fieldCount = when {
        compact -> 280
        snapshot.stars.size > 900 -> 420
        else -> 900
    }
    val field = remember(fieldCount) { galaxyField(seed = 42, count = fieldCount) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var zoom by remember { mutableFloatStateOf(previewZoom.coerceIn(1f, 3.2f)) }
    var hover by remember { mutableStateOf<Pair<Offset, String>?>(null) }
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(snapshot.stars, compact) {
                detectTapGestures { pos ->
                    nearest(
                        snapshot.stars,
                        pos,
                        size.width,
                        size.height,
                        44f,
                        pan,
                        zoom,
                    )?.let { onPlay(it.id) }
                }
            }
            .pointerInput(snapshot.stars, snapshot.lensX, snapshot.lensY, snapshot.lensRadius, compact) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    var localPan = pan
                    var localZoom = zoom
                    val star = nearest(snapshot.stars, down.position, size.width, size.height, 44f, localPan, localZoom)
                    if (star != null) {
                        val sp = moodToPx(star.valence, star.energy, w, h, localPan, localZoom)
                        hover = sp to "${star.title} · ${star.artist}"
                        onHover(hover!!.second)
                    }
                    val lensPx = moodToPx(snapshot.lensX, snapshot.lensY, w, h, localPan, localZoom)
                    val rr = snapshot.lensRadius.coerceIn(0.08f, 0.48f) * min(w, h) * localZoom
                    val nearLens = hypot(
                        (down.position.x - lensPx.x).toDouble(),
                        (down.position.y - lensPx.y).toDouble(),
                    ) <= rr * 1.12
                    var pinId: Int? = null
                    var lastPin = screenToMood(down.position, w, h, localPan, localZoom)
                    var moved = false
                    var travel = 0f
                    val pinSlop = 18f
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val centroid = event.calculateCentroid(useCurrent = true)
                        val liveLens = moodToPx(snapshot.lensX, snapshot.lensY, w, h, localPan, localZoom)
                        val liveRr = snapshot.lensRadius.coerceIn(0.08f, 0.48f) * min(w, h) * localZoom
                        if (pressed.size >= 2 && zoomChange != 1f) {
                            pinId = null
                            val inside = hypot(
                                (centroid.x - liveLens.x).toDouble(),
                                (centroid.y - liveLens.y).toDouble(),
                            ) <= liveRr * 1.08
                            if (inside || compact) {
                                val r = (snapshot.lensRadius * zoomChange).coerceIn(0.08f, 0.48f)
                                onLens(snapshot.lensX, snapshot.lensY, r)
                            } else {
                                localZoom = (localZoom * zoomChange).coerceIn(1f, 3.2f)
                                zoom = localZoom
                            }
                            pressed.forEach { if (it.positionChanged()) it.consume() }
                            moved = true
                        } else if (pressed.size == 1 && (panChange.x != 0f || panChange.y != 0f)) {
                            travel += hypot(panChange.x.toDouble(), panChange.y.toDouble()).toFloat()
                            if (travel < pinSlop) {
                                continue
                            }
                            if (pinId == null && star != null) {
                                pinId = star.id
                            }
                            val p = pressed[0].position
                            val tip = nearest(snapshot.stars, p, size.width, size.height, 56f, localPan, localZoom)
                            if (tip != null && pinId == null) {
                                val sp = moodToPx(tip.valence, tip.energy, w, h, localPan, localZoom)
                                hover = sp to "${tip.title} · ${tip.artist}"
                                onHover(hover!!.second)
                            }
                            if (pinId != null) {
                                lastPin = screenToMood(p, w, h, localPan, localZoom)
                            } else if (nearLens || compact) {
                                val mood = screenToMood(p, w, h, localPan, localZoom)
                                onLens(mood.first, mood.second, snapshot.lensRadius)
                            } else {
                                localPan = Offset(localPan.x - panChange.x / localZoom, localPan.y - panChange.y / localZoom)
                                pan = localPan
                            }
                            pressed.forEach { if (it.positionChanged()) it.consume() }
                            moved = true
                        }
                    } while (event.changes.any { it.pressed })
                    if (pinId != null && moved) {
                        onPin(pinId, lastPin.first, lastPin.second)
                    }
                }
            },
    ) {
        val w = size.width
        val h = size.height
        if (showSky) {
            drawRect(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF1A0A3A), Color(0xFF090614), Color(0xFF030208)),
                    center = Offset(w * 0.5f, h * 0.44f),
                    radius = min(w, h) * 0.95f,
                ),
            )
            drawGalaxy(field, MapLod.galaxyAlpha(zoom))
        }
        val glow = MapLod.glowScale(zoom)
        val chrome = MapLod.chromeAlpha(zoom)
        val labelCandidates = ArrayList<Pair<Offset, TrackRow>>(32)
        snapshot.stars.forEach { star ->
            val p = moodToPx(star.valence, star.energy, w, h, pan, zoom)
            if (!MapLod.onScreen(p.x, p.y, w, h)) return@forEach
            val color = if (star.lowTrust || star.confidence < 0.45f) {
                LowTrust
            } else {
                quadrantColor(star.quadrant)
            }
            val selected = snapshot.current?.id == star.id
            val core = when (star.quadrant) {
                "now" -> 3.4f
                "deep" -> 3.1f
                "fill" -> 3.0f
                else -> 2.7f
            } * (0.85f + star.confidence.coerceIn(0.3f, 1f) * 0.35f) * (0.85f + zoom * 0.12f) * glow
            if (MapLod.shouldBloom(zoom, star.quadrant, selected)) {
                drawBloomStar(p, color, core, selected)
            } else {
                drawCircle(color.copy(alpha = 0.85f), (core * 0.7f).coerceAtLeast(1.4f), p)
            }
            if (!compact && MapLod.shouldLabel(zoom)) {
                labelCandidates += p to star
            }
        }
        if (labelCandidates.isNotEmpty()) {
            val cx = w / 2f
            val cy = h / 2f
            labelCandidates.sortBy { hypot((it.first.x - cx).toDouble(), (it.first.y - cy).toDouble()) }
            labelCandidates.take(MapLod.MAX_LABELS).forEach { (p, star) ->
                drawStarLabel(p, star.title)
            }
        }
        hover?.let { (p, text) ->
            if (!compact) drawStarLabel(Offset(p.x, p.y - 18f), text)
        }
        val lens = moodToPx(snapshot.lensX, snapshot.lensY, w, h, pan, zoom)
        val rr = snapshot.lensRadius.coerceIn(0.12f, 0.48f) * min(w, h) * zoom
        val shade = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(0f, 0f, w, h))
            addOval(Rect(lens.x - rr, lens.y - rr, lens.x + rr, lens.y + rr))
        }
        drawPath(shade, Color(0x99060812).copy(alpha = 0.6f * chrome))
        drawCircle(Color(0xFFB8F4FF).copy(alpha = 0.10f * chrome), rr * 1.08f, lens)
        drawCircle(
            Brush.radialGradient(
                colors = listOf(Color(0x22E8FFFF).copy(alpha = 0.13f * chrome), Color.Transparent),
                center = lens,
                radius = rr,
            ),
            radius = rr,
            center = lens,
        )
        drawCircle(Color(0x66D7F6FF).copy(alpha = 0.4f * chrome), rr, lens, style = Stroke(14f))
        drawCircle(Color(0xCCF4FFFF).copy(alpha = chrome), rr, lens, style = Stroke(2.8f))
        drawCircle(Color.White.copy(alpha = 0.35f * chrome), rr * 0.78f, lens, style = Stroke(1.1f))
    }
}

private fun DrawScope.drawStarLabel(p: Offset, title: String) {
    val paint = AndroidPaint().apply {
        color = android.graphics.Color.argb(210, 246, 240, 232)
        textSize = 26f
        isAntiAlias = true
        isFakeBoldText = true
    }
    drawContext.canvas.nativeCanvas.drawText(title.take(22), p.x + 10f, p.y - 8f, paint)
}

internal fun moodToPx(
    valence: Float,
    energy: Float,
    w: Float,
    h: Float,
    pan: Offset = Offset.Zero,
    zoom: Float = 1f,
): Offset {
    val world = Offset(valence * w, (1f - energy) * h)
    val cx = w / 2f
    val cy = h / 2f
    return Offset(
        cx + (world.x - cx - pan.x) * zoom,
        cy + (world.y - cy - pan.y) * zoom,
    )
}

internal fun screenToMood(
    pos: Offset,
    w: Float,
    h: Float,
    pan: Offset,
    zoom: Float,
): Pair<Float, Float> {
    val cx = w / 2f
    val cy = h / 2f
    val worldX = cx + (pos.x - cx) / zoom + pan.x
    val worldY = cy + (pos.y - cy) / zoom + pan.y
    val v = (worldX / w).coerceIn(0f, 1f)
    val e = (1f - worldY / h).coerceIn(0f, 1f)
    return v to e
}

internal fun nearest(
    stars: List<TrackRow>,
    pos: Offset,
    w: Int,
    h: Int,
    maxPx: Float,
    pan: Offset = Offset.Zero,
    zoom: Float = 1f,
): TrackRow? {
    var best: TrackRow? = null
    var bestD = maxPx
    for (star in stars) {
        val p = moodToPx(star.valence, star.energy, w.toFloat(), h.toFloat(), pan, zoom)
        val d = hypot((p.x - pos.x).toDouble(), (p.y - pos.y).toDouble()).toFloat()
        if (d < bestD) {
            bestD = d
            best = star
        }
    }
    return best
}
