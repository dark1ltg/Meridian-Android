package io.github.dark1ltg.meridian.ui

import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.dark1ltg.meridian.data.EngineSnapshot
import io.github.dark1ltg.meridian.data.TrackRow
import io.github.dark1ltg.meridian.ui.theme.LowTrust
import io.github.dark1ltg.meridian.ui.theme.quadrantColor
import kotlin.math.hypot
import kotlin.math.min
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Live lens aim. Mutable fields are Compose state so the ring and HUD can
 * move at pointer rate without rebuilding [EngineSnapshot] or the starfield.
 *
 * Marked [Stable] so [MoodMap] can skip composition while only [x]/[y]/[radius]
 * change (Titan 2 class: 1440×1440 @ 120 Hz, ~8–16 ms/frame).
 */
@Stable
class LensAim(
    x: Float = 0.5f,
    y: Float = 0.5f,
    radius: Float = 0.22f,
) {
    var x by mutableFloatStateOf(x.coerceIn(0f, 1f))
        internal set
    var y by mutableFloatStateOf(y.coerceIn(0f, 1f))
        internal set
    var radius by mutableFloatStateOf(radius.coerceIn(0.08f, 0.48f))
        internal set
    var dragging by mutableStateOf(false)
        internal set

    fun syncFrom(lx: Float, ly: Float, r: Float) {
        if (dragging) return
        x = lx.coerceIn(0f, 1f)
        y = ly.coerceIn(0f, 1f)
        radius = r.coerceIn(0.08f, 0.48f)
    }

    fun moveTo(lx: Float, ly: Float) {
        x = lx.coerceIn(0f, 1f)
        y = ly.coerceIn(0f, 1f)
    }

    fun resizeBy(factor: Float) {
        radius = (radius * factor).coerceIn(0.08f, 0.48f)
    }
}

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
    lensAim: LensAim? = null,
) {
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val minDimPx = with(density) {
        minOf(config.screenWidthDp.dp.toPx(), config.screenHeightDp.dp.toPx())
    }
    val fieldCount = MapLod.galaxyBudget(compact, snapshot.stars.size, minDimPx)
    val field = remember(fieldCount) { galaxyField(seed = 42, count = fieldCount) }
    val aim = lensAim ?: remember { LensAim(snapshot.lensX, snapshot.lensY, snapshot.lensRadius) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var zoom by remember { mutableFloatStateOf(previewZoom.coerceIn(1f, 3.2f)) }
    var hover by remember { mutableStateOf<Pair<Offset, String>?>(null) }
    val latestOnLens = rememberUpdatedState(onLens)
    val latestOnPlay = rememberUpdatedState(onPlay)
    val latestOnPin = rememberUpdatedState(onPin)
    val latestOnHover = rememberUpdatedState(onHover)
    val latestStars = rememberUpdatedState(snapshot.stars)
    val scope = rememberCoroutineScope()
    val settleJob = remember { mutableStateOf<Job?>(null) }
    val labelPaint = remember {
        AndroidPaint().apply {
            color = android.graphics.Color.argb(210, 246, 240, 232)
            textSize = 26f
            isAntiAlias = true
            isFakeBoldText = true
        }
    }
    val shadePath = remember { Path() }
    LaunchedEffect(snapshot.lensX, snapshot.lensY, snapshot.lensRadius) {
        aim.syncFrom(snapshot.lensX, snapshot.lensY, snapshot.lensRadius)
    }
    fun commitLens() {
        settleJob.value?.cancel()
        settleJob.value = null
        latestOnLens.value(aim.x, aim.y, aim.radius)
    }
    fun scheduleLensCommit() {
        settleJob.value?.cancel()
        settleJob.value = scope.launch {
            delay(MapLod.LENS_COMMIT_IDLE_MS)
            latestOnLens.value(aim.x, aim.y, aim.radius)
        }
    }
    Box(
        modifier
            .fillMaxSize()
            .pointerInput(compact) {
                detectTapGestures { pos ->
                    nearest(
                        latestStars.value,
                        pos,
                        size.width,
                        size.height,
                        44f,
                        pan,
                        zoom,
                    )?.let { latestOnPlay.value(it.id) }
                }
            }
            .pointerInput(compact) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val stars = latestStars.value
                    var localPan = pan
                    var localZoom = zoom
                    val star = nearest(stars, down.position, size.width, size.height, 44f, localPan, localZoom)
                    if (star != null) {
                        val sp = moodToPx(star.valence, star.energy, w, h, localPan, localZoom)
                        hover = sp to "${star.title} · ${star.artist}"
                        latestOnHover.value(hover!!.second)
                    }
                    val lensPx = moodToPx(aim.x, aim.y, w, h, localPan, localZoom)
                    val rr = aim.radius.coerceIn(0.08f, 0.48f) * min(w, h) * localZoom
                    val nearLens = hypot(
                        (down.position.x - lensPx.x).toDouble(),
                        (down.position.y - lensPx.y).toDouble(),
                    ) <= rr * 1.12
                    var pinId: Int? = null
                    var lastPin = screenToMood(down.position, w, h, localPan, localZoom)
                    var moved = false
                    var travel = 0f
                    var aimed = false
                    val pinSlop = 18f
                    try {
                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = true)
                            val liveLens = moodToPx(aim.x, aim.y, w, h, localPan, localZoom)
                            val liveRr = aim.radius.coerceIn(0.08f, 0.48f) * min(w, h) * localZoom
                            if (pressed.size >= 2 && zoomChange != 1f) {
                                pinId = null
                                val inside = hypot(
                                    (centroid.x - liveLens.x).toDouble(),
                                    (centroid.y - liveLens.y).toDouble(),
                                ) <= liveRr * 1.08
                                if (inside || compact) {
                                    aim.dragging = true
                                    aim.resizeBy(zoomChange)
                                    aimed = true
                                    scheduleLensCommit()
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
                                if (shouldHitTestStars(aimingLens = nearLens || compact || aimed, pinning = pinId != null)) {
                                    val tip = nearest(stars, p, size.width, size.height, 56f, localPan, localZoom)
                                    if (tip != null && pinId == null) {
                                        val sp = moodToPx(tip.valence, tip.energy, w, h, localPan, localZoom)
                                        hover = sp to "${tip.title} · ${tip.artist}"
                                        latestOnHover.value(hover!!.second)
                                    }
                                }
                                if (pinId != null) {
                                    lastPin = screenToMood(p, w, h, localPan, localZoom)
                                } else if (nearLens || compact) {
                                    val mood = screenToMood(p, w, h, localPan, localZoom)
                                    aim.dragging = true
                                    aim.moveTo(mood.first, mood.second)
                                    aimed = true
                                    scheduleLensCommit()
                                } else {
                                    localPan = Offset(localPan.x - panChange.x / localZoom, localPan.y - panChange.y / localZoom)
                                    pan = localPan
                                }
                                pressed.forEach { if (it.positionChanged()) it.consume() }
                                moved = true
                            }
                        } while (event.changes.any { it.pressed })
                    } finally {
                        if (aimed) commitLens()
                        aim.dragging = false
                    }
                    if (pinId != null && moved) {
                        latestOnPin.value(pinId, lastPin.first, lastPin.second)
                    }
                }
            },
    ) {
        StarFieldLayer(
            stars = snapshot.stars,
            currentId = snapshot.current?.id,
            field = field,
            pan = pan,
            zoom = zoom,
            hover = hover,
            showSky = showSky,
            compact = compact,
            labelPaint = labelPaint,
        )
        LensChromeLayer(
            aim = aim,
            pan = pan,
            zoom = zoom,
            shadePath = shadePath,
        )
    }
}

