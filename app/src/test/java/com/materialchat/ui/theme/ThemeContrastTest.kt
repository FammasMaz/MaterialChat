package com.materialchat.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.materialchat.data.local.preferences.AppPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * The theme-level contrast gate: every standard Material 3 role pair in every palette the app
 * ships, in light, dark and pitch-black dark, must meet WCAG AA (4.5:1 body text, 3:1 icons
 * and outlines). Three wallpaper-dynamic-shaped schemes are enumerated too so the pitch-black
 * ramp is checked on hues the static palettes do not cover; dynamic accent pairs are reported
 * but not asserted because the system, not the app, chooses them.
 */
class ThemeContrastTest {

    private data class Theme(val name: String, val scheme: ColorScheme, val static: Boolean)

    private val themes: List<Theme> by lazy {
        buildList {
            for (palette in AppPreferences.ThemePalette.values()) {
                add(Theme("$palette light", MaterialChatThemePalettes.colorScheme(palette, darkTheme = false), true))
                val dark = MaterialChatThemePalettes.colorScheme(palette, darkTheme = true)
                add(Theme("$palette dark", dark, true))
                add(Theme("$palette pitch-black", dark.withPitchBlackSurfaces(), true))
            }
            for ((seedName, seed) in listOf("blue" to seedBlue, "green" to seedGreen, "amber" to seedAmber)) {
                add(Theme("dynamic $seedName light", dynamicLike(seed, dark = false), false))
                val dark = dynamicLike(seed, dark = true)
                add(Theme("dynamic $seedName dark", dark, false))
                add(Theme("dynamic $seedName pitch-black", dark.withPitchBlackSurfaces(), false))
            }
        }
    }

    @Test fun themeMatrixCoversEveryAppearance() {
        assertEquals(AppPreferences.ThemePalette.values().size * 3 + 9, themes.size)
        assertEquals(themes.size, themes.map { it.name }.distinct().size)
    }

    @Test fun contrastRatioMatchesWcagReferenceValues() {
        assertEquals(21.0, ThemeContrast.contrastRatio(Color.White, Color.Black), 1e-6)
        assertEquals(1.0, ThemeContrast.contrastRatio(Color.White, Color.White), 1e-6)
        assertEquals(4.48, ThemeContrast.contrastRatio(Color(0xFF777777), Color.White), 0.01)
        assertEquals(4.48, ThemeContrast.contrastRatio(Color.White, Color(0xFF777777)), 0.01)
    }

