package com.materialchat.assistant.ui

import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import com.materialchat.ui.theme.ContrastSlotSet
import com.materialchat.ui.theme.ExpressiveShapes
import com.materialchat.ui.theme.ExpressiveSizes
import com.materialchat.ui.theme.ExpressiveSpacing
import com.materialchat.ui.theme.ThemeContrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.unit.dp

/**
 * W5: the assistant palette speaks the theme's slot vocabulary, and the assistant's size and
 * spacing tokens are the theme's where the value matches (with the two documented deltas).
 */
class AssistantContrastSlotTest {

    @Test fun thePaletteIsTheAssistantSlotSetAndItsSlotsAreItsPairs() {
        for (scheme in listOf(lightColorScheme(), darkColorScheme())) {
            val palette = AssistantSurfacePalette.resolve(scheme)
            val set: ContrastSlotSet = palette
            assertEquals("assistant", set.surfaceName)
            assertEquals(AssistantSurfacePalette.SURFACE, set.surfaceName)
            val pairs = palette.pairs()
            assertEquals(pairs.size, set.slots.size)
            for ((slot, pair) in set.slots.zip(pairs)) {
                assertEquals(pair.name, slot.slotName)
                assertEquals(pair.container, slot.fill)
                assertEquals(pair.content, slot.content)
                assertEquals(pair.minimum, slot.minimum, 0.0)
                // The slot's default arithmetic and the assistant's agree exactly.
                assertEquals(ThemeContrast.contrastRatio(slot.content, slot.fill), slot.ratio, 1e-9)
                assertEquals(pair.passes, slot.passes)
            }
            assertTrue(set.slots.isNotEmpty())
        }
    }

    @Test fun sizesAndSpacingAliasTheThemeWhereTheValueMatches() {
        assertEquals(ExpressiveSizes.Chip.Height, AssistantSizes.ChipHeight)
        assertEquals(ExpressiveSizes.Chip.Inset, AssistantSpacing.ChipInset)
        assertEquals(ExpressiveSpacing.Inset, AssistantSpacing.Inset)
        assertEquals(ExpressiveSizes.Target, AssistantSizes.Target)
        assertEquals(ExpressiveSizes.Control, AssistantSizes.Control)
        // The two documented deltas (AssistantExpressiveStyle.kt): the chip icon stays 16 (theme
        // 18) and the compact remove target maps to the theme's 32dp Compact, not CompactTarget 44.
        assertEquals(16.dp, AssistantSizes.ChipIcon)
        assertNotEquals(ExpressiveSizes.Chip.Icon, AssistantSizes.ChipIcon)
        assertEquals(ExpressiveSizes.Compact, AssistantSizes.CompactTarget)
        assertNotEquals(ExpressiveSizes.CompactTarget, AssistantSizes.CompactTarget)
        // The chip shape is the app-wide capsule.
        assertSame(ExpressiveShapes.Chip, AssistantShapes.Chip)
    }
}
