package com.materialchat.ui.components

import androidx.compose.ui.unit.dp
import com.materialchat.ui.theme.ExpressiveSizes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * W8 part 3 (audit F4): the chip family's fixed values. Height is the chip class token, the
 * resting corner is half of it (a capsule at exactly that height), the pressed corner is the
 * 12dp the chat memory chip and the assistant chip already press to, and the glyph is the
 * chip icon token.
 */
class ExpressiveChipsTest {

    @Test fun chipFamilyIsTheChipClass() {
        assertEquals(ExpressiveSizes.Chip.Height, ExpressiveChipDefaults.Height)
        assertEquals(40.dp, ExpressiveChipDefaults.Height)
        assertEquals(ExpressiveSizes.Chip.Icon, ExpressiveChipDefaults.IconSize)
    }

    @Test fun restingCornerIsACapsuleAtTheChipHeightAndThePressedCornerIsTwelve() {
        assertEquals(ExpressiveChipDefaults.Height / 2, ExpressiveChipDefaults.RestingRadius)
        assertEquals(20.dp, ExpressiveChipDefaults.RestingRadius)
        assertEquals(12.dp, ExpressiveChipDefaults.PressedRadius)
        assertTrue(ExpressiveChipDefaults.PressedRadius < ExpressiveChipDefaults.RestingRadius)
        assertEquals(0.95f, ExpressiveChipDefaults.PressedScale, 0f)
    }
}
