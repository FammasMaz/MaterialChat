package com.materialchat.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Fraction of the way (channel-wise sRGB, not Oklab) from true black to `surfaceContainerHigh`
 * at which the pitch-black `surfaceContainerLow` and `surfaceContainer` tiers start. These are
 * only the starting points: every tier is then lifted just enough to sit at least
 * [ThemeContrast.TIER_STEP] (1.1:1) above the tier below it, so the ramp keeps visible steps on
 * every shipped dark palette and on any wallpaper-dynamic dark ramp, however dark its
 * `surfaceContainerHigh` is.
 */
internal const val PitchBlackLowTierFraction = 0.42f
internal const val PitchBlackContainerTierFraction = 0.72f

/**
 * Replaces the surface ramp with true black, the way an OLED-tuned theme does.
 *
 * `background`, `surface`, `surfaceDim` and `surfaceContainerLowest` go to #000000 so the
 * panel is off wherever nothing is drawn. The raised tiers keep a visible tonal step:
 * `surfaceContainerLow` and `surfaceContainer` are re-derived between black and the scheme's
 * own `surfaceContainerHigh` (so wallpaper-dynamic hues are preserved), and every tier,
 * `High` and `Highest` included, is lifted toward white only as far as needed to stay
 * [ThemeContrast.TIER_STEP] above the tier below it. Cards, sheets and bubbles therefore stay
 * distinguishable from the list they sit in instead of collapsing into one black plane.
 */
fun ColorScheme.withPitchBlackSurfaces(): ColorScheme {
    val high = surfaceContainerHigh
    val low = mixSrgb(Color.Black, high, PitchBlackLowTierFraction).atLeastStepAbove(Color.Black)
    val container = mixSrgb(Color.Black, high, PitchBlackContainerTierFraction).atLeastStepAbove(low)
    val liftedHigh = high.atLeastStepAbove(container)
    val liftedHighest = surfaceContainerHighest.atLeastStepAbove(liftedHigh)
    return copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = low,
        surfaceContainer = container,
        surfaceContainerHigh = liftedHigh,
        surfaceContainerHighest = liftedHighest
    )
}

/** Channel-wise sRGB mix; Compose's `lerp(Color, Color, Float)` interpolates in Oklab instead. */
internal fun mixSrgb(from: Color, to: Color, fraction: Float): Color {
    val t = fraction.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * t,
        green = from.green + (to.green - from.green) * t,
        blue = from.blue + (to.blue - from.blue) * t,
        alpha = 1f
    )
}

/**
 * This colour, or the nearest point on its sRGB line toward white whose contrast over [below]
 * reaches [ThemeContrast.TIER_STEP]. Hue is kept; only lightness rises, and only when needed.
 */
internal fun Color.atLeastStepAbove(below: Color): Color {
    // Directional: the upper tier must be LIGHTER than the lower one by the step. A symmetric
    // contrast ratio would also accept a tier that is darker by the step, which lets a lifted
    // lower tier overtake a dark wallpaper-dynamic High and invert the ramp.
    val target = (ThemeContrast.relativeLuminance(below) + 0.05) * ThemeContrast.TIER_STEP - 0.05
    fun clears(c: Color) = ThemeContrast.relativeLuminance(c) >= target
    if (clears(this)) return this
    var lo = 0f
    var hi = 1f
    repeat(24) {
        val mid = (lo + hi) / 2f
        if (clears(mixSrgb(this, Color.White, mid))) hi = mid else lo = mid
    }
    // Round up one 8-bit step so quantisation into Color's byte channels cannot fall back under the bar.
    return mixSrgb(this, Color.White, (hi + 1f / 255f).coerceAtMost(1f))
}
