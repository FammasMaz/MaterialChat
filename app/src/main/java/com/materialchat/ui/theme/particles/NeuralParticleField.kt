/*
 * Ported from sameerasw/essentials (MIT) — island/plugins/media/particles/GeminiParticlePill.kt
 *
 * VISUAL SPEC of the reference (essentials island, "neural expressive" media pill), read from
 * IslandRoot.kt, MediaSurfaceBackdrop.kt, GeminiParticlePill.kt, ParticleFieldStyle.kt,
 * ParticleFieldMath.kt, GeminiParticleShader.kt and IslandBackdropBlur.kt:
 *
 *  Layer order (bottom to top), all drawn by one full-window parent around the pill rect:
 *   1. Backdrop blur (optional, off by default, 40dp): a SurfaceFlinger region blur of what is
 *      behind the WINDOW inside the pill's rounded rect (ViewRootImpl.createBackgroundBlurDrawable,
 *      hidden API reached through HiddenApiBypass). It blurs the wallpaper/app, never the pill.
 *   2. Pill fill (MediaSurface): a radial gradient of BLACK, opaque at the camera/top and ~50%
 *      (0x80) at the far edge, so the pill is a translucent dark glass. An inner glow of the
 *      ACCENT colour (item accent, else Material primary) sits inside the pill along its edge:
 *      BlurMaskFilter 20dp, alpha 0.6, shifted 14dp upward, plus a 3dp rim at 0.5; both clipped
 *      to the pill. The fill+glow are then blurred as a whole by a progressive RenderEffect
 *      (2dp at the pill's top edge to 16dp at its bottom, scaled by the 1450ms pulse) with
 *      Unbounded edge treatment: THIS is the "blurry edge" — the pill's own boundary is
 *      softened, nothing glows outward.
 *   3. Pill content (icons/text), crisp, on top of the fill.
 *   4. Dots (GeminiParticlePill) drawn LAST, in front of the content and over the edge:
 *      - colour = the same accent (`glow`), not white; alpha = 0.35 × pulse activation;
 *      - Poisson-disk field, min spacing 6dp, radius 0..1.5dp (shrinks with speed);
 *      - band: 64dp outside, 48dp inside the edge, smoothstep envelope raised to the 4th power,
 *        so visibly dense within ~10dp of the edge, sparse by ~30dp out / ~25dp in, gone beyond;
 *        dots cross the boundary freely and lie over the fill, over the content, and outside;
 *      - motion: curl noise (fbm Perlin), 12dp displacement, ~83dp noise scale, speed 1;
 *      - crisp: the AGSL shader gives each dot a 1px smoothstep edge; dots are never blurred;
 *      - presence: alpha follows the surface pulse (fades in/out with it), not a constant.
 *
 *  So "blurry edges + floating dots" means: soft-edged dark translucent pill, accent glow
 *  inside it, accent dots floating across its boundary in front of everything. No white fog.
 *
 * GAPS of v2.29.34 against that spec, fixed here:
 *   a. White 20dp outward halo at 0.45 alpha, blurred 14dp: the reference has no outward glow at
 *      all — this was the "white fog ring". Removed (haloSteps default 0).
 *   b. Dots were white; the reference tints them with the accent. Now colorScheme.primary.
 *   c. The fill blur was 14dp, twice the reference's deepest edge, so the whole pill boundary
 *      smeared ~1cm. Now 7dp (12dp geometric feather below API 31).
 *   d. No inner accent glow, so the pill was a flat grey blob. Added, recorded into the same
 *      blurred layer as the fill so it shares the soft edge.
 *   e. innerBand 14dp kept every dot outside; the reference lets dots 48dp inside. Raised to
 *      20dp, the pill's own content padding, so dots cross the edge but stay off text/buttons.
 *   f. No backdrop blur: probed through the reference's mechanism (CapsuleBackdropBlur.kt) and
 *      logged; expected unavailable without a hidden-API exemption (see that file).
 *
 * The drawing node must fully contain the outer band: MaterialChat applies this to the
 * full-window scrim parent and passes the assistant card's rect, because below API 28 a node's
 * drawing is clipped to its own bounds (project memory #3904). Presence: the field fades out
 * when [active] turns false or after [idleTimeoutMillis], and stops requesting frames once
 * fully faded (ParticleFade.kt).
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
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
 * time [active] (or the field's visibility) flips back to true. By default the first
 * composition snaps to the current target; with [fadeInOnFirstAppearance] the field starts
 * invisible and fades in the first time it becomes active and visible.
 *
 * The soft-edged fill is drawn whenever [featherColor] is set, independent of presence, and the
 * inner glow never drops below [restingGlow], so a capsule keeps its fill and glow while the
 * dots are faded.
 *
 * Draw order, as in essentials: with [featherColor] set, the capsule is filled with that colour
 * (its boundary blurred on API 31+, feathered geometrically below) and the accent inner glow is
 * recorded into the same layer, then this node's content is drawn crisp on top, then the dots
 * are drawn last so they float over the edge in front of everything. The blur never touches the
 * content: only this node's own background drawing is recorded into the blurred layer. Without
 * [featherColor] only the dots (and the optional outward halo) are drawn, still in front.
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
    /** The capsule's own surface colour; when set the field paints the soft-edged fill. */
    featherColor: Color? = null,
    /** Width of the geometric feather used where no RenderEffect blur exists (API 26-30). */
    featherWidth: Dp = 24.dp,
    /** Start invisible and fade in on the first appearance instead of snapping to the target. */
    fadeInOnFirstAppearance: Boolean = false,
    /** Inner-glow strength floor (0..1) kept while the dots are faded. */
    restingGlow: Float = 0f,
    /**
     * Whether the scattered dots are drawn. Turning them off keeps everything else — the
     * soft-edged fill, the edge blur and the inner glow — so a capsule can keep its feathered
     * boundary while another texture (the assistant's dot lattice) owns the dots.
     */
    dotsEnabled: Boolean = true,
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
    // By default the first composition snaps to the current target; a caller that wants the
    // first appearance to fade in starts from zero instead.
    var presence by remember { mutableFloatStateOf(if (fadeInOnFirstAppearance) 0f else target) }
    val fadeScale = if (control.motionEnabled) animationScale else 0f
    LaunchedEffect(target, visible, fadeSpec, fadeScale) {
        if (presence == target) return@LaunchedEffect
        if (!visible) {
            // Nothing is drawn while hidden, so a fade-out would only burn frames: settle
            // silently. A fade-in waits for visibility (the host may not be STARTED yet on the
            // first composition) so it is never lost to a snap.
            if (target == 0f) presence = target
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
            val blurred = edgeTreatment == ParticleEdgeTreatment.Blur
            val haloRings = haloRings(edgeStyle.haloWidth.toPx(), edgeStyle.haloSteps, edgeStyle.haloOpacity)
            val glowRings = innerGlowRings(
                edgeStyle.innerGlowWidth.toPx(), edgeStyle.innerGlowSteps, edgeStyle.innerGlowOpacity
            )
            val featherPx = featherWidth.toPx()
            val glowClip = Path()
            // Decal: the fill fades into transparency at the layer edge instead of smearing the
            // last row of pixels outward.
            val blurLayer = if (blurred && (featherColor != null || haloRings.isNotEmpty())) {
                obtainGraphicsLayer().also {
                    it.renderEffect = BlurEffect(blurRadiusPx, blurRadiusPx, TileMode.Decal)
                }
            } else null
            onDrawWithContent {
                // No rect and no inset means the caller has nothing to decorate yet.
                val capsule = if (capsuleRect == null && capsuleInset == null) null
                else CapsuleParticleGeometry.create(size, capsuleInset?.toPx() ?: 0f, cornerRadius.toPx(), capsuleRect)
                val strength = if (visible && capsule != null) particleActivation(latestActivation()) * presence else 0f
                // The glow keeps its resting floor while the dots are faded.
                val glowStrength = if (visible && capsule != null)
                    particleActivation(latestActivation()) * glowPresence(presence, restingGlow) else 0f
                val alpha = strength * style.opacity
                val samples = tile
                if (capsule != null) {
                    if (featherColor != null) {
                        // Soft-edged pill: its own colour, boundary blurred (or feathered), with the
                        // accent glowing inside along the edge. Recorded together so the glow's edge
                        // is as soft as the fill's.
                        val drawFill: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit = {
                            drawCapsuleSoftFill(capsule, featherColor, featherPx, blurred = blurred)
                            drawCapsuleInnerGlow(
                                capsule, latestColor, glowStrength, glowRings,
                                clipInset = if (blurred) 0f else featherPx / 2f, clip = glowClip
                            )
                        }
                        if (blurLayer != null) {
                            blurLayer.record { drawFill() }
                            drawLayer(blurLayer)
                        } else {
                            drawFill()
                        }
                    } else if (strength > 0f && haloRings.isNotEmpty()) {
                        if (blurLayer != null) {
                            blurLayer.record { drawCapsuleHalo(capsule, latestColor, strength, haloRings) }
                            drawLayer(blurLayer)
                        } else {
                            drawCapsuleHalo(capsule, latestColor, strength, haloRings)
                        }
                    }
                }
                drawContent()
                // Dots last: in front of the content and over the edge, never blurred.
                if (dotsEnabled && capsule != null && samples != null && strength > 0f) {
                    NeuralParticleDiagnostics.record(painter.kind, edgeTreatment, capsule)
                    val time = control.timeSeconds?.takeIf { it.isFinite() } ?: elapsed
                    if (alpha > 0f) {
                        with(painter) {
                            paint(capsule, time, alpha, latestColor, pixels, screenOrigin, samples)
                        }
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

internal fun readAnimationScale(context: Context): Float = runCatching {
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
