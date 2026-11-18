package com.materialchat.assistant.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG 2.x contrast for the assistant window.
 *
 * The assistant paints several of its own fills (the feathered pill, the accent-tinted answer
 * surface, the thinking tone) and runs under every theme the app can produce: light or dark,
 * static palette or wallpaper-dynamic, pitch black or not. A content colour
 * taken from a theme role alone (`onSurface` because "this is a surface") is only right while
 * the fill really is that role's surface. Here every text and icon colour is chosen against the
 * colour(s) the fill can actually present: the preferred Material role is kept whenever it meets
 * the required ratio against every one of them, otherwise the next role in line is tried, and
 * pure white or black close the list so a readable colour always exists (for any opaque fill the
 * better of white/black is >= 4.58:1).
 *
 * Pure Kotlin: no composition, no Android, so the JVM test can enumerate themes.
 */
internal object AssistantContrast {
    /** WCAG AA for body-sized text. */
    const val BODY_TEXT = 4.5

    /** WCAG AA for icons, graphical objects and large text. */
    const val ICON = 3.0

    /** The window scrim drawn under every assistant surface (AssistantOverlay, `Color.Black` at 0.42). */
    const val SCRIM_ALPHA = 0.42f

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
     * Contrast ratio (1..21) between [foreground] drawn on [background]. A translucent foreground
     * is composited first; the background is taken as opaque (it is a painted fill).
     */
    fun contrastRatio(foreground: Color, background: Color): Double {
        val bg = opaque(background)
        val fg = compositeOver(foreground, bg)
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(bg)
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    }

    /** The lowest ratio [foreground] reaches against any of [fills]. */
    fun contrastRatio(foreground: Color, fills: List<Color>): Double =
        fills.minOf { contrastRatio(foreground, it) }

    /**
     * The first of [candidates] (in order of preference, pure white and black appended) that
     * reaches [minimum] against every one of [fills]; if none does, the candidate whose worst
     * ratio is highest.
     */
    fun pick(fills: List<Color>, candidates: List<Color>, minimum: Double): Color {
        require(fills.isNotEmpty()) { "a content colour needs at least one fill" }
        val all = candidates + listOf(Color.White, Color.Black)
        all.firstOrNull { contrastRatio(it, fills) >= minimum }?.let { return it }
        return all.maxByOrNull { contrastRatio(it, fills) } ?: Color.Black
    }

    fun pick(fill: Color, candidates: List<Color>, minimum: Double): Color =
        pick(listOf(fill), candidates, minimum)

    private fun opaque(color: Color): Color = if (color.alpha >= 1f) color else color.copy(alpha = 1f)

    private fun linear(channel: Float): Double {
        val v = channel.toDouble().coerceIn(0.0, 1.0)
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }
}

/** One container/content pair the assistant draws, with the ratio it must reach. */
internal data class AssistantContrastPair(
    val name: String,
    val container: Color,
    val content: Color,
    val minimum: Double
) {
    val ratio: Double get() = AssistantContrast.contrastRatio(content, container)
    val passes: Boolean get() = ratio >= minimum
}

/**
 * The colours a painted container can present. A `Surface` paints its colour, so the fill IS the
 * container colour; a translucent container is composited over the pill's own surface.
 */
internal fun assistantFills(container: Color, scheme: ColorScheme): List<Color> =
    listOf(
        if (container.alpha >= 1f) container
        else AssistantContrast.compositeOver(container, scheme.surfaceContainerHigh)
    )

/** A tonal container, the content colour resolved against it, and the fills it was checked on. */
internal data class AssistantTonal(val container: Color, val content: Color, val fills: List<Color>)

