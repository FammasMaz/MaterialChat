/*
 * Ported from sameerasw/essentials (MIT) — island/plugins/media/particles/GeminiParticlePill.kt
 *
 * A Poisson-disk particle field around a capsule (rounded-rect signed distance), animated with
 * curl noise. The drawing node must fully contain the outer band: MaterialChat applies this to the
 * full-window scrim parent and passes the assistant card's rect, because below API 28 a node's
 * drawing is clipped to its own bounds (project memory #3904).
 */
package com.materialchat.ui.theme.particles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

internal const val NeuralParticleTag = "NeuralParticles"

/** Outset that must surround the capsule so the whole outer band is inside the drawing node. */
val NeuralParticleOutset: Dp = ParticleFieldStyle().outerBand + 8.dp

/**
 * Test/preview hooks. Production leaves this at its defaults; instrumentation tests pin the frame
 * time, observe requested frames, and read which painter was resolved.
 */
@Immutable
data class NeuralParticleRenderControl(
    val timeSeconds: Float? = null,
    val motionEnabled: Boolean = true,
    val onFrame: (() -> Unit)? = null,
    val onPainterResolved: ((NeuralParticlePainterKind) -> Unit)? = null,
    val onTileReady: (() -> Unit)? = null,
)

val LocalNeuralParticleRenderControl = staticCompositionLocalOf { NeuralParticleRenderControl() }

/**
 * Draws the particle field around [capsuleRect] (in this node's coordinate space) or, when null,
 * around a capsule inset by [capsuleInset] from this node's bounds.
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
    val animating = visible && painter.animated && control.motionEnabled && animationScale > 0f
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
            onDrawBehind {
                // No rect and no inset means the caller has nothing to decorate yet.
                if (capsuleRect == null && capsuleInset == null) return@onDrawBehind
                val capsule = CapsuleParticleGeometry.create(
                    size,
                    capsuleInset?.toPx() ?: 0f,
                    cornerRadius.toPx(),
                    capsuleRect
                )
                val alpha = if (visible) particleActivation(latestActivation()) * style.opacity else 0f
                val samples = tile
                if (capsule != null && samples != null && alpha > 0f) {
                    NeuralParticleDiagnostics.record(painter.kind, capsule)
                    with(painter) {
                        paint(
                            capsule,
                            control.timeSeconds?.takeIf { it.isFinite() } ?: elapsed,
                            alpha,
                            latestColor,
                            pixels,
                            screenOrigin,
                            samples
                        )
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
                forceFallback = forceFallback
            )
    )
}

private fun readAnimationScale(context: Context): Float = runCatching {
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
}.getOrDefault(1f)

/**
 * One log line per painter/capsule signature so the hosted gate can prove the real assistant
 * window resolved the expected painter (AGSL on API 33+, static below).
 */
internal object NeuralParticleDiagnostics {
    private var lastKind: NeuralParticlePainterKind? = null
    private var lastWidth = -1
    private var lastHeight = -1
    private var lastRadius = -1

    fun record(kind: NeuralParticlePainterKind, capsule: CapsuleParticleGeometry) {
        // Runs inside the draw path: compare primitives so the steady state allocates nothing.
        val width = capsule.rect.width.toInt()
        val height = capsule.rect.height.toInt()
        val radius = capsule.radius.toInt()
        if (kind == lastKind && width == lastWidth && height == lastHeight && radius == lastRadius) return
        lastKind = kind
        lastWidth = width
        lastHeight = height
        lastRadius = radius
        Log.i(
            NeuralParticleTag,
            "painter=${kind.name.lowercase()} capsule=${width}x$height radius=$radius " +
                "band=${capsule.viewport.width.toInt()}x${capsule.viewport.height.toInt()}"
        )
    }
}
