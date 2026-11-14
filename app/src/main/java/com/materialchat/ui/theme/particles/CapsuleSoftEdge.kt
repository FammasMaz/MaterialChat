/*
 * Soft boundary of the capsule and the accent glow inside it.
 *
 * essentials (see the spec in NeuralParticleField.kt) has NO outward glow ring: its pill is a
 * translucent dark fill whose boundary is softened by a progressive RenderEffect blur of the
 * fill itself (2dp at the top edge to 16dp at the bottom), with the accent colour glowing
 * INSIDE the pill along the edge (BlurMaskFilter inner glow, clipped to the pill). MaterialChat
 * paints the same three things: the pill colour as a blurred capsule (API 31+; a geometric
 * feather below), the accent inner glow recorded into the same blurred layer so it shares the
 * soft edge, and nothing outside the capsule except the dots.
 *
 * The outward halo API is kept for callers that want a glow ring but is off by default.
 */
package com.materialchat.ui.theme.particles

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How the field's boundary is softened. */
enum class ParticleEdgeTreatment {
    /** The capsule fill (and inner glow) is recorded into a layer blurred by a RenderEffect (API 31+). */
    Blur,
    /** A geometric stroke-gradient feather softens the edge (API 26-30 or software canvases). */
    Gradient
}

/** Blur, inner glow and (optional) outward halo geometry around the capsule edge. */
@Immutable
data class ParticleEdgeStyle(
    /**
     * Blur applied to the capsule fill on API 31+; 0 disables the RenderEffect pass. essentials
     * runs 2..16dp across the pill; a single 7dp keeps the edge soft but still legible as a pill
     * rather than a fog (the previous 14dp read as a wide white haze).
     */
    val blurRadius: Dp = 16.dp,
    /** Inward reach of the accent glow from the capsule edge (essentials: 40dp-blur inner glow). */
    val innerGlowWidth: Dp = 26.dp,
    /** Peak inner-glow alpha at the capsule edge (essentials: accent at 0.6 over a dark fill). */
    val innerGlowOpacity: Float = 0.34f,
    /** Number of concentric strokes approximating the inner glow gradient; 0 disables it. */
    val innerGlowSteps: Int = 12,
    /** Outward reach of an optional glow halo from the capsule edge. */
    val haloWidth: Dp = 20.dp,
    /** Peak halo alpha at the capsule edge (multiplied by the field colour's alpha). */
    val haloOpacity: Float = 0.45f,
    /** Number of concentric strokes approximating the halo gradient; 0 (default) disables the halo. */
    val haloSteps: Int = 0,
) {
    init {
        require(blurRadius.value.isFinite() && blurRadius.value >= 0f) { "blurRadius must be >= 0" }
        require(innerGlowWidth.value.isFinite() && innerGlowWidth.value >= 0f) { "innerGlowWidth must be >= 0" }
        require(innerGlowOpacity.isFinite() && innerGlowOpacity in 0f..1f) { "innerGlowOpacity must be within 0..1" }
        require(innerGlowSteps >= 0) { "innerGlowSteps must be >= 0" }
        require(haloWidth.value.isFinite() && haloWidth.value >= 0f) { "haloWidth must be >= 0" }
        require(haloOpacity.isFinite() && haloOpacity in 0f..1f) { "haloOpacity must be within 0..1" }
        require(haloSteps >= 0) { "haloSteps must be >= 0" }
    }
}

internal fun resolveParticleEdgeTreatment(
    sdkInt: Int,
    blurRadiusPx: Float,
    hardwareAccelerated: Boolean,
): ParticleEdgeTreatment =
    if (sdkInt >= 31 && hardwareAccelerated && blurRadiusPx.isFinite() && blurRadiusPx > 0f) {
        ParticleEdgeTreatment.Blur
    } else {
        ParticleEdgeTreatment.Gradient
    }

/**
 * One concentric stroke centred [outset] px from the capsule edge (positive = outside, negative =
 * inside), [strokeWidth] wide.
 */
internal data class HaloRing(val outset: Float, val strokeWidth: Float, val alpha: Float)

/**
 * Strokes that tile [widthPx] outward from the edge without overlapping the capsule interior:
 * ring i is centred at (i + 0.5) * step so its inner edge sits at i * step (0 for the first).
 * Alpha follows the band envelope, so the glow is strongest hugging the edge and reaches zero at
 * [widthPx].
 */
