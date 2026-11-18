package com.materialchat.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG 2.x contrast arithmetic for the theme foundation.
 *
 * Pure Kotlin (no composition, no Android) so the JVM contrast gate can enumerate every
 * palette the app ships. The assistant window keeps its own private copy
 * (`assistant/ui/AssistantContrast.kt`); the two agree to the last digit on the reference
 * values the tests pin.
 */
object ThemeContrast {
    /** WCAG AA for body-sized text. */
    const val BODY_TEXT = 4.5

    /** WCAG AA for large text (>= 18pt regular / 14pt bold). */
    const val LARGE_TEXT = 3.0

    /** WCAG AA for icons and other graphical objects. */
    const val ICON = 3.0

    /**
     * Minimum luminance step between neighbouring surface-container tiers so a card still
     * reads as raised on the tier below it (used by the pitch-black ramp).
     */
    const val TIER_STEP = 1.1

    /** WCAG relative luminance of [color] taken as opaque sRGB. */
    fun relativeLuminance(color: Color): Double {
        val c = opaque(color)
        return 0.2126 * linear(c.red) + 0.7152 * linear(c.green) + 0.0722 * linear(c.blue)
    }

    /** Source-over composite of a translucent [foreground] on an opaque [background]. */
    fun compositeOver(foreground: Color, background: Color): Color {
        val a = foreground.alpha
        if (a >= 1f) return foreground
        val bg = opaque(background)
        return Color(
            red = foreground.red * a + bg.red * (1f - a),
            green = foreground.green * a + bg.green * (1f - a),
            blue = foreground.blue * a + bg.blue * (1f - a),
            alpha = 1f
        )
    }

    /**
     * Contrast ratio (1..21) between [foreground] drawn on [background]. A translucent
     * foreground is composited first; the background is taken as opaque (it is a painted fill).
     */
    fun contrastRatio(foreground: Color, background: Color): Double {
        val bg = opaque(background)
        val fg = compositeOver(foreground, bg)
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(bg)
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    }

    /**
     * The first of [candidates] (in order of preference, pure white and black appended) that
     * reaches [minimum] against [fill]; if none does, the candidate with the highest ratio.
     * For any opaque fill the better of white/black is >= 4.58:1, so a readable colour always
     * exists.
     */
    fun pick(fill: Color, candidates: List<Color>, minimum: Double): Color {
        val all = candidates + listOf(Color.White, Color.Black)
        all.firstOrNull { contrastRatio(it, fill) >= minimum }?.let { return it }
        return all.maxByOrNull { contrastRatio(it, fill) } ?: Color.Black
    }

    private fun opaque(color: Color): Color = if (color.alpha >= 1f) color else color.copy(alpha = 1f)

    private fun linear(channel: Float): Double {
        val v = channel.toDouble().coerceIn(0.0, 1.0)
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }
}

/** One container/content pair a theme guarantees, with the ratio it must reach. */
data class ThemeContrastPair(
    val name: String,
    val container: Color,
    val content: Color,
    val minimum: Double
) {
    val ratio: Double get() = ThemeContrast.contrastRatio(content, container)
    val passes: Boolean get() = ratio >= minimum
}

/**
 * Runtime guard for fills the theme cannot vouch for (wallpaper-dynamic schemes, tinted or
 * blended containers): the first of [preferred] that reads on [fill] at [minimum], falling back
 * through `onSurface`, `inverseOnSurface`, white and black. Prefer the plain role pairs
 * (`onPrimaryContainer` on `primaryContainer`, ...) whenever the fill IS that role's container;
 * reach for this only when it is not.
 */
fun ColorScheme.readableOn(
    fill: Color,
    minimum: Double = ThemeContrast.BODY_TEXT,
    preferred: List<Color> = emptyList()
): Color = ThemeContrast.pick(
    fill,
    preferred + listOf(onSurface, inverseOnSurface),
    minimum
)

/** [readableOn] for secondary text: tries `onSurfaceVariant` first. */
fun ColorScheme.readableMutedOn(fill: Color): Color =
    readableOn(fill, ThemeContrast.BODY_TEXT, listOf(onSurfaceVariant))

