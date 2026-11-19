package com.materialchat.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every style in the type scale must declare a weight its font family actually ships. A
 * weight the family lacks is silently snapped to the nearest instance (W1 found headlineSmall
 * declared SemiBold on a Bold-only family), so the declaration and the drawn glyphs drift.
 * Also pins that nothing in the scale slants (FontStyle.Italic is banned app-wide).
 */
class TypographyWeightsTest {

    private fun Typography.styles(): List<Pair<String, TextStyle>> = listOf(
        "displayLarge" to displayLarge, "displayMedium" to displayMedium, "displaySmall" to displaySmall,
        "headlineLarge" to headlineLarge, "headlineMedium" to headlineMedium, "headlineSmall" to headlineSmall,
        "titleLarge" to titleLarge, "titleMedium" to titleMedium, "titleSmall" to titleSmall,
        "bodyLarge" to bodyLarge, "bodyMedium" to bodyMedium, "bodySmall" to bodySmall,
        "labelLarge" to labelLarge, "labelMedium" to labelMedium, "labelSmall" to labelSmall
    )

    private fun FontFamily.shippedWeights(): Set<FontWeight> =
        (this as FontListFontFamily).fonts.map { it.weight }.toSet()

    private fun weightFailures(name: String, typography: Typography): List<String> = buildList {
        for ((styleName, style) in typography.styles()) {
            val family = style.fontFamily
            if (family !is FontListFontFamily) {
                add("$name.$styleName: family is not a FontListFontFamily ($family)")
                continue
            }
            val weight = style.fontWeight
            if (weight == null) {
                add("$name.$styleName: no weight declared")
            } else if (weight !in family.shippedWeights()) {
                add("$name.$styleName: declared $weight, family ships ${family.shippedWeights().map { it.weight }.sorted()}")
            }
        }
    }

    @Test fun defaultScaleDeclaresOnlyShippedWeights() {
        val failures = weightFailures("default", buildTypography())
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun everyUserFontFamilyDeclaresOnlyShippedWeights() {
        val failures = buildList {
            for (font in availableFonts) addAll(weightFailures(font.name, buildTypography(createFontFamily(font.googleFontName))))
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun expressiveTitleFamilyShipsExactlyOneBoldInstance() {
        // Display and headline tiers are drawn with it, so they must all declare Bold.
        assertEquals(setOf(FontWeight.Bold), MaterialChatExpressiveTitleFontFamily.shippedWeights())
        for ((styleName, style) in buildTypography().styles().take(6)) {
            assertEquals(styleName, MaterialChatExpressiveTitleFontFamily, style.fontFamily)
            assertEquals(styleName, FontWeight.Bold, style.fontWeight)
        }
    }

    @Test fun roundedBodyFamilyShipsLightThroughBold() {
        assertEquals(
            setOf(FontWeight.Light, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold),
            MaterialChatRoundedFontFamily.shippedWeights()
        )
    }

    @Test fun timestampStyleDeclaresAShippedWeight() {
        assertTrue(TimestampTypography.fontWeight in (TimestampTypography.fontFamily as FontFamily).shippedWeights())
    }

    @Test fun nothingInTheScaleIsItalic() {
        for ((styleName, style) in buildTypography().styles()) {
            assertTrue(styleName, style.fontStyle != FontStyle.Italic)
        }
        for (style in listOf(CodeTypography, InlineCodeTypography, TimestampTypography)) {
            assertTrue(style.fontStyle != FontStyle.Italic)
        }
    }
}