/** Content colours resolved against every fill one container can present. */
internal data class AssistantContentColors(
    /** The colour the container is painted with (what the `Surface` receives). */
    val container: Color,
    /** The colours the container can present on screen; every content colour reads on all of them. */
    val fills: List<Color>,
    /** Primary text and the icons that inherit `LocalContentColor` (>= 4.5:1). */
    val body: Color,
    /** Secondary text: prompts, status lines, notices (>= 4.5:1). */
    val muted: Color,
    /** Accent marks: the idle sparkle, the text cursor (>= 3:1, an icon). */
    val accent: Color,
    /** Error text on this fill (>= 4.5:1). */
    val error: Color,
    /** Decorative marks (the typing dots): a translucent muted tone when that still reads, else solid (>= 3:1). */
    val indicator: Color
) {
    /** The container colour itself; kept for callers that paint the fill. */
    val fill: Color get() = container

    companion object {
        fun on(container: Color, scheme: ColorScheme): AssistantContentColors {
            val fills = assistantFills(container, scheme)
            val body = AssistantContrast.pick(
                fills, listOf(scheme.onSurface, scheme.inverseOnSurface), AssistantContrast.BODY_TEXT
            )
            val muted = AssistantContrast.pick(
                fills, listOf(scheme.onSurfaceVariant, scheme.onSurface, scheme.inverseOnSurface),
                AssistantContrast.BODY_TEXT
            )
            return AssistantContentColors(
                container = container,
                fills = fills,
                body = body,
                muted = muted,
                accent = AssistantContrast.pick(
                    fills,
                    listOf(scheme.primary, scheme.onPrimaryContainer, scheme.onSurface, scheme.inverseOnSurface),
                    AssistantContrast.ICON
                ),
                error = AssistantContrast.pick(
                    fills, listOf(scheme.error, scheme.onErrorContainer, scheme.onSurface, scheme.inverseOnSurface),
                    AssistantContrast.BODY_TEXT
                ),
                indicator = AssistantContrast.pick(
                    fills, listOf(muted.copy(alpha = 0.6f), muted, body), AssistantContrast.ICON
                )
            )
        }
    }
}

/**
 * Every fill the assistant window paints and the content colour resolved for each, from one
 * [ColorScheme]. The overlay reads colours from here instead of from theme
 * roles; [pairs] lists the same values for the contrast test, so the test and the UI cannot
 * drift apart.
 */
