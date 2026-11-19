package com.materialchat.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The size ladder for one control class across every surface (W4 audit F4 / F13). One value per
 * class so a chip, a touch target or a leading tile is the same height whoever built it; the
 * assistant's `AssistantSizes` and the chat/home sites alias these where the value already
 * matches and migrate where it does not (W5 REPORT.md migration table). Pinned in
 * `ExpressiveDimensionsTest`.
 */
object ExpressiveSizes {
    /** The "pill with a leading glyph" class: assist/filter/suggestion chips, labelled chips, compact badges. */
    object Chip {
        /** Chip row height (M3 assist/filter chip containers are 32dp; the app's touch-friendly chips are 40). */
        val Height: Dp = 40.dp

        /** Leading glyph inside a chip (M3 chip icon size). */
        val Icon: Dp = 18.dp

        /** Horizontal inset from the chip edge to its first glyph or label. */
        val Inset: Dp = 14.dp
    }

    /** Minimum touch target (M3 accessibility, 48dp). */
    val Target: Dp = 48.dp

    /** Touch target of a dense row action (home row trailing icons, settings control rows). */
    val CompactTarget: Dp = 44.dp

    /** A standard inline control: icon button, inline tonal button, the assistant's control row. */
    val Control: Dp = 40.dp

    /** Compact control: a group-row chevron, a 32dp step badge, the assistant's compact target. */
    val Compact: Dp = 32.dp

    /** Leading tile on a list row ([ExpressiveShapes.Tile]). */
    val Tile: Dp = 48.dp

    /** Glyph inside a [Tile]. */
    val TileIcon: Dp = 26.dp

    /** Indeterminate indicator inline with text (a button, a row, a dropdown). */
    val IndicatorInline: Dp = 20.dp

    /** Indeterminate indicator that owns a screen or a card. */
    val IndicatorHero: Dp = 48.dp
}

/**
 * Screen-level spacing: the inset between a screen edge and its content, and between a
 * floating element (snackbar, notice) and the edge it floats off.
 */
object ExpressiveSpacing {
    /** Horizontal gutter of a screen's list or column content. */
    val Gutter: Dp = 16.dp

    /** Horizontal gutter of a modal bottom sheet's content (`ui/components/SheetGutter` aliases this). */
    val SheetGutter: Dp = 24.dp

    /** Inset of a floating notice or snackbar from the screen edges. */
    val Inset: Dp = 12.dp
}
