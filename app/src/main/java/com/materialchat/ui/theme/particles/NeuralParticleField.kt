/*
 * Ported from sameerasw/essentials (MIT) — island/plugins/media/particles/GeminiParticlePill.kt
 *
 * A Poisson-disk particle field around a capsule (rounded-rect signed distance), animated with
 * curl noise. The drawing node must fully contain the outer band: MaterialChat applies this to the
 * full-window scrim parent and passes the assistant card's rect, because below API 28 a node's
 * drawing is clipped to its own bounds (project memory #3904).
 *
 * MaterialChat additions over the reference:
 *  - a soft edge: a glow halo hugging the capsule plus a RenderEffect blur of the whole field on
 *    API 31+ (see CapsuleHalo.kt; a pure stroke-gradient halo below API 31);
 *  - presence: the field fades out when [active] turns false or after [idleTimeoutMillis], and
 *    stops requesting frames once fully faded (see ParticleFade.kt).
 */
package com.materialchat.ui.theme.particles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

internal const val NeuralParticleTag = "NeuralParticles"

/** Outset that must surround the capsule so the whole outer band is inside the drawing node. */
val NeuralParticleOutset: Dp = ParticleFieldStyle().outerBand + 8.dp

/**
 * Test/preview hooks. Production leaves this at its defaults; instrumentation tests pin the frame
 * time, observe requested frames, and read which painter and edge treatment were resolved.
 * [onFrame] fires for every frame the field requests, whether for particle motion or for a fade.
 */
@Immutable
data class NeuralParticleRenderControl(
    val timeSeconds: Float? = null,
    val motionEnabled: Boolean = true,
    val onFrame: (() -> Unit)? = null,
    val onPainterResolved: ((NeuralParticlePainterKind) -> Unit)? = null,
    val onTileReady: (() -> Unit)? = null,
    val onEdgeTreatmentResolved: ((ParticleEdgeTreatment) -> Unit)? = null,
)

val LocalNeuralParticleRenderControl = staticCompositionLocalOf { NeuralParticleRenderControl() }

/**
 * Draws the particle field around [capsuleRect] (in this node's coordinate space) or, when null,
 * around a capsule inset by [capsuleInset] from this node's bounds.
 *
 * Presence: the field is shown while [active] is true. When [active] turns false it fades out
 * over [ParticleFadeSpec.fadeOutMillis] and then requests no further frames; when it turns true
 * again it fades back in. With a positive [idleTimeoutMillis] the field also fades out that long
 * after it last became active and visible, even if [active] is still true; it reappears the next
 * time [active] (or the field's visibility) flips back to true. The first composition snaps to
 * the current target so the field never fades in on its initial appearance.
 *
 * Intended assistant wiring: `active = voiceState is Listening/Thinking/Speaking || streaming`,
 * with `idleTimeoutMillis` of a few seconds so the glow retires shortly after a response ends.
 *
 * Edge: [edgeStyle] adds a glow halo hugging the capsule and, on API 31+ with a hardware canvas,
 * blurs the recorded field (halo + particles) with a RenderEffect. The blur never touches the
 * card content: only this node's own background drawing is recorded into the blurred layer.
 */
