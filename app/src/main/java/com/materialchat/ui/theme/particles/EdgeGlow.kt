/*
 * Screen-edge light for the assistant: a soft accent glow along all four borders of the window
 * (the Gemini / Siri edge light), breathing and slowly flowing around the screen. Strongest
 * while listening, calmer while thinking or speaking, and faded out when the session leaves.
 *
 * Drawn by the full-window scrim as concentric strokes fading inward from the edge, so it looks
 * the same on API 26-36 without a RenderEffect. The strokes are inset by the display's own
 * rounded corners (RoundedCorner, API 31+) so the light hugs the panel instead of cutting its
 * corners. Frames are requested only while something moves: the glow is gated on the host
 * lifecycle being STARTED, power saver off, a non-zero animator duration scale and a presence
 * above zero, exactly like the neural particle field; a static glow (animations disabled) is
 * drawn once and requests nothing.
 */
package com.materialchat.ui.theme.particles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.view.RoundedCorner
import android.view.WindowInsets
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** How much edge light the assistant's current phase wants. */
enum class EdgeGlowPhase(val level: Float) {
    /** Not invoked, or leaving: the light fades out. */
    Hidden(0f),
    /** Invoked and waiting for the user: a gentle frame. */
    Idle(0.5f),
    /** Capturing speech: full light. */
    Listening(1f),
    /** Thinking, answering or speaking: calmer than listening. */
    Busy(0.65f),
}

/** Geometry and motion of the edge light. */
@Immutable
data class EdgeGlowStyle(
    /** Inward reach of the light from the window edge. The hosted gate keeps its control band
     *  more than this (plus a margin) away from every edge; keep scripts/check-assistant-window.sh
     *  in step when changing it. */
    val reach: Dp = 56.dp,
    /** Concentric strokes approximating the inward gradient. */
    val steps: Int = 24,
    /** Peak alpha at the edge while listening. */
    val opacity: Float = 0.6f,
    /** Breathing period; the light dims by [breathDepth] at the trough. */
    val breathPeriodMillis: Int = 2600,
    val breathDepth: Float = 0.22f,
    /** One full trip of the colour flow around the screen. */
    val flowPeriodMillis: Int = 7000,
    /** Time constant of the level change between phases (listening to busy and back). */
    val levelResponseSeconds: Float = 0.28f,
) {
    init {
        require(reach.value.isFinite() && reach.value >= 0f) { "reach must be >= 0" }
        require(steps >= 0) { "steps must be >= 0" }
        require(opacity.isFinite() && opacity in 0f..1f) { "opacity must be within 0..1" }
        require(breathPeriodMillis > 0) { "breathPeriodMillis must be > 0" }
        require(breathDepth.isFinite() && breathDepth in 0f..1f) { "breathDepth must be within 0..1" }
        require(flowPeriodMillis > 0) { "flowPeriodMillis must be > 0" }
        require(levelResponseSeconds.isFinite() && levelResponseSeconds > 0f) { "levelResponseSeconds must be > 0" }
    }
}

/** Breathing multiplier in `1 - depth .. 1`, starting at full light. */
internal fun edgeGlowBreath(timeSeconds: Float, periodMillis: Int, depth: Float): Float {
    val d = depth.coerceIn(0f, 1f)
    if (d == 0f || periodMillis <= 0) return 1f
    val phase = (timeSeconds * 1000f / periodMillis) * 2.0 * PI
    // cos-shaped: 1 at t=0, 1-depth half a period later.
    val wave = (1.0 - Math.cos(phase)) / 2.0
    return (1f - d * wave.toFloat()).coerceIn(0f, 1f)
}

/** Exponential approach of the drawn level towards the phase's level. */
internal fun edgeGlowLevel(current: Float, target: Float, deltaSeconds: Float, responseSeconds: Float): Float {
    if (deltaSeconds <= 0f || !deltaSeconds.isFinite()) return current
    val k = 1f - exp(-deltaSeconds / responseSeconds)
    val next = current + (target - current) * k
    // Snap the last hair so the loop can settle instead of chasing floating point.
    return if (kotlin.math.abs(next - target) < 0.002f) target else next
}

/** Frames are only needed while something visible can move. */
internal fun shouldRequestEdgeGlowFrames(
    visible: Boolean,
    presence: Float,
    motionEnabled: Boolean,
    animationScale: Float,
): Boolean = visible && presence > 0f && motionEnabled && animationScale > 0f

/**
 * Sweep-gradient stops that carry [palette] once around the screen, shifted by [flow] (0..1,
 * one full trip). The stops stay sorted and closed so the brush never shows a seam.
 */
internal fun edgeGlowStops(palette: List<Color>, flow: Float, segments: Int = 16): Array<Pair<Float, Color>> {
    val colors = palette.ifEmpty { listOf(Color.White) }
    val cycle = colors + colors.first()
    val span = cycle.size - 1
    return Array(segments + 1) { k ->
        val f = k.toFloat() / segments
        val u = ((f - flow) % 1f + 1f) % 1f
        val pos = u * span
        val i = pos.toInt().coerceIn(0, span - 1)
        f to lerp(cycle[i], cycle[i + 1], (pos - i).coerceIn(0f, 1f))
    }
}

/** The largest of the display's rounded corners, 0 where the platform reports none. */
internal fun displayCornerRadiusPx(insets: WindowInsets?): Float {
    if (Build.VERSION.SDK_INT < 31 || insets == null) return 0f
    val positions = intArrayOf(
        RoundedCorner.POSITION_TOP_LEFT, RoundedCorner.POSITION_TOP_RIGHT,
        RoundedCorner.POSITION_BOTTOM_LEFT, RoundedCorner.POSITION_BOTTOM_RIGHT
    )
    return positions.maxOf { runCatching { insets.getRoundedCorner(it)?.radius ?: 0 }.getOrDefault(0) }.toFloat()
}

