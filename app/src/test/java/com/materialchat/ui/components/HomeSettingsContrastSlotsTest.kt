package com.materialchat.ui.components

import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.ui.theme.ContrastSlotSet
import com.materialchat.ui.theme.MaterialChatThemePalettes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The home/settings palette speaks the theme's [ContrastSlotSet] vocabulary: the resolver names
 * the surface and the slot list is exactly the pair list the home gate already walks.
 */
class HomeSettingsContrastSlotsTest {

    @Test fun theResolverExposesEveryHomeSettingsSlotUnderTheSurfaceName() {
        for (palette in AppPreferences.ThemePalette.values()) {
            for (dark in listOf(false, true)) {
                val scheme = MaterialChatThemePalettes.colorScheme(palette, darkTheme = dark)
                val set: ContrastSlotSet = HomeSettingsContrastSlots.resolve(scheme)
                assertEquals("home-settings", set.surfaceName)
                val pairs = HomeSettingsPalette.resolve(scheme).pairs()
                assertEquals(pairs.size, set.slots.size)
                assertEquals(HomeSettingsSlot.values().size, set.slots.size)
                assertEquals(pairs.map { it.name }, set.slots.map { it.slotName })
                for ((pair, slot) in pairs.zip(set.slots)) {
                    assertEquals(pair.content, slot.content)
                    assertEquals(pair.container, slot.fill)
                    assertEquals(pair.minimum, slot.minimum, 0.0)
                }
                assertTrue("$palette dark=$dark", set.slots.all { it.passes })
            }
        }
    }
}
