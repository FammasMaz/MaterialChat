package com.materialchat.ui.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Whether the neural-expressive glow is drawn around floating surfaces. */
val LocalNeuralExpressiveEnabled = staticCompositionLocalOf { true }

/** Fixed time and motion control for repeatable captures of the production decoration. */
internal data class NeuralExpressiveRenderControl(
    val timeSeconds: Float? = null,
    val motionEnabled: Boolean = true,
    val onFrame: ((Float) -> Unit)? = null
)

internal val LocalNeuralExpressiveRenderControl = staticCompositionLocalOf {
    NeuralExpressiveRenderControl()
}

/**
 * An outward soft edge and a scattered particle band. Apply to an UNCLIPPED parent of a Surface,
 * not inside its opaque fill or alpha/offscreen layer. Drawing has no effect on measured bounds.
 *
 * On older API levels a layout node's drawing is clipped to its own bounds, so a decoration that
 * must overflow a card must be drawn by a LARGER node: apply [neuralExpressiveGlowAround] to a
 * full-window parent with the card's rect in that node's coordinate space.
 *
 * Linear edge gradients and radial corner gradients work on hardware canvases back to API 26;
 * no BlurMaskFilter, RenderEffect, software layer, or newer layer-outset API is needed.
 * The Essentials reference informed the outward band/falloff, not the implementation.
 */
@Composable
fun Modifier.neuralExpressiveGlow(
    enabled: Boolean = LocalNeuralExpressiveEnabled.current,
    cornerRadius: Dp = 32.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    dotCount: Int = 96,
    visible: Boolean = true
): Modifier = neuralExpressiveGlowAround(null, enabled, cornerRadius, color, dotCount, visible)