internal data class AssistantSurfacePalette(
    /** The 76dp pill: its feathered fill (or opaque surface) and the text/icons on it. */
    val pill: AssistantContentColors,
    /** The answer surface above the pill. */
    val answer: AssistantContentColors,
    /** The thinking surface inside the answer. */
    val thinking: AssistantContentColors,
    /** The action toolbar strip inside the answer surface (icon buttons inherit [AssistantContentColors.muted]). */
    val toolbar: AssistantContentColors,
    /** The one-line notice surface above the pill when there is no answer surface. */
    val notice: AssistantContentColors,
    /** The error variant of the notice surface. */
    val errorNotice: AssistantTonal,
    /** The user's question bubble. */
    val query: AssistantTonal,
    /** Tonal chips (Open in app, Take photo, Attach photo). */
    val chip: AssistantTonal,
    /** The legacy "Continue in App" chip: a translucent primary container on the answer surface. */
    val continueChip: AssistantTonal,
    /** Mic / Send: the bold primary button. */
    val primaryButton: AssistantTonal,
    /** Stop listening: the primary container button. */
    val stopButton: AssistantTonal,
    /** Interrupt: the tonal (secondary container) button. */
    val interruptButton: AssistantTonal,
    /** The secondary circle holding the thinking surface's loading indicator. */
    val thinkingBadge: AssistantTonal,
    /** The pill's leading glyph per phase; the idle container is transparent and sits on the pill fill. */
    val glyphs: Map<AssistantPhase, AssistantTonal>
) {
    fun glyph(phase: AssistantPhase): AssistantTonal = glyphs.getValue(phase)

    /** Every container/content pair with its required ratio, one per fill the container can present. */
    fun pairs(): List<AssistantContrastPair> = buildList {
        fun text(name: String, c: AssistantContentColors) {
            c.fills.forEachIndexed { i, fill ->
                val n = if (c.fills.size == 1) name else "$name[$i]"
                add(AssistantContrastPair("$n body", fill, c.body, AssistantContrast.BODY_TEXT))
                add(AssistantContrastPair("$n muted", fill, c.muted, AssistantContrast.BODY_TEXT))
                add(AssistantContrastPair("$n accent", fill, c.accent, AssistantContrast.ICON))
                add(AssistantContrastPair("$n error", fill, c.error, AssistantContrast.BODY_TEXT))
                add(AssistantContrastPair("$n indicator", fill, c.indicator, AssistantContrast.ICON))
            }
        }
        fun tonal(name: String, t: AssistantTonal, minimum: Double) {
            t.fills.forEachIndexed { i, fill ->
                val n = if (t.fills.size == 1) name else "$name[$i]"
                add(AssistantContrastPair(n, fill, t.content, minimum))
            }
        }
        text("pill", pill)
        text("answer", answer)
        text("thinking", thinking)
        text("toolbar", toolbar)
        text("notice", notice)
        tonal("error notice", errorNotice, AssistantContrast.BODY_TEXT)
        tonal("query bubble", query, AssistantContrast.BODY_TEXT)
        tonal("chip", chip, AssistantContrast.BODY_TEXT)
        tonal("continue chip", continueChip, AssistantContrast.BODY_TEXT)
        tonal("primary button", primaryButton, AssistantContrast.ICON)
        tonal("stop button", stopButton, AssistantContrast.ICON)
        tonal("interrupt button", interruptButton, AssistantContrast.ICON)
        tonal("thinking badge", thinkingBadge, AssistantContrast.ICON)
        for ((phase, glyph) in glyphs) tonal("glyph $phase", glyph, AssistantContrast.ICON)
    }

    companion object {
        fun resolve(scheme: ColorScheme): AssistantSurfacePalette {
            // Tonal containers are painted as plain backgrounds or Material buttons, so they are opaque.
            fun tonal(container: Color, preferred: Color, minimum: Double, over: Color? = null): AssistantTonal {
                val fills = if (over == null) assistantFills(container, scheme)
                else listOf(AssistantContrast.compositeOver(container, over))
                return AssistantTonal(
                    container,
                    AssistantContrast.pick(fills, listOf(preferred, scheme.onSurface, scheme.inverseOnSurface), minimum),
                    fills
                )
            }
            val pill = AssistantContentColors.on(AssistantColors.pillFill(scheme), scheme)
            val answer = AssistantContentColors.on(AssistantColors.answerSurface(scheme), scheme)
            val glyphs = AssistantPhase.values().associateWith { phase ->
                val container = AssistantColors.glyphContainer(scheme, phase)
                when (phase) {
                    // Transparent container: the sparkle sits on the pill's own fill(s).
                    AssistantPhase.Idle -> AssistantTonal(container, pill.accent, pill.fills)
                    AssistantPhase.Capturing -> tonal(container, scheme.onPrimaryContainer, AssistantContrast.ICON)
                    AssistantPhase.Thinking -> tonal(container, scheme.onSecondaryContainer, AssistantContrast.ICON)
                    AssistantPhase.Speaking -> tonal(container, scheme.onTertiaryContainer, AssistantContrast.ICON)
                }
            }
            return AssistantSurfacePalette(
                pill = pill,
                answer = answer,
                thinking = AssistantContentColors.on(AssistantColors.thinkingSurface(scheme), scheme),
                toolbar = AssistantContentColors.on(AssistantColors.toolbarSurface(scheme), scheme),
                notice = AssistantContentColors.on(scheme.surfaceContainerHigh, scheme),
                // The error notice and the question bubble are painted Surfaces too.
                errorNotice = AssistantTonal(
                    scheme.errorContainer,
                    AssistantContrast.pick(
                        assistantFills(scheme.errorContainer, scheme),
                        listOf(scheme.onErrorContainer, scheme.error, scheme.onSurface, scheme.inverseOnSurface),
                        AssistantContrast.BODY_TEXT
                    ),
                    assistantFills(scheme.errorContainer, scheme)
                ),
                query = AssistantTonal(
                    AssistantColors.querySurface(scheme),
                    AssistantContrast.pick(
                        assistantFills(AssistantColors.querySurface(scheme), scheme),
                        listOf(scheme.onPrimaryContainer, scheme.onSurface, scheme.inverseOnSurface),
                        AssistantContrast.BODY_TEXT
                    ),
                    assistantFills(AssistantColors.querySurface(scheme), scheme)
                ),
                chip = tonal(scheme.secondaryContainer, scheme.onSecondaryContainer, AssistantContrast.BODY_TEXT),
                continueChip = tonal(
                    scheme.primaryContainer.copy(alpha = 0.7f), scheme.onPrimaryContainer,
                    AssistantContrast.BODY_TEXT, over = answer.fills.first()
                ),
                primaryButton = tonal(scheme.primary, scheme.onPrimary, AssistantContrast.ICON),
                stopButton = tonal(scheme.primaryContainer, scheme.onPrimaryContainer, AssistantContrast.ICON),
                interruptButton = tonal(scheme.secondaryContainer, scheme.onSecondaryContainer, AssistantContrast.ICON),
                thinkingBadge = tonal(scheme.secondaryContainer, scheme.onSecondaryContainer, AssistantContrast.ICON),
                glyphs = glyphs
            )
        }
    }
}

/** The colour the pill's fill is painted with (feathered by the particle field, or its own surface). */
internal fun AssistantColors.pillFill(scheme: ColorScheme): Color = scheme.surfaceContainerHigh
