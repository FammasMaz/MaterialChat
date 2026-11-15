package com.materialchat.assistant.ui

import android.graphics.Paint
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.materialchat.ui.theme.particles.CapsuleParticleGeometry
import com.materialchat.ui.theme.particles.LocalNeuralParticleRenderControl
import com.materialchat.ui.theme.particles.rememberParticleMotionGate
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The assistant's dot texture, after Gemini Live's bottom band: a bottom-anchored vertical scrim
 * behind the pill, and a uniform dot lattice (small white dots on a regular grid) drawn IN FRONT
 * of the pill, over its blurred edge, fading upward to nothing at the band's top edge.
 *
 * Geometry read from the reference frames: ~1dp dots on a ~7dp pitch, the band roughly the
 * bottom quarter of the screen, the scrim reaching about 55% black at the very bottom. The
 * grid's positions depend only on the node size; the dots never translate. What moves is their
 * brightness: each dot twinkles in place on its own 3.5-7.5 s cycle ([LatticeTwinkle], with the
 * measurements), liveliest just outside the pill and absent inside its opaque interior.
 *
 * The twinkle runs on one frame clock read only from the draw scope, so it invalidates the draw
 * phase and never recomposes per frame, and it stops entirely behind the shared
 * [rememberParticleMotionGate] (window not STARTED, power saver, animator scale 0) or when the
 * texture is leaving.
 */
internal object AssistantLatticeStyle {
    /** Dot diameter. The reference dots are about 1dp; 1.2dp survives downsampling on dense screens. */
    val DotDiameter: Dp = 1.2.dp

    /** Grid pitch, both axes. */
    val Pitch: Dp = 7.dp

    /** The band is this fraction of the drawing node's height, measured from its bottom edge. */
    const val BAND_FRACTION = 0.24f

    /** The band never collapses below this on a short host (keyboard up). */
    val MinBandHeight: Dp = 160.dp

    /** A fully lit dot at the bottom of the band next to the pill, white (reference on level ~210/255). */
    const val DOT_ALPHA = 0.85f

    /** An unlit dot's share of [DOT_ALPHA] (reference off level ~7/255). */
    const val DOT_FLOOR = 0.04f

    /** Dots this far inside the pill's edge are gone: that is its opaque interior. */
    val PillInnerBand: Dp = 20.dp

    /** Outside the pill the twinkle eases from full strength to [FAR_LEVEL] over this distance. */
    val PillNearBand: Dp = 24.dp

    /** Twinkle strength far from the pill (and everywhere when no pill rect is known). */
    const val FAR_LEVEL = 0.6f

    /** Dot alpha is quantised to this many levels so each frame is a handful of batched point draws. */
    const val ALPHA_LEVELS = 16

    /**
     * The band's own scrim at the bottom edge. It is added on top of the window's resting
     * 42% scrim, so the compound at the very bottom lands near the reference's ~55%.
     */
    const val SCRIM_ALPHA = 0.26f

    /** Fade in on arrival, aligned with the particle field's fade-in. */
    const val FADE_IN_MILLIS = 600

    /** Fade out while leaving; must settle inside the session's exit window ([AssistantExitMotion]). */
    const val FADE_OUT_MILLIS = 320

    /** The motion marker is logged once this many clock-driven draws have landed. */
    const val MOTION_MARKER_FRAMES = 60
}

/** One row of the lattice: its y, its alpha factor (0 at the band top, 1 at the bottom), its dots. */
internal class LatticeRow(val y: Float, val alpha: Float, val points: List<Offset>)

/** The resolved lattice for one node size; pure geometry, no state. */
internal class LatticeGeometry(val bandTop: Float, val rows: List<LatticeRow>) {
    val dotCount: Int = rows.sumOf { it.points.size }

    /** Every dot's x,y interleaved, bottom row first, left to right. */
    val xy: FloatArray = FloatArray(dotCount * 2)

    /** Every dot's band alpha factor, parallel to [xy]. */
    val bandAlpha: FloatArray = FloatArray(dotCount)

    init {
        var i = 0
        for (row in rows) {
            for (point in row.points) {
                xy[2 * i] = point.x
                xy[2 * i + 1] = point.y
                bandAlpha[i] = row.alpha
                i++
            }
        }
    }