    @Test fun everyPaletteColourIsOpaque() {
        // A missing alpha byte (Color(0xAA2F96)) silently yields a transparent role.
        val failures = buildList {
            for (theme in themes.filter { it.static }) {
                for ((role, color) in theme.scheme.roles()) if (color.alpha < 1f) add("${theme.name}: $role alpha=${color.alpha}")
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun everyStandardPairMeetsWcagAaInEveryStaticPalette() {
        val failures = buildList {
            for (theme in themes.filter { it.static }) {
                for (pair in theme.scheme.standardContrastPairs()) {
                    if (!pair.passes) add("${theme.name}: ${pair.name} ${pair.ratio.fmt()} < ${pair.minimum}")
                }
            }
        }
        assertTrue("${failures.size} pair(s) below WCAG AA:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun dynamicSchemesKeepTheSurfaceGuaranteesTheAppControls() {
        // Under dynamic colour the app only owns the pitch-black ramp; the surface text pairs
        // must still hold after it is applied. Accent pairs are the system's.
        val failures = buildList {
            for (theme in themes.filterNot { it.static }) {
                for (pair in theme.scheme.standardContrastPairs()) {
                    val surfacePair = pair.name.startsWith("onSurface") || pair.name.startsWith("outline/") ||
                        pair.name.startsWith("onBackground")
                    if (surfacePair && !pair.passes) add("${theme.name}: ${pair.name} ${pair.ratio.fmt()} < ${pair.minimum}")
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun pitchBlackKeepsScreenFillingSurfacesBlackAndTiersSeparated() {
        for (theme in themes.filter { it.name.endsWith("pitch-black") }) {
            val s = theme.scheme
            assertEquals(theme.name, Color.Black, s.surface)
            assertEquals(theme.name, Color.Black, s.background)
            assertEquals(theme.name, Color.Black, s.surfaceDim)
            assertEquals(theme.name, Color.Black, s.surfaceContainerLowest)
            // Every tier above the black lowest must be at least one visible step above the one below.
            for ((stepName, ratio) in s.surfaceTierSteps().drop(1)) {
                assertTrue("${theme.name}: $stepName ${ratio.fmt()} < ${ThemeContrast.TIER_STEP}", ratio >= ThemeContrast.TIER_STEP)
            }
            // The raised tiers keep the palette's own tone: untouched on every shipped palette, and
            // on a wallpaper-dynamic ramp only ever lifted (never darkened) when High is too dark
            // to fit two visible steps between it and black.
            val base = themes.first { it.name == theme.name.replace("pitch-black", "dark") }.scheme
            if (theme.static) {
                assertEquals(theme.name, base.surfaceContainerHigh, s.surfaceContainerHigh)
                assertEquals(theme.name, base.surfaceContainerHighest, s.surfaceContainerHighest)
            } else {
                assertTrue(theme.name, ThemeContrast.relativeLuminance(s.surfaceContainerHigh) >= ThemeContrast.relativeLuminance(base.surfaceContainerHigh))
                assertTrue(theme.name, ThemeContrast.relativeLuminance(s.surfaceContainerHighest) >= ThemeContrast.relativeLuminance(base.surfaceContainerHighest))
            }
        }
    }

    @Test fun pitchBlackLowTiersStayDarkerThanHigh() {
        for (theme in themes.filter { it.name.endsWith("pitch-black") }) {
            val s = theme.scheme
            val low = ThemeContrast.relativeLuminance(s.surfaceContainerLow)
            val mid = ThemeContrast.relativeLuminance(s.surfaceContainer)
            val high = ThemeContrast.relativeLuminance(s.surfaceContainerHigh)
            assertTrue(theme.name, low < mid && mid < high)
        }
    }

    @Test fun readableOnKeepsThePreferredRoleWhenItReadsAndFallsBackOtherwise() {
        val scheme = MaterialChatThemePalettes.colorScheme(AppPreferences.ThemePalette.VIOLET, darkTheme = false)
        assertEquals(scheme.onSurface, scheme.readableOn(scheme.surface))
        assertEquals(scheme.onSurfaceVariant, scheme.readableMutedOn(scheme.surfaceContainer))
        assertEquals(scheme.primary, scheme.readableAccentOn(scheme.surfaceContainerHigh))
        // A dark fill under a light scheme: onSurface (dark) no longer reads, inverseOnSurface does.
        val picked = scheme.readableOn(Color(0xFF202020))
        assertTrue(ThemeContrast.contrastRatio(picked, Color(0xFF202020)) >= ThemeContrast.BODY_TEXT)
        assertEquals(scheme.inverseOnSurface, picked)
        // Mid grey: nothing in the scheme reads at 4.5; white/black close the list.
        val mid = Color(0xFF808080)
        val onMid = scheme.readableOn(mid)
        assertTrue(onMid == Color.White || onMid == Color.Black)
    }

    @Test fun themeAndAssistantContrastAgree() {
        // The assistant keeps a private copy of the arithmetic; the two must not drift.
        val samples = listOf(
            Color(0xFF6C4FF5) to Color(0xFFFCF8FF),
            Color(0xFFD2C2FF) to Color(0xFF15101D),
            Color(0x99FFFFFF) to Color(0xFF30263D)
        )
        for ((fg, bg) in samples) {
            assertEquals(
                com.materialchat.assistant.ui.AssistantContrast.contrastRatio(fg, bg),
                ThemeContrast.contrastRatio(fg, bg),
                1e-9
            )
        }
    }

    @Test fun printContrastTable() {
        // Human-readable table for REPORT.md; never fails.
        val sb = StringBuilder()
        for (theme in themes) {
            val pairs = theme.scheme.standardContrastPairs()
            val worst = pairs.minByOrNull { it.ratio / it.minimum }!!
            val steps = theme.scheme.surfaceTierSteps().joinToString(" ") { it.second.fmt() }
            sb.append("${theme.name} | worst ${worst.name} ${worst.ratio.fmt()} (min ${worst.minimum}) | tier steps $steps\n")
        }
        println("CONTRAST TABLE\n$sb")
    }

    // --- helpers -----------------------------------------------------------------------------

    private fun Double.fmt() = String.format(Locale.US, "%.2f", this)

    private fun ColorScheme.roles(): List<Pair<String, Color>> = listOf(
        "primary" to primary, "onPrimary" to onPrimary, "primaryContainer" to primaryContainer,
        "onPrimaryContainer" to onPrimaryContainer, "secondary" to secondary, "onSecondary" to onSecondary,
        "secondaryContainer" to secondaryContainer, "onSecondaryContainer" to onSecondaryContainer,
        "tertiary" to tertiary, "onTertiary" to onTertiary, "tertiaryContainer" to tertiaryContainer,
        "onTertiaryContainer" to onTertiaryContainer, "error" to error, "onError" to onError,
        "errorContainer" to errorContainer, "onErrorContainer" to onErrorContainer,
        "background" to background, "onBackground" to onBackground, "surface" to surface,
        "onSurface" to onSurface, "surfaceVariant" to surfaceVariant, "onSurfaceVariant" to onSurfaceVariant,
        "outline" to outline, "outlineVariant" to outlineVariant, "inverseSurface" to inverseSurface,
        "inverseOnSurface" to inverseOnSurface, "inversePrimary" to inversePrimary,
        "surfaceDim" to surfaceDim, "surfaceBright" to surfaceBright
    ) + surfaceTiers()

    private data class Seed(val primary: Color, val secondary: Color, val tertiary: Color, val neutral: Color)

    private val seedBlue = Seed(Color(0xFF0B57D0), Color(0xFF535F70), Color(0xFF6B5778), Color(0xFF5F6368))
    private val seedGreen = Seed(Color(0xFF2E6B3A), Color(0xFF52634F), Color(0xFF38656A), Color(0xFF5E615A))
    private val seedAmber = Seed(Color(0xFF7A5A00), Color(0xFF6C5D3F), Color(0xFF4D6544), Color(0xFF66605A))

    /** Same construction as AssistantContrastTest.dynamicLike so the two gates see the same shapes. */
    private fun dynamicLike(seed: Seed, dark: Boolean): ColorScheme {
        fun tone(c: Color, t: Float): Color = if (t >= 0f) lerp(c, Color.White, t) else lerp(c, Color.Black, -t)
        return if (!dark) lightColorScheme(
            primary = seed.primary, onPrimary = Color.White,
            primaryContainer = tone(seed.primary, 0.82f), onPrimaryContainer = tone(seed.primary, -0.7f),
            secondary = seed.secondary, onSecondary = Color.White,
            secondaryContainer = tone(seed.secondary, 0.8f), onSecondaryContainer = tone(seed.secondary, -0.7f),
            tertiary = seed.tertiary, onTertiary = Color.White,
            tertiaryContainer = tone(seed.tertiary, 0.82f), onTertiaryContainer = tone(seed.tertiary, -0.7f),
            background = tone(seed.neutral, 0.96f), onBackground = tone(seed.neutral, -0.8f),
            surface = tone(seed.neutral, 0.96f), onSurface = tone(seed.neutral, -0.8f),
            surfaceVariant = tone(seed.neutral, 0.78f), onSurfaceVariant = tone(seed.neutral, -0.35f),
            outline = tone(seed.neutral, -0.1f), outlineVariant = tone(seed.neutral, 0.6f),
            inverseSurface = tone(seed.neutral, -0.75f), inverseOnSurface = tone(seed.neutral, 0.9f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = tone(seed.neutral, 0.93f),
            surfaceContainer = tone(seed.neutral, 0.9f),
            surfaceContainerHigh = tone(seed.neutral, 0.86f),
            surfaceContainerHighest = tone(seed.neutral, 0.82f),
            surfaceDim = tone(seed.neutral, 0.78f), surfaceBright = tone(seed.neutral, 0.96f)
        ) else darkColorScheme(
            primary = tone(seed.primary, 0.6f), onPrimary = tone(seed.primary, -0.55f),
            primaryContainer = tone(seed.primary, -0.3f), onPrimaryContainer = tone(seed.primary, 0.82f),
            secondary = tone(seed.secondary, 0.6f), onSecondary = tone(seed.secondary, -0.55f),
            secondaryContainer = tone(seed.secondary, -0.3f), onSecondaryContainer = tone(seed.secondary, 0.8f),
            tertiary = tone(seed.tertiary, 0.6f), onTertiary = tone(seed.tertiary, -0.55f),
            tertiaryContainer = tone(seed.tertiary, -0.3f), onTertiaryContainer = tone(seed.tertiary, 0.82f),
            background = tone(seed.neutral, -0.86f), onBackground = tone(seed.neutral, 0.82f),
            surface = tone(seed.neutral, -0.86f), onSurface = tone(seed.neutral, 0.82f),
            surfaceVariant = tone(seed.neutral, -0.3f), onSurfaceVariant = tone(seed.neutral, 0.55f),
            outline = tone(seed.neutral, 0.3f), outlineVariant = tone(seed.neutral, -0.3f),
            inverseSurface = tone(seed.neutral, 0.82f), inverseOnSurface = tone(seed.neutral, -0.7f),
            surfaceContainerLowest = tone(seed.neutral, -0.92f),
            surfaceContainerLow = tone(seed.neutral, -0.8f),
            surfaceContainer = tone(seed.neutral, -0.74f),
            surfaceContainerHigh = tone(seed.neutral, -0.66f),
            surfaceContainerHighest = tone(seed.neutral, -0.58f),
            surfaceDim = tone(seed.neutral, -0.86f), surfaceBright = tone(seed.neutral, -0.5f)
        )
    }
}