@Composable
fun Modifier.neuralExpressiveGlowAround(
    targetRect: Rect?,
    enabled: Boolean = LocalNeuralExpressiveEnabled.current,
    cornerRadius: Dp = 32.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    dotCount: Int = 96,
    visible: Boolean = true
): Modifier {
    if (!enabled || dotCount <= 0 || !visible) return this

    val context = LocalContext.current.applicationContext
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val control = LocalNeuralExpressiveRenderControl.current
    val durationScale = rememberCoroutineScope().coroutineContext[MotionDurationScale]
    val powerManager = remember(context) { context.getSystemService(PowerManager::class.java) }
    var started by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    var viewVisible by remember(view) { mutableStateOf(view.isDecorationVisible()) }
    var powerSave by remember(powerManager) { mutableStateOf(powerManager?.isPowerSaveMode == true) }
    var interactive by remember(powerManager) { mutableStateOf(powerManager?.isInteractive != false) }
    var systemMotionScale by remember(context) { mutableFloatStateOf(context.animatorScale()) }

    DisposableEffect(lifecycle, view, context) {
        fun updateVisibility() {
            started = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            viewVisible = view.isDecorationVisible()
        }
        val observer = LifecycleEventObserver { _, _ -> updateVisibility() }
        val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { updateVisibility() }
        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = updateVisibility()
            override fun onViewDetachedFromWindow(v: View) { viewVisible = false }
        }
        val treeObserver = view.viewTreeObserver
        lifecycle.addObserver(observer)
        treeObserver.addOnGlobalLayoutListener(layoutListener)
        view.addOnAttachStateChangeListener(attachListener)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                powerSave = powerManager?.isPowerSaveMode == true
                interactive = powerManager?.isInteractive != false
                updateVisibility()
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        val scaleObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                systemMotionScale = context.animatorScale()
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, scaleObserver
        )
        updateVisibility() // Lifecycle observers do not replay an already-started owner's events.
        onDispose {
            lifecycle.removeObserver(observer)
            if (treeObserver.isAlive) treeObserver.removeOnGlobalLayoutListener(layoutListener)
            else if (view.viewTreeObserver.isAlive) {
                view.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
            }
            view.removeOnAttachStateChangeListener(attachListener)
            context.unregisterReceiver(receiver)
            context.contentResolver.unregisterContentObserver(scaleObserver)
        }
    }

    val motionScale = durationScale?.scaleFactor ?: systemMotionScale
    val animate = neuralExpressiveMotionAllowed(
        enabled = enabled && control.motionEnabled && control.timeSeconds == null,
        visible = visible && interactive,
        lifecycleStarted = started,
        viewVisible = viewVisible,
        powerSave = powerSave,
        motionScale = minOf(motionScale, systemMotionScale)
    )
    // Read only during draw: a moving particle must not recompose the assistant's controls.
    var elapsed by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animate, motionScale, control) {
        if (!animate) return@LaunchedEffect
        var previous = 0L
        while (true) {
            withFrameNanos { now ->
                if (previous != 0L) {
                    elapsed = (elapsed + (now - previous) / 1_000_000_000f / motionScale) % 120f
                }
                previous = now
                control.onFrame?.invoke(elapsed)
            }
        }
    }

    val secondary = MaterialTheme.colorScheme.tertiary
    return drawWithCache {
        val nodeWidth = size.width
        val nodeHeight = size.height
        val hasTarget = targetRect != null && targetRect.width > 0f && targetRect.height > 0f
        val target = if (hasTarget) targetRect!! else Rect(Offset.Zero, size)
        val width = target.width
        val height = target.height
        val radius = cornerRadius.toPx().coerceIn(0f, minOf(width, height) / 2f)
        val spread = 16.dp.toPx()
        val cutout = Path().apply {
            addRoundRect(RoundRect(
                target.left, target.top, target.right, target.bottom, CornerRadius(radius)
            ))
        }
        val halo = color.copy(alpha = color.alpha * 0.48f)
        val clear = color.copy(alpha = 0f)
        val top = Brush.verticalGradient(listOf(clear, halo), -spread, 0f)
        val bottom = Brush.verticalGradient(listOf(halo, clear), height, height + spread)
        val left = Brush.horizontalGradient(listOf(clear, halo), -spread, 0f)
        val right = Brush.horizontalGradient(listOf(halo, clear), width, width + spread)
        val cornerExtent = radius + spread
        val cornerStops = arrayOf(
            0f to halo,
            radius / cornerExtent to halo,
            (radius + spread * 0.4f) / cornerExtent to halo.copy(alpha = halo.alpha * 0.36f),
            1f to clear
        )
        val cornerCenters = listOf(
            Offset(radius, radius), Offset(width - radius, radius),
            Offset(width - radius, height - radius), Offset(radius, height - radius)
        )
        val corners = cornerCenters.map { Brush.radialGradient(*cornerStops, center = it, radius = cornerExtent) }
        val seeds = neuralExpressiveSeeds(dotCount.coerceAtMost(240))
        onDrawBehind {
            if (nodeWidth <= 0f || nodeHeight <= 0f || width <= 0f || height <= 0f ||
                !started || !viewVisible || !interactive
            ) return@onDrawBehind
            clipPath(cutout, ClipOp.Difference) {
                translate(left = target.left, top = target.top) {
                    drawRect(top, Offset(radius, -spread), Size((width - 2f * radius).coerceAtLeast(0f), spread))
                    drawRect(bottom, Offset(radius, height), Size((width - 2f * radius).coerceAtLeast(0f), spread))
                    drawRect(left, Offset(-spread, radius), Size(spread, (height - 2f * radius).coerceAtLeast(0f)))
                    drawRect(right, Offset(width, radius), Size(spread, (height - 2f * radius).coerceAtLeast(0f)))
                    corners.forEachIndexed { index, brush ->
                        drawArc(
                            brush = brush, startAngle = 180f + index * 90f, sweepAngle = 90f,
                            useCenter = true, topLeft = cornerCenters[index] - Offset(cornerExtent, cornerExtent),
                            size = Size(cornerExtent * 2f, cornerExtent * 2f)
                        )
                    }
                    val time = control.timeSeconds?.takeIf { it.isFinite() } ?: elapsed
                    seeds.forEach { seed ->
                        val particle = neuralExpressiveParticle(seed, width, height, radius, density, time)
                        val tint = if (seed.accent) secondary else color
                        drawCircle(tint.copy(alpha = tint.alpha * particle.alpha), particle.radius, particle.center)
                    }
                }
            }
        }
    }
}

private fun View.isDecorationVisible(): Boolean =
    isAttachedToWindow && isShown && windowVisibility == View.VISIBLE