    companion object {
        fun layout(
            size: Size,
            pitchPx: Float,
            bandFraction: Float = AssistantLatticeStyle.BAND_FRACTION,
            minBandPx: Float = 0f,
        ): LatticeGeometry {
            if (size.width <= 0f || size.height <= 0f || pitchPx <= 0f) return LatticeGeometry(size.height, emptyList())
            val bandHeight = maxOf(size.height * bandFraction, minBandPx).coerceAtMost(size.height)
            val bandTop = size.height - bandHeight
            // Columns centred so the lattice is symmetric edge to edge.
            val columns = floor(size.width / pitchPx).toInt() + 1
            val startX = (size.width - (columns - 1) * pitchPx) / 2f
            val xs = FloatArray(columns) { startX + it * pitchPx }
            // Rows anchored at the bottom, climbing by one pitch until the band's top edge.
            val rows = ArrayList<LatticeRow>()
            var y = size.height - pitchPx / 2f
            while (y >= bandTop) {
                val t = ((y - bandTop) / bandHeight).coerceIn(0f, 1f)
                // Ease toward the top so the lattice dissolves with the scrim rather than cutting off.
                val alpha = t.pow(1.6f)
                rows += LatticeRow(y, alpha, xs.map { Offset(it, y) })
                y -= pitchPx
            }
            return LatticeGeometry(bandTop, rows)
        }
    }
}

/**
 * Proves, from the draw path, that the clock actually drives the lattice: one marker once
 * [AssistantLatticeStyle.MOTION_MARKER_FRAMES] draws have landed with a different time each.
 * A fade alone redraws without advancing the time, so it never counts.
 */
internal class LatticeMotionDiagnostics {
    private var lastTime = Float.NaN
    private var motionDraws = 0
    private var logged = false

    fun onDrawn(time: Float, lit: Int, reachable: Int) {
        if (logged) return
        if (!lastTime.isNaN() && time != lastTime) motionDraws++
        lastTime = time
        if (motionDraws >= AssistantLatticeStyle.MOTION_MARKER_FRAMES) {
            logged = true
            val fraction = if (reachable > 0) lit.toFloat() / reachable else 0f
            Log.i("NeuralLattice", "motion=on frames=$motionDraws lit=${"%.2f".format(fraction)}")
        }
    }
}

/**
 * Draws the bottom scrim band behind this node's content and the twinkling dot lattice in front
 * of it, from this node, so the texture spans the whole node regardless of the pill's bounds
 * (below API 28 a node's drawing is clipped to its own bounds, so the full-window host must own
 * the band). [pillRect], in this node's coordinates, is the pill the dots float over: they are
 * gone inside its opaque interior, cross its blurred edge, and are liveliest just outside it.
 *
 * [visible] drives a bounded presence fade: the texture starts absent and fades in on arrival
 * and fades out while leaving. The twinkle clock runs only while the texture is wanted and the
 * host gate allows motion; it stops completely otherwise. [enabled] removes the texture entirely
 * (it follows the neural-expressive preference like the particles).
 */
