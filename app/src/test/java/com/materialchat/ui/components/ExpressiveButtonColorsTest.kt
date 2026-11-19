package com.materialchat.ui.components

import androidx.compose.ui.unit.dp
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.ui.theme.ExpressiveSizes
import com.materialchat.ui.theme.MaterialChatThemePalettes
import com.materialchat.ui.theme.ThemeContrast
import com.materialchat.ui.theme.withPitchBlackSurfaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * W8 part 2: the optional colour pair and size preset on `ExpressiveButton`. The defaults are
 * pinned so adding the parameters changed no existing call site; the tertiary helpers are the
 * scheme's own role pairs and meet body-text contrast in every static palette (light, dark,
 * pitch-black). Dynamic schemes are the system's choice and are reported by `ThemeContrastTest`.
 */
class ExpressiveButtonColorsTest {

    @Test fun defaultSizeIsTheLabelledButtonEveryCurrentSiteDraws() {
        assertEquals(56.dp, ExpressiveButtonSize.Default.minHeight)
        assertEquals(28.dp, ExpressiveButtonSize.Default.inset)
        assertEquals(24.dp, ExpressiveButtonSize.Default.textInset)
        assertEquals(ExpressiveSizes.Icon, ExpressiveButtonSize.Default.icon)
        assertEquals(20.dp, ExpressiveButtonSize.Default.icon)
    }

    @Test fun chipSizeIsTheChipClass() {
        assertEquals(ExpressiveSizes.Chip.Height, ExpressiveButtonSize.Chip.minHeight)
        assertEquals(ExpressiveSizes.Chip.Inset, ExpressiveButtonSize.Chip.inset)
        assertEquals(ExpressiveSizes.Chip.Inset, ExpressiveButtonSize.Chip.textInset)
        assertEquals(ExpressiveSizes.Chip.Icon, ExpressiveButtonSize.Chip.icon)
        assertTrue(ExpressiveButtonSize.Chip.minHeight < ExpressiveButtonSize.Default.minHeight)
    }

    @Test fun tertiaryPairsAreTheSchemeRolesAndReadableInEveryStaticPalette() {
        for (palette in AppPreferences.ThemePalette.values()) {
            val light = MaterialChatThemePalettes.colorScheme(palette, darkTheme = false)
            val dark = MaterialChatThemePalettes.colorScheme(palette, darkTheme = true)
            for ((name, scheme) in listOf("$palette light" to light, "$palette dark" to dark, "$palette pitch-black" to dark.withPitchBlackSurfaces())) {
                val filled = ExpressiveButtonColors.tertiary(scheme)
                assertEquals(name, scheme.tertiary, filled.container)
                assertEquals(name, scheme.onTertiary, filled.content)
                val ratio = ThemeContrast.contrastRatio(filled.content, filled.container)
                assertTrue("$name tertiary ${"%.2f".format(ratio)}", ratio >= ThemeContrast.BODY_TEXT)

                val tonal = ExpressiveButtonColors.tertiaryTonal(scheme)
                assertEquals(name, scheme.tertiaryContainer, tonal.container)
                assertEquals(name, scheme.onTertiaryContainer, tonal.content)
                val tonalRatio = ThemeContrast.contrastRatio(tonal.content, tonal.container)
                assertTrue("$name tertiaryTonal ${"%.2f".format(tonalRatio)}", tonalRatio >= ThemeContrast.BODY_TEXT)
            }
        }
    }
}
