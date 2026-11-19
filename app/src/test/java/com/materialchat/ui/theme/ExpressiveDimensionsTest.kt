package com.materialchat.ui.theme

import androidx.compose.ui.unit.dp
import com.materialchat.ui.components.SheetGutter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the W5 size and spacing ladder (audit F4 / F13) so a surface that aliases a value cannot
 * be silently retuned from the theme side, and the theme cannot drift from what the surfaces
 * were measured at.
 */
class ExpressiveDimensionsTest {

    @Test fun chipClassIsFortyEighteenFourteen() {
        assertEquals(40.dp, ExpressiveSizes.Chip.Height)
        assertEquals(18.dp, ExpressiveSizes.Chip.Icon)
        assertEquals(14.dp, ExpressiveSizes.Chip.Inset)
        // A chip is a Control-height row, not a touch target of its own.
        assertEquals(ExpressiveSizes.Control, ExpressiveSizes.Chip.Height)
    }

    @Test fun controlLadderIsPinned() {
        assertEquals(48.dp, ExpressiveSizes.Target)
        assertEquals(44.dp, ExpressiveSizes.CompactTarget)
        assertEquals(40.dp, ExpressiveSizes.Control)
        assertEquals(32.dp, ExpressiveSizes.Compact)
        assertTrue(ExpressiveSizes.Target > ExpressiveSizes.CompactTarget)
        assertTrue(ExpressiveSizes.CompactTarget > ExpressiveSizes.Control)
        assertTrue(ExpressiveSizes.Control > ExpressiveSizes.Compact)
    }

    @Test fun tileAndItsGlyphArePinned() {
        assertEquals(48.dp, ExpressiveSizes.Tile)
        assertEquals(26.dp, ExpressiveSizes.TileIcon)
        assertEquals(ExpressiveSizes.Target, ExpressiveSizes.Tile)
    }

    @Test fun iconLadderIsEighteenTwentyTwentyFour() {
        // W7 part 4 (audit F11): ExpressiveButton draws Icon (labelled / 48dp icon button) and
        // IconLarge (filled icon button, FAB); chips draw IconSmall through Chip.Icon.
        assertEquals(18.dp, ExpressiveSizes.IconSmall)
        assertEquals(20.dp, ExpressiveSizes.Icon)
        assertEquals(24.dp, ExpressiveSizes.IconLarge)
        assertEquals(ExpressiveSizes.IconSmall, ExpressiveSizes.Chip.Icon)
        assertTrue(ExpressiveSizes.IconSmall < ExpressiveSizes.Icon)
        assertTrue(ExpressiveSizes.Icon < ExpressiveSizes.IconLarge)
        // Icon is the inline-indicator size, so a button swapping its glyph for a spinner keeps its width.
        assertEquals(ExpressiveSizes.IndicatorInline, ExpressiveSizes.Icon)
    }

    @Test fun indicatorSizesArePinned() {
        assertEquals(20.dp, ExpressiveSizes.IndicatorInline)
        assertEquals(48.dp, ExpressiveSizes.IndicatorHero)
        assertEquals(ExpressiveSizes.Target, ExpressiveSizes.IndicatorHero)
    }

    @Test fun spacingIsPinnedAndTheSheetGutterAliasesTheToken() {
        assertEquals(16.dp, ExpressiveSpacing.Gutter)
        assertEquals(24.dp, ExpressiveSpacing.SheetGutter)
        assertEquals(12.dp, ExpressiveSpacing.Inset)
        assertEquals(ExpressiveSpacing.SheetGutter, SheetGutter)
    }
}