/** [readableOn] for an accent icon: tries `primary` first at the icon threshold. */
fun ColorScheme.readableAccentOn(fill: Color): Color =
    readableOn(fill, ThemeContrast.ICON, listOf(primary, onPrimaryContainer))

/** The surface tiers text and icons are drawn on, in ascending tonal order. */
fun ColorScheme.surfaceTiers(): List<Pair<String, Color>> = listOf(
    "surfaceContainerLowest" to surfaceContainerLowest,
    "surfaceContainerLow" to surfaceContainerLow,
    "surfaceContainer" to surfaceContainer,
    "surfaceContainerHigh" to surfaceContainerHigh,
    "surfaceContainerHighest" to surfaceContainerHighest
)

/** Every fill that `onSurface` / `onSurfaceVariant` / `outline` are drawn on. */
fun ColorScheme.surfaceFills(): List<Pair<String, Color>> = listOf(
    "surface" to surface,
    "background" to background,
    "surfaceDim" to surfaceDim,
    "surfaceBright" to surfaceBright,
    "surfaceVariant" to surfaceVariant
) + surfaceTiers()

/**
 * The standard Material 3 role pairs every palette the app ships must satisfy. This is the
 * single list the JVM contrast gate asserts, so a palette edit that breaks a pair fails the
 * build instead of a screen.
 */
fun ColorScheme.standardContrastPairs(): List<ThemeContrastPair> = buildList {
    fun body(name: String, container: Color, content: Color) =
        add(ThemeContrastPair(name, container, content, ThemeContrast.BODY_TEXT))
    fun icon(name: String, container: Color, content: Color) =
        add(ThemeContrastPair(name, container, content, ThemeContrast.ICON))

    // Accent roles and their containers.
    body("onPrimary/primary", primary, onPrimary)
    body("onSecondary/secondary", secondary, onSecondary)
    body("onTertiary/tertiary", tertiary, onTertiary)
    body("onError/error", error, onError)
    body("onPrimaryContainer/primaryContainer", primaryContainer, onPrimaryContainer)
    body("onSecondaryContainer/secondaryContainer", secondaryContainer, onSecondaryContainer)
    body("onTertiaryContainer/tertiaryContainer", tertiaryContainer, onTertiaryContainer)
    body("onErrorContainer/errorContainer", errorContainer, onErrorContainer)

    // Inverse surfaces (snackbars, tooltips).
    body("inverseOnSurface/inverseSurface", inverseSurface, inverseOnSurface)
    body("inversePrimary/inverseSurface", inverseSurface, inversePrimary)

    // Text on every surface fill.
    for ((fillName, fill) in surfaceFills()) {
        body("onSurface/$fillName", fill, onSurface)
        body("onSurfaceVariant/$fillName", fill, onSurfaceVariant)
        icon("outline/$fillName", fill, outline)
    }
    body("onBackground/background", background, onBackground)

    // Accent text on the screen-filling surfaces (links, labels, error lines): body size.
    for ((fillName, fill) in listOf("surface" to surface, "background" to background)) {
        body("primary/$fillName", fill, primary)
        body("secondary/$fillName", fill, secondary)
        body("tertiary/$fillName", fill, tertiary)
        body("error/$fillName", fill, error)
    }
    // Accent icons on raised tiers: M3 only guarantees tone 40 vs tone 90 at 3:1.
    for ((fillName, fill) in surfaceTiers()) {
        icon("primary/$fillName", fill, primary)
        icon("secondary/$fillName", fill, secondary)
        icon("tertiary/$fillName", fill, tertiary)
        icon("error/$fillName", fill, error)
    }
}

/**
 * Luminance ratio of each surface-container tier over the tier below it (lowest over
 * `surface`), so a test can require the ramp to keep visible steps under pitch black.
 */
fun ColorScheme.surfaceTierSteps(): List<Pair<String, Double>> {
    val tiers = listOf("surface" to surface) + surfaceTiers()
    return tiers.zipWithNext { (lowerName, lower), (upperName, upper) ->
        "$upperName over $lowerName" to ThemeContrast.contrastRatio(upper, lower)
    }
}
