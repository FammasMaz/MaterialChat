package com.materialchat.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * One named fill/content pair a surface paints, with the WCAG ratio it must reach. The one
 * vocabulary the three surface palettes (`HomeSettingsSlot`, `ChatSurfacePalette`,
 * `AssistantSurfacePalette`) and the theme's own [ThemeContrastPair] share, so the JVM contrast
 * gate (`ThemeContrastTest`) can enumerate every slot in the app through one type instead of
 * three private pair classes.
 *
 * A slot is a resolved value for one [ColorScheme]: [fill] is the colour actually painted under
 * [content] (a composited or lerped fill, not a role name), and [minimum] is
 * [ThemeContrast.BODY_TEXT] or [ThemeContrast.ICON].
 */
interface ContrastSlot {
    /** Stable, human-readable slot name (`"SnackbarAction"`, `"error notice"`, `"screenError"`). */
    val slotName: String

    /** The fill the content is drawn on, as painted. */
    val fill: Color

    /** The content colour drawn on [fill]. */
    val content: Color

    /** The WCAG ratio this slot must reach ([ThemeContrast.BODY_TEXT] or [ThemeContrast.ICON]). */
    val minimum: Double

    /** Contrast ratio of [content] on [fill]. */
    val ratio: Double get() = ThemeContrast.contrastRatio(content, fill)

    /** Whether [ratio] reaches [minimum]. */
    val passes: Boolean get() = ratio >= minimum
}

/** A [ContrastSlot] as the theme's own pair type. */
fun ContrastSlot.toThemeContrastPair(): ThemeContrastPair =
    this as? ThemeContrastPair ?: ThemeContrastPair(slotName, fill, content, minimum)

/**
 * Every slot one surface paints for one [ColorScheme]: what a resolved palette exposes so the
 * gate can walk it. A palette implements this on its resolved instance (`pairs()` becomes
 * [slots]); its resolver implements [ContrastSlotResolver].
 */
interface ContrastSlotSet {
    /** The surface the slots belong to (`"home-settings"`, `"chat"`, `"assistant"`). */
    val surfaceName: String

    /** Every fill/content pair the surface draws, resolved for one scheme. */
    val slots: List<ContrastSlot>
}

/**
 * Resolves a surface's [ContrastSlotSet] for a [ColorScheme]. The contrast gate holds one of
 * these per surface and crosses it with every appearance the app ships.
 */
fun interface ContrastSlotResolver {
    fun resolve(scheme: ColorScheme): ContrastSlotSet
}

/** A [ContrastSlotSet] built from a list, for palettes that already expose a pair list. */
class ContrastSlotList(
    override val surfaceName: String,
    override val slots: List<ContrastSlot>
) : ContrastSlotSet

/** The theme foundation's own slot set: [standardContrastPairs] plus [componentContrastPairs]. */
object ThemeContrastSlots : ContrastSlotResolver {
    const val SURFACE = "theme"

    override fun resolve(scheme: ColorScheme): ContrastSlotSet =
        ContrastSlotList(SURFACE, scheme.standardContrastPairs() + scheme.componentContrastPairs())
}
