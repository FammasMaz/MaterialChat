/*
 * Soft boundary around the capsule. essentials gets its soft pill edge from the island's
 * blurred backdrop plus the AGSL smoothstep particle edges; MaterialChat has no backdrop blur on
 * the assistant scrim, so the glow is drawn explicitly as concentric strokes whose alpha follows
 * the band envelope, then softened by a RenderEffect blur where the platform has one (API 31+).
 * Below that the stroke gradient alone is the "blur": the fallback is purely geometric.
 */
package com.materialchat.ui.theme.particles

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How the field's boundary is softened. */
enum class ParticleEdgeTreatment {
    /** Particles and halo are recorded into a layer and blurred with a RenderEffect (API 31+). */
    Blur,
    /** Only the stroke-gradient halo softens the edge (API 26-30 or software canvases). */
    Gradient
}

/** Blur and glow geometry around the capsule edge. */
@Immutable
data class ParticleEdgeStyle(
    /** Blur applied to the edge glow on API 31+; 0 disables the RenderEffect pass. */
    val blurRadius: Dp = 14.dp,
    /** Outward reach of the glow halo from the capsule edge. */
    val haloWidth: Dp = 20.dp,
    /** Peak halo alpha at the capsule edge (multiplied by the field colour's alpha). */
    val haloOpacity: Float = 0.45f,
    /** Number of concentric strokes approximating the halo gradient; 0 disables the halo. */
    val haloSteps: Int = 8,
) {
    init {
        require(blurRadius.value.isFinite() && blurRadius.value >= 0f) { "blurRadius must be >= 0" }
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

/** One concentric stroke: centred [outset] px outside the capsule edge, [strokeWidth] wide. */
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

internal fun DrawScope.drawCapsuleHalo(
    geometry: CapsuleParticleGeometry,
    color: Color,
    alpha: Float,
    rings: List<HaloRing>,
) {
    if (alpha <= 0f || rings.isEmpty()) return
    val rect = geometry.rect
    for (ring in rings) {
        val ringAlpha = color.alpha * alpha * ring.alpha
        if (ringAlpha <= 0f) continue
        val radius = geometry.radius + ring.outset
        drawRoundRect(
            color = color.copy(alpha = ringAlpha),
            topLeft = Offset(rect.left - ring.outset, rect.top - ring.outset),
            size = Size(rect.width + 2f * ring.outset, rect.height + 2f * ring.outset),
            cornerRadius = CornerRadius(radius, radius),
            // A hair of overlap hides the seam between neighbouring strokes before the blur.
            style = Stroke(width = ring.strokeWidth + 0.5f)
        )
    }
}