fun Modifier.neuralParticleField(
    capsuleRect: Rect? = null,
    cornerRadius: Dp = 24.dp,
    color: Color = Color.White,
    enabled: Boolean = true,
    activation: () -> Float = { 1f },
    capsuleInset: Dp? = 0.dp,
    style: ParticleFieldStyle = ParticleFieldStyle(),
    forceFallback: Boolean = false,
    active: Boolean = true,
    idleTimeoutMillis: Long? = null,
    fadeSpec: ParticleFadeSpec = ParticleFadeSpec(),
    edgeStyle: ParticleEdgeStyle = ParticleEdgeStyle(),
): Modifier = composed {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val control = LocalNeuralParticleRenderControl.current
    val pixels = remember(style, density) { style.resolve(density) }
    val tile by produceState<PoissonTile?>(null, pixels.minDistance) {
        value = null
        value = withContext(Dispatchers.Default) {
            val running = currentCoroutineContext()
            generatePoissonTile(pixels.minDistance, checkCancelled = { running.ensureActive() })
        }
    }
    val latestActivation by rememberUpdatedState(activation)
    val latestColor by rememberUpdatedState(color)
    // The composition's lifecycle owner, not the view tree's: it defaults to the window owner
    // but honours an overriding provider, so a host that stops its content stops the frame loop.
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
    val painter = remember(forceFallback) { createParticlePainter(forceFallback) }
    DisposableEffect(painter) {
        control.onPainterResolved?.invoke(painter.kind)
        onDispose { }
    }
    DisposableEffect(tile) {
        if (tile != null) control.onTileReady?.invoke()
        onDispose { }
    }
    var hasArea by remember { mutableStateOf(false) }
    var elapsed by remember { mutableFloatStateOf(0f) }
    var screenOrigin by remember { mutableStateOf(Offset.Zero) }
    val visible = enabled && started && !powerSave && style.opacity > 0f

    // --- Presence (fade in/out and idle retirement) -------------------------------------------
    val idleTimeout = effectiveIdleTimeout(idleTimeoutMillis)
    var idleExpired by remember { mutableStateOf(false) }
    LaunchedEffect(active, visible, idleTimeout) {
        // Becoming active (or visible) again restarts the idle countdown.
        idleExpired = false
        if (idleTimeout == null || !active || !visible) return@LaunchedEffect
        delay(idleTimeout)
        idleExpired = true
    }
    val target = fadeTarget(active, idleExpired)
    // Snap on first composition: the field must not fade in on its initial appearance.
    var presence by remember { mutableFloatStateOf(target) }
    val fadeScale = if (control.motionEnabled) animationScale else 0f
    LaunchedEffect(target, visible, fadeSpec, fadeScale) {
        if (presence == target) return@LaunchedEffect
        if (!visible) {
            // Nothing is drawn while hidden, so a fade would only burn frames: settle silently.
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

    // --- Particle motion ---------------------------------------------------------------------
    val animating = shouldRequestParticleFrames(
        visible = visible,
        presence = presence,
        painterAnimated = painter.animated,
        motionEnabled = control.motionEnabled,
        animationScale = animationScale
    )
    LaunchedEffect(animating, hasArea, tile, control.timeSeconds) {
        if (!animating || !hasArea || tile == null || control.timeSeconds != null) return@LaunchedEffect
        snapshotFlow { particleActivation(latestActivation()) > 0f }.collectLatest { active ->
            if (active) {
                var previous = 0L
                while (true) {
                    withFrameNanos { now ->
                        if (previous != 0L) elapsed += ((now - previous) / 1_000_000_000.0).toFloat()
                        previous = now
                        control.onFrame?.invoke()
                    }
                }
            }
        }
    }
    this
        .onSizeChanged { hasArea = it.width > 0 && it.height > 0 }
        .onGloballyPositioned {
            val origin = it.positionOnScreen()
            if (origin.x.isFinite() && origin.y.isFinite()) screenOrigin = origin
        }
        .drawWithCache {
            val blurRadiusPx = edgeStyle.blurRadius.toPx()
            // Resolved here rather than in composition: the view is attached by the time it draws,
            // so isHardwareAccelerated reports the real canvas.
            val edgeTreatment = resolveParticleEdgeTreatment(
                Build.VERSION.SDK_INT, blurRadiusPx, view.isHardwareAccelerated
            )
            control.onEdgeTreatmentResolved?.invoke(edgeTreatment)
            val rings = haloRings(edgeStyle.haloWidth.toPx(), edgeStyle.haloSteps, edgeStyle.haloOpacity)
            val blurLayer = if (edgeTreatment == ParticleEdgeTreatment.Blur) {
                obtainGraphicsLayer().also {
                    // Decal: the field fades into transparency at the layer edge instead of
                    // smearing the last row of pixels outward.
                    it.renderEffect = BlurEffect(blurRadiusPx, blurRadiusPx, TileMode.Decal)
                }
            } else null
            onDrawBehind {
                // No rect and no inset means the caller has nothing to decorate yet.
                if (capsuleRect == null && capsuleInset == null) return@onDrawBehind
                val capsule = CapsuleParticleGeometry.create(
                    size,
                    capsuleInset?.toPx() ?: 0f,
                    cornerRadius.toPx(),
                    capsuleRect
                )
                val strength = if (visible) particleActivation(latestActivation()) * presence else 0f
                val alpha = strength * style.opacity
                val samples = tile
                if (capsule != null && samples != null && strength > 0f) {
                    NeuralParticleDiagnostics.record(painter.kind, edgeTreatment, capsule)
                    val time = control.timeSeconds?.takeIf { it.isFinite() } ?: elapsed
                    val drawField: DrawScope.() -> Unit = {
                        drawCapsuleHalo(capsule, latestColor, strength, rings)
                        if (alpha > 0f) {
                            with(painter) {
                                paint(capsule, time, alpha, latestColor, pixels, screenOrigin, samples)
                            }
                        }
                    }
                    if (blurLayer != null) {
                        // Only this node's background is recorded: the card content drawn on
                        // top is never part of the blurred layer.
                        blurLayer.record { drawField() }
                        drawLayer(blurLayer)
                    } else {
                        drawField()
                    }
                }
            }
        }
}

/**
 * Standalone form: the node is padded by [NeuralParticleOutset] so the band around its own
 * bounds fits inside the node (mirrors essentials' MediaParticleOutset padding).
 */
@Composable
fun NeuralParticleField(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    color: Color = Color.White,
    enabled: Boolean = true,
    activation: () -> Float = { 1f },
    style: ParticleFieldStyle = ParticleFieldStyle(),
    forceFallback: Boolean = false,
    active: Boolean = true,
    idleTimeoutMillis: Long? = null,
    fadeSpec: ParticleFadeSpec = ParticleFadeSpec(),
    edgeStyle: ParticleEdgeStyle = ParticleEdgeStyle(),
) {
    Box(
        modifier
            .padding(NeuralParticleOutset)
            .neuralParticleField(
                cornerRadius = cornerRadius,
                color = color,
                enabled = enabled,
                activation = activation,
                capsuleInset = 0.dp,
                style = style,
                forceFallback = forceFallback,
                active = active,
                idleTimeoutMillis = idleTimeoutMillis,
                fadeSpec = fadeSpec,
                edgeStyle = edgeStyle
            )
    )
}

private fun readAnimationScale(context: Context): Float = runCatching {
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
}.getOrDefault(1f)

/**
 * One log line per painter/edge/capsule signature so the hosted gate can prove the real assistant
 * window resolved the expected painter (AGSL on API 33+, static below) and edge treatment
 * (blur on API 31+, gradient below).
 */
internal object NeuralParticleDiagnostics {
    private var lastKind: NeuralParticlePainterKind? = null
    private var lastEdge: ParticleEdgeTreatment? = null
    private var lastWidth = -1
    private var lastHeight = -1
    private var lastRadius = -1

    fun record(kind: NeuralParticlePainterKind, edge: ParticleEdgeTreatment, capsule: CapsuleParticleGeometry) {
        // Runs inside the draw path: compare primitives so the steady state allocates nothing.
        val width = capsule.rect.width.toInt()
        val height = capsule.rect.height.toInt()
        val radius = capsule.radius.toInt()
        if (kind == lastKind && edge == lastEdge && width == lastWidth && height == lastHeight && radius == lastRadius) return
        lastKind = kind
        lastEdge = edge
        lastWidth = width
        lastHeight = height
        lastRadius = radius
        Log.i(
            NeuralParticleTag,
            "painter=${kind.name.lowercase()} capsule=${width}x$height radius=$radius " +
                "band=${capsule.viewport.width.toInt()}x${capsule.viewport.height.toInt()} " +
                "edge=${edge.name.lowercase()}"
        )
    }
}