internal fun haloRings(widthPx: Float, steps: Int, opacity: Float): List<HaloRing> {
    if (steps <= 0 || widthPx <= 0f || !widthPx.isFinite() || opacity <= 0f) return emptyList()
    val step = widthPx / steps
    return List(steps) { i ->
        val centre = (i + 0.5f) * step
        HaloRing(outset = centre, strokeWidth = step, alpha = opacity * particleAlphaEnvelope(centre, 0f, widthPx))
    }.filter { it.alpha > 0f }
}

/**
 * Strokes that tile [widthPx] inward from the edge: ring i is centred at -(i + 0.5) * step so the
 * first ring's outer edge sits exactly on the capsule boundary. Alpha is strongest at the edge
 * and reaches zero [widthPx] inside, the mirror image of [haloRings].
 */
internal fun innerGlowRings(widthPx: Float, steps: Int, opacity: Float): List<HaloRing> =
    haloRings(widthPx, steps, opacity).map { it.copy(outset = -it.outset) }

private fun DrawScope.capsuleRing(geometry: CapsuleParticleGeometry, color: Color, outset: Float, stroke: Float?) {
    val rect = geometry.rect
    val w = rect.width + 2f * outset
    val h = rect.height + 2f * outset
    if (w <= 0f || h <= 0f) return
    val radius = (geometry.radius + outset).coerceAtLeast(0f)
    drawRoundRect(
        color = color,
        topLeft = Offset(rect.left - outset, rect.top - outset),
        size = Size(w, h),
        cornerRadius = CornerRadius(radius, radius),
        // A hair of overlap hides the seam between neighbouring strokes.
        style = if (stroke == null) Fill else Stroke(width = stroke + 0.5f)
    )
}

internal fun DrawScope.drawCapsuleHalo(
    geometry: CapsuleParticleGeometry,
    color: Color,
    alpha: Float,
    rings: List<HaloRing>,
) {
    if (alpha <= 0f || rings.isEmpty()) return
    for (ring in rings) {
        val ringAlpha = color.alpha * alpha * ring.alpha
        if (ringAlpha <= 0f) continue
        capsuleRing(geometry, color.copy(alpha = ringAlpha), ring.outset, ring.strokeWidth)
    }
}

/**
 * The accent glow inside the capsule along its edge, clipped to the capsule inset by [clipInset]
 * (0 when the caller blurs the result; half the feather width for the geometric fallback, so the
 * glow's hard clip never shows outside the solid core of the feathered fill).
 */
internal fun DrawScope.drawCapsuleInnerGlow(
    geometry: CapsuleParticleGeometry,
    color: Color,
    alpha: Float,
    rings: List<HaloRing>,
    clipInset: Float,
    clip: Path,
) {
    if (alpha <= 0f || rings.isEmpty()) return
    val rect = geometry.rect
    val inset = clipInset.coerceIn(0f, minOf(rect.width, rect.height) / 2f)
    val radius = (geometry.radius - inset).coerceAtLeast(0f)
    clip.rewind()
    clip.addRoundRect(RoundRect(rect.deflate(inset), CornerRadius(radius, radius)))
    clipPath(clip) {
        for (ring in rings) {
            val ringAlpha = color.alpha * alpha * ring.alpha
            if (ringAlpha <= 0f) continue
            capsuleRing(geometry, color.copy(alpha = ringAlpha), ring.outset, ring.strokeWidth)
        }
    }
}

/**
 * A capsule filled with [color] whose boundary is soft. Blurred (API 31+): a solid capsule that the
 * caller's RenderEffect softens. Fallback: a solid core inset by half [featherPx] plus concentric
 * strokes fading from opaque to transparent across the boundary.
 */
internal fun DrawScope.drawCapsuleSoftFill(
    geometry: CapsuleParticleGeometry,
    color: Color,
    featherPx: Float,
    blurred: Boolean,
) {
    if (blurred || featherPx <= 0f || !featherPx.isFinite()) {
        capsuleRing(geometry, color, 0f, null)
        return
    }
    capsuleRing(geometry, color, -featherPx / 2f, null)
    val steps = 10
    val step = featherPx / steps
    for (i in 0 until steps) {
        val outset = -featherPx / 2f + (i + 0.5f) * step
        val t = (i + 0.5f) / steps
        val a = 1f - t * t * (3f - 2f * t)
        if (a > 0f) capsuleRing(geometry, color.copy(alpha = color.alpha * a), outset, step)
    }
}