/**
 * Draws the edge light inside this node's bounds, underneath its content. [phase] selects the
 * level; [colors] is the accent family that flows around the edge (the first entry dominates).
 * The light fades in on its first appearance (the window has just appeared) and out when the
 * phase turns [EdgeGlowPhase.Hidden].
 */
fun Modifier.screenEdgeGlow(
    phase: EdgeGlowPhase,
    colors: List<Color>,
    enabled: Boolean = true,
    style: EdgeGlowStyle = EdgeGlowStyle(),
    fadeSpec: ParticleFadeSpec = ParticleFadeSpec(fadeInMillis = 450L, fadeOutMillis = 320L),
): Modifier = composed {
    val context = LocalContext.current
    val view = LocalView.current
    val control = LocalNeuralParticleRenderControl.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var started by remember(view, lifecycle) { mutableStateOf(false) }
    var powerSave by remember(context) {
        mutableStateOf(context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true)
    }
    var animationScale by remember(context) { mutableStateOf(readAnimationScale(context)) }
    DisposableEffect(view, lifecycle) {
        fun update() {
            started = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        val observer = LifecycleEventObserver { _, _ -> update() }
        lifecycle.addObserver(observer)
        update()
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                powerSave = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
                animationScale = readAnimationScale(context)
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter().apply {
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                addAction("android.intent.action.ANIMATOR_DURATION_SCALE_CHANGED")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    val latestPalette by rememberUpdatedState(colors)
    val latestPhase by rememberUpdatedState(phase)
    val visible = enabled && started && !powerSave && style.opacity > 0f && style.steps > 0

    // --- Presence: fades in on first appearance, out when hidden ------------------------------
    val target = fadeTarget(active = phase != EdgeGlowPhase.Hidden, idleExpired = false)
    var presence by remember { mutableFloatStateOf(0f) }
    val fadeScale = if (control.motionEnabled) animationScale else 0f
    LaunchedEffect(target, visible, fadeSpec, fadeScale) {
        if (presence == target) return@LaunchedEffect
        if (!visible) {
            presence = target
            return@LaunchedEffect
        }
        val from = presence
        val start = withFrameNanos { it }
        control.onFrame?.invoke()
        while (presence != target) {
            withFrameNanos { now ->
                presence = fadePresence(from, target, (now - start) / 1_000_000L, fadeSpec, fadeScale)
                control.onFrame?.invoke()
            }
        }
    }

    // --- Motion: breathing, colour flow and the level change between phases -------------------
    var level by remember { mutableFloatStateOf(phase.level) }
    var elapsed by remember { mutableFloatStateOf(0f) }
    val animating = shouldRequestEdgeGlowFrames(visible, presence, control.motionEnabled, animationScale)
    LaunchedEffect(animating, phase, control.timeSeconds) {
        if (!animating || control.timeSeconds != null) {
            // Static: draw the phase's level once, request nothing.
            if (phase != EdgeGlowPhase.Hidden) level = phase.level
            return@LaunchedEffect
        }
        var previous = 0L
        while (true) {
            withFrameNanos { now ->
                if (previous != 0L) {
                    val dt = ((now - previous) / 1_000_000_000.0).toFloat()
                    elapsed += dt
                    val wanted = latestPhase.let { if (it == EdgeGlowPhase.Hidden) level else it.level }
                    level = edgeGlowLevel(level, wanted, dt, style.levelResponseSeconds)
                }
                previous = now
                control.onFrame?.invoke()
            }
        }
    }

    this.drawWithCache {
        val reachPx = style.reach.toPx()
        val rings = innerGlowRings(reachPx, style.steps, style.opacity)
        val cornerPx = displayCornerRadiusPx(if (Build.VERSION.SDK_INT >= 31) view.rootWindowInsets else null)
        val clip = Path().apply {
            addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(cornerPx, cornerPx)))
        }
        val center = Offset(size.width / 2f, size.height / 2f)
        onDrawWithContent {
            val strength = if (visible) presence * level else 0f
            if (strength > 0f && rings.isNotEmpty()) {
                val time = control.timeSeconds?.takeIf { it.isFinite() } ?: elapsed
                val breath = edgeGlowBreath(time, style.breathPeriodMillis, style.breathDepth)
                val flow = ((time * 1000f / style.flowPeriodMillis) % 1f + 1f) % 1f
                val brush = Brush.sweepGradient(*edgeGlowStops(latestPalette, flow), center = center)
                clipPath(clip) {
                    for (ring in rings) {
                        val alpha = (ring.alpha * strength * breath).coerceIn(0f, 1f)
                        if (alpha <= 0f) continue
                        val inset = -ring.outset // rings are inward: outset is negative
                        val w = size.width - 2f * inset
                        val h = size.height - 2f * inset
                        if (w <= 0f || h <= 0f) continue
                        val radius = (cornerPx - inset).coerceAtLeast(0f)
                        drawRoundRect(
                            brush = brush,
                            topLeft = Offset(inset, inset),
                            size = Size(w, h),
                            cornerRadius = CornerRadius(radius, radius),
                            alpha = alpha,
                            style = Stroke(width = ring.strokeWidth + 0.5f)
                        )
                    }
                }
            }
            drawContent()
        }
    }
}