internal fun Modifier.assistantDotLattice(
    visible: Boolean,
    enabled: Boolean = true,
    dotColor: Color = Color.White,
    pillRect: Rect? = null,
    pillCornerRadius: Dp = 38.dp,
): Modifier = composed {
    val control = LocalNeuralParticleRenderControl.current
    // Shared with the particle field: lifecycle STARTED, power saver, animator duration scale.
    val gate = rememberParticleMotionGate()
    val presence = remember { Animatable(0f) }
    val loggedSignatures = remember { mutableSetOf<String>() }
    val diagnostics = remember { LatticeMotionDiagnostics() }
    val target = if (visible && enabled) 1f else 0f
    val fadeScale = if (control.motionEnabled) gate.animationScale else 0f
    LaunchedEffect(target, fadeScale) {
        val baseMillis = if (target > 0f) AssistantLatticeStyle.FADE_IN_MILLIS else AssistantLatticeStyle.FADE_OUT_MILLIS
        val millis = if (fadeScale.isFinite() && fadeScale > 0f) {
            (baseMillis * fadeScale.coerceAtMost(10f)).toInt()
        } else 0
        // A bounded animation: it completes and then nothing here touches the frame clock again.
        if (millis > 0) presence.animateTo(target, tween(durationMillis = millis, easing = FastOutSlowInEasing))
        else presence.snapTo(target)
    }

    // --- Twinkle clock ------------------------------------------------------------------------
    // Read only from the draw scope below, so each tick invalidates drawing and nothing else.
    var elapsed by remember { mutableFloatStateOf(0f) }
    val animating = target > 0f && gate.motionAllowed && control.motionEnabled && control.timeSeconds == null
    LaunchedEffect(animating) {
        if (!animating) return@LaunchedEffect
        var previous = 0L
        while (true) {
            withFrameNanos { now ->
                if (previous != 0L) elapsed += ((now - previous) / 1_000_000_000.0).toFloat()
                previous = now
                control.onFrame?.invoke()
            }
        }
    }

    drawWithCache {
        val pitchPx = AssistantLatticeStyle.Pitch.toPx()
        val strokePx = AssistantLatticeStyle.DotDiameter.toPx()
        val geometry = LatticeGeometry.layout(
            size = size,
            pitchPx = pitchPx,
            minBandPx = AssistantLatticeStyle.MinBandHeight.toPx()
        )
        val count = geometry.dotCount
        // One line per lattice signature, from inside the draw path: the hosted gate uses it to
        // prove the real assistant window laid the lattice out and drew it.
        if (loggedSignatures.add("${count}x${pitchPx.toInt()}x${geometry.bandTop.toInt()}")) {
            Log.d(
                "NeuralLattice",
                "dots=$count pitch=${pitchPx.toInt()}px bandTop=${geometry.bandTop.toInt()}px " +
                    "height=${size.height.toInt()}px"
            )
        }
        val bandTop = geometry.bandTop
        val scrim = Brush.verticalGradient(
            0f to Color.Transparent,
            1f to Color.Black.copy(alpha = AssistantLatticeStyle.SCRIM_ALPHA),
            startY = bandTop,
            endY = size.height
        )
        // Per-dot constants, resolved once per node size / pill rect: cycle, offset and how much
        // of the twinkle the dot's position relative to the pill allows.
        val capsule = pillRect?.let { CapsuleParticleGeometry.create(size, 0f, pillCornerRadius.toPx(), it) }
        val innerPx = AssistantLatticeStyle.PillInnerBand.toPx()
        val nearPx = AssistantLatticeStyle.PillNearBand.toPx()
        val periods = FloatArray(count) { LatticeTwinkle.period(it) }
        val phases = FloatArray(count) { LatticeTwinkle.phase(it) }
        val reach = FloatArray(count) { i ->
            if (capsule == null) AssistantLatticeStyle.FAR_LEVEL
            else LatticeTwinkle.pillFactor(
                capsule.signedDistance(Offset(geometry.xy[2 * i], geometry.xy[2 * i + 1])),
                innerPx, nearPx, AssistantLatticeStyle.FAR_LEVEL
            )
        }
        val reachable = reach.count { it > 0f }
        val levels = AssistantLatticeStyle.ALPHA_LEVELS
        val paints = Array(levels) { level ->
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeWidth = strokePx
                color = dotColor.toArgb()
                alpha = (255f * level / (levels - 1)).roundToInt()
            }
        }
        val buckets = IntArray(count)
        val scratch = FloatArray(count * 2)
        onDrawWithContent {
            val p = presence.value
            if (p > 0f && count > 0) {
                drawRect(
                    brush = scrim,
                    topLeft = Offset(0f, bandTop),
                    size = Size(size.width, size.height - bandTop),
                    alpha = p
                )
            }
            // The pill (its soft fill, edge blur, glow and content) draws here; the dots follow,
            // in front of it and over its blurred edge, as in the reference.
            drawContent()
            if (p <= 0f || count == 0) return@onDrawWithContent
            val time = control.timeSeconds?.takeIf { it.isFinite() } ?: elapsed
            var lit = 0
            for (i in 0 until count) {
                val level = LatticeTwinkle.level(periods[i], phases[i], time)
                val alpha = p * geometry.bandAlpha[i] * reach[i] * AssistantLatticeStyle.DOT_ALPHA *
                    (AssistantLatticeStyle.DOT_FLOOR + (1f - AssistantLatticeStyle.DOT_FLOOR) * level)
                buckets[i] = (alpha * (levels - 1)).roundToInt().coerceIn(0, levels - 1)
                if (level >= 0.5f && reach[i] > 0f) lit++
            }
            // One batched point draw per alpha level; the steady state allocates nothing.
            val canvas = drawContext.canvas.nativeCanvas
            for (b in 1 until levels) {
                var n = 0
                for (i in 0 until count) {
                    if (buckets[i] != b) continue
                    scratch[n++] = geometry.xy[2 * i]
                    scratch[n++] = geometry.xy[2 * i + 1]
                }
                if (n > 0) canvas.drawPoints(scratch, 0, n, paints[b])
            }
            diagnostics.onDrawn(time, lit, reachable)
        }
    }
}