private fun Context.animatorScale(): Float =
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

internal fun neuralExpressiveMotionAllowed(
    enabled: Boolean,
    visible: Boolean,
    lifecycleStarted: Boolean,
    viewVisible: Boolean,
    powerSave: Boolean,
    motionScale: Float
): Boolean = enabled && visible && lifecycleStarted && viewVisible && !powerSave &&
    motionScale.isFinite() && motionScale > 0f

internal data class NeuralExpressiveSeed(
    val phase: Float,
    val distance: Float,
    val radius: Float,
    val opacity: Float,
    val accent: Boolean
)

internal data class NeuralExpressiveParticle(val center: Offset, val radius: Float, val alpha: Float)

/** Stable, uneven spacing and three depth bands, without a random generator or asynchronous work. */
internal fun neuralExpressiveSeeds(count: Int): List<NeuralExpressiveSeed> =
    List(count.coerceAtLeast(0)) { index ->
        val scatter = ((index * 0.618034f) % 1f)
        NeuralExpressiveSeed(
            phase = (index + scatter * 0.7f) / count,
            distance = 3.5f + (index % 3) * 6f + scatter * 7f,
            radius = 0.65f + scatter * 1.05f,
            opacity = 0.32f + (1f - scatter) * 0.46f,
            accent = index % 4 == 0
        )
    }

/** Inflating the actual rounded rectangle keeps every dot outside its fill, including corners. */
internal fun neuralExpressiveParticle(
    seed: NeuralExpressiveSeed,
    width: Float,
    height: Float,
    radius: Float,
    density: Float,
    timeSeconds: Float
): NeuralExpressiveParticle {
    val wave = sin(timeSeconds * 0.65f + seed.phase * 2f * PI.toFloat())
    val outset = (seed.distance + wave * 1.2f) * density
    val r = radius.coerceIn(0f, minOf(width, height) / 2f)
    val w = width + 2f * outset
    val h = height + 2f * outset
    val outerRadius = r + outset
    val perimeter = perimeterOf(w, h, outerRadius)
    val phase = ((seed.phase + timeSeconds / 120f) % 1f + 1f) % 1f
    return NeuralExpressiveParticle(
        center = pointOnRoundedRect(phase * perimeter, w, h, outerRadius) - Offset(outset, outset),
        radius = seed.radius * density,
        alpha = seed.opacity * (0.8f + 0.2f * wave)
    )
}

private fun perimeterOf(width: Float, height: Float, radius: Float): Float {
    val straight = (width - 2f * radius).coerceAtLeast(0f) + (height - 2f * radius).coerceAtLeast(0f)
    return 2f * straight + 2f * PI.toFloat() * radius
}

/** Walk straight edges and quarter circles rather than an ellipse. */
private fun pointOnRoundedRect(distance: Float, width: Float, height: Float, radius: Float): Offset {
    val topRun = (width - 2f * radius).coerceAtLeast(0f)
    val sideRun = (height - 2f * radius).coerceAtLeast(0f)
    val arc = PI.toFloat() * radius / 2f
    var remaining = distance
    if (remaining <= topRun) return Offset(radius + remaining, 0f)
    remaining -= topRun
    if (remaining <= arc) {
        val angle = -PI.toFloat() / 2f + remaining / radius
        return Offset(width - radius + radius * cos(angle), radius + radius * sin(angle))
    }
    remaining -= arc
    if (remaining <= sideRun) return Offset(width, radius + remaining)
    remaining -= sideRun
    if (remaining <= arc) {
        val angle = remaining / radius
        return Offset(width - radius + radius * cos(angle), height - radius + radius * sin(angle))
    }
    remaining -= arc
    if (remaining <= topRun) return Offset(width - radius - remaining, height)
    remaining -= topRun
    if (remaining <= arc) {
        val angle = PI.toFloat() / 2f + remaining / radius
        return Offset(radius + radius * cos(angle), height - radius + radius * sin(angle))
    }
    remaining -= arc
    if (remaining <= sideRun) return Offset(0f, height - radius - remaining)
    remaining -= sideRun
    val angle = PI.toFloat() + remaining / radius
    return Offset(radius + radius * cos(angle), radius + radius * sin(angle))
}