@Composable
private fun StarFieldLayer(
    stars: List<TrackRow>,
    currentId: Int?,
    field: List<FieldStar>,
    pan: Offset,
    zoom: Float,
    hover: Pair<Offset, String>?,
    showSky: Boolean,
    compact: Boolean,
    labelPaint: AndroidPaint,
) {
    Canvas(Modifier.fillMaxSize()) {
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
        val labelCandidates = ArrayList<Pair<Offset, TrackRow>>(32)
        stars.forEach { star ->
            val p = moodToPx(star.valence, star.energy, w, h, pan, zoom)
            if (!MapLod.onScreen(p.x, p.y, w, h)) return@forEach
            val color = if (star.lowTrust || star.confidence < 0.45f) {
                LowTrust
            } else {
                quadrantColor(star.quadrant)
            }
            val selected = currentId == star.id
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
                drawStarLabel(p, star.title, labelPaint)
            }
        }
        hover?.let { (p, text) ->
            if (!compact) drawStarLabel(Offset(p.x, p.y - 18f), text, labelPaint)
        }
    }
}

@Composable
private fun LensChromeLayer(
    aim: LensAim,
    pan: Offset,
    zoom: Float,
    shadePath: Path,
) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val lens = moodToPx(aim.x, aim.y, w, h, pan, zoom)
        val rr = aim.radius.coerceIn(0.12f, 0.48f) * min(w, h) * zoom
        val chrome = MapLod.chromeAlpha(zoom)
        shadePath.rewind()
        shadePath.fillType = PathFillType.EvenOdd
        shadePath.addRect(Rect(0f, 0f, w, h))
        shadePath.addOval(Rect(lens.x - rr, lens.y - rr, lens.x + rr, lens.y + rr))
        drawPath(shadePath, Color(0x99060812).copy(alpha = 0.6f * chrome))
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

private fun DrawScope.drawStarLabel(p: Offset, title: String, paint: AndroidPaint) {
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

internal fun shouldHitTestStars(aimingLens: Boolean, pinning: Boolean): Boolean {
    return !aimingLens && !pinning
}
