package com.materialchat.assistant.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.materialchat.ui.theme.particles.readAnimationScale
import kotlin.math.floor
import kotlin.math.pow

/**
 * The assistant's backdrop texture, after Gemini Live's bottom band: a bottom-anchored vertical
 * scrim with a uniform dot lattice inside it (small white dots on a regular grid), both fading
 * upward to nothing at the band's top edge and covering the band edge to edge.
 *
 * Read from the reference frames (band.png, dots4x.png): ~1dp dots on a ~7dp pitch, the band
 * roughly the bottom quarter of the screen, the scrim reaching about 55% black at the very
 * bottom. The lattice is deterministic (its geometry depends only on the node size), so it never
 * shimmers, and it requests frames only during the bounded presence fade.
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

    /** Dot alpha at the bottom of the band, white. */
    const val DOT_ALPHA = 0.32f

    /**
     * The band's own scrim at the bottom edge. It is added on top of the window's resting
     * 42% scrim, so the compound at the very bottom lands near the reference's ~55%.
     */
    const val SCRIM_ALPHA = 0.26f

    /** Fade in on arrival, aligned with the particle field's fade-in. */
    const val FADE_IN_MILLIS = 600

    /** Fade out while leaving; must settle inside the session's exit window ([AssistantExitMotion]). */
    const val FADE_OUT_MILLIS = 320
}

/** One row of the lattice: its y, its alpha factor (0 at the band top, 1 at the bottom), its dots. */
internal class LatticeRow(val y: Float, val alpha: Float, val points: List<Offset>)

/** The resolved lattice for one node size; pure geometry, no state. */
internal class LatticeGeometry(val bandTop: Float, val rows: List<LatticeRow>) {
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
 * Draws the bottom scrim band and its dot lattice behind this node's content, from this node,
 * so the texture spans the whole node regardless of the pill's bounds (below API 28 a node's
 * drawing is clipped to its own bounds, so the full-window host must own the band).
 *
 * [visible] drives a bounded presence fade: the texture starts absent and fades in on arrival,
 * fades out while leaving, and requests no frames once either fade has settled. [enabled]
 * removes the texture entirely (it follows the neural-expressive preference like the particles).
 */
internal fun Modifier.assistantDotLattice(
    visible: Boolean,
    enabled: Boolean = true,
    dotColor: Color = Color.White,
): Modifier = composed {
    val context = LocalContext.current
    val animationScale = remember(context) { readAnimationScale(context) }
    val presence = remember { Animatable(0f) }
    val target = if (visible && enabled) 1f else 0f
    LaunchedEffect(target) {
        val baseMillis = if (target > 0f) AssistantLatticeStyle.FADE_IN_MILLIS else AssistantLatticeStyle.FADE_OUT_MILLIS
        val millis = if (animationScale.isFinite() && animationScale > 0f) {
            (baseMillis * animationScale.coerceAtMost(10f)).toInt()
        } else 0
        // A bounded animation: it completes and then nothing here touches the frame clock again.
        if (millis > 0) presence.animateTo(target, tween(durationMillis = millis, easing = FastOutSlowInEasing))
        else presence.snapTo(target)
    }
    drawWithCache {
        val pitchPx = AssistantLatticeStyle.Pitch.toPx()
        val strokePx = AssistantLatticeStyle.DotDiameter.toPx()
        val geometry = LatticeGeometry.layout(
            size = size,
            pitchPx = pitchPx,
            minBandPx = AssistantLatticeStyle.MinBandHeight.toPx()
        )
        val bandTop = geometry.bandTop
        val scrim = Brush.verticalGradient(
            0f to Color.Transparent,
            1f to Color.Black.copy(alpha = AssistantLatticeStyle.SCRIM_ALPHA),
            startY = bandTop,
            endY = size.height
        )
        onDrawBehind {
            val p = presence.value
            if (p <= 0f || geometry.rows.isEmpty()) return@onDrawBehind
            drawRect(
                brush = scrim,
                topLeft = Offset(0f, bandTop),
                size = Size(size.width, size.height - bandTop),
                alpha = p
            )
            for (row in geometry.rows) {
                val alpha = p * row.alpha * AssistantLatticeStyle.DOT_ALPHA
                if (alpha <= 0.004f) continue
                drawPoints(
                    points = row.points,
                    pointMode = PointMode.Points,
                    color = dotColor,
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round,
                    alpha = alpha
                )
            }
        }
    }
}
