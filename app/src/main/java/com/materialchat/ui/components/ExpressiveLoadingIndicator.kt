package com.materialchat.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.materialchat.ui.theme.ExpressiveMotion
import com.materialchat.ui.theme.particles.rememberParticleMotionGate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// The pulse loop below runs behind rememberParticleMotionGate and holds a static frame when gated.

// W9 part 2: `M3ExpressiveLoadingIndicator` (the morphing soft-burst hero, with its private
// `drawMorphingShape` / `lerp` / `MAX_MORPH_VERTICES`) had zero callers in main, androidTest,
// test, scripts/ and .github/ (word-exact grep) once W8 deleted its two in-file wrappers, and was
// deleted. Every hero indicator in the app is the stock M3 `LoadingIndicator` at
// `ExpressiveSizes.IndicatorHero`; the cycle tokens `Durations.Indicator.MorphRotation /
// MorphShape` stay (MorphShape is `FusionOrbit`; both pinned in `MotionTokensTest`).

// W7 (audit F14): `M3ExpressiveCircularProgress` (the spinning arc) and
// `M3ExpressiveLinearProgress` (the indeterminate bar) had zero callers and were deleted, with
// the legacy alias block (`ExpressiveLoadingIndicator`, `ExpressivePulsingIndicator`,
// `ExpressiveTypingIndicator`, `ExpressiveCircularProgress`, `ExpressiveSpinningArc`,
// `ExpressiveLoadingWithText`, `ExpressiveInlineLoading`, `ExpressiveFullscreenLoading`), also
// zero callers. Indeterminate arcs are the stock M3 `LoadingIndicator` / `CircularWavyProgressIndicator`.
// W8 part 4: `M3ExpressiveDeterminateProgress`, `M3ExpressiveLoadingWithText`,
// `M3ExpressiveFullscreenLoading` and `MorphingSendButton` (with its private
// `calculateMorphParams` / `drawMorphingShapeFilled`) had zero callers in main, androidTest,
// scripts/ and .github/ (word-exact grep) and were deleted. Chat's send control is
// `ComposerSendButton`; a determinate bar is the stock M3 `LinearProgressIndicator`.

/**
 * M3 Expressive Pulsing Dots Indicator with spring-based animation.
 */
@Composable
fun M3ExpressivePulsingDots(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    dotSize: Dp = 8.dp
) {
    val dots = remember { listOf(Animatable(0.4f), Animatable(0.4f), Animatable(0.4f)) }
    val motionAllowed = rememberParticleMotionGate().motionAllowed

    // The pulse loop obeys the host motion gate: when motion is off the dots rest at their
    // dim state instead of looping. Rise on the playful spatial spring, settle on the slow one.
    LaunchedEffect(motionAllowed) {
        if (!motionAllowed) {
            dots.forEach { it.snapTo(0.4f) }
            return@LaunchedEffect
        }
        while (true) {
            dots.forEachIndexed { index, animatable ->
                launch {
                    delay(index * 150L)
                    animatable.animateTo(
                        targetValue = 1f,
                        animationSpec = ExpressiveMotion.Spatial.playful()
                    )
                    animatable.animateTo(
                        targetValue = 0.4f,
                        animationSpec = ExpressiveMotion.Spatial.slow()
                    )
                }
            }
            delay(900L)
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        dots.forEach { animatable ->
            Canvas(modifier = Modifier.size(dotSize)) {
                drawCircle(
                    color = color.copy(alpha = animatable.value),
                    radius = (size.minDimension / 2) * (0.6f + 0.4f * animatable.value)
                )
            }
        }
    }
}

/**
 * M3 Expressive Inline Loading (for buttons).
 */
@Composable
fun M3ExpressiveInlineLoading(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onPrimary
) {
    M3ExpressivePulsingDots(
        modifier = modifier,
        color = color,
        dotSize = 6.dp
    )
}
