package com.materialchat.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.materialchat.assistant.ui.AssistantContrast
import com.materialchat.assistant.ui.AssistantSurfacePalette
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

    @Test fun pickOnOneFillAndOnASingletonListAgree() {
        for (theme in themes) {
            val s = theme.scheme
            for ((_, fill) in s.surfaceFills()) {
                val candidates = listOf(s.onSurfaceVariant, s.onSurface, s.inverseOnSurface)
                assertEquals(
                    "${theme.name}",
                    ThemeContrast.pick(fill, candidates, ThemeContrast.BODY_TEXT),
                    ThemeContrast.pick(listOf(fill), candidates, ThemeContrast.BODY_TEXT)
                )
            }
        }
    }

    @Test fun pickOnSeveralFillsRequiresTheCandidateToReadOnEveryOne() {
        val scheme = MaterialChatThemePalettes.colorScheme(AppPreferences.ThemePalette.VIOLET, darkTheme = false)
        // onSurface reads on every light tier, so a list of tiers keeps it (no needless repaint).
        val tiers = scheme.surfaceTiers().map { it.second }
        assertEquals(scheme.onSurface, ThemeContrast.pick(tiers, listOf(scheme.onSurface), ThemeContrast.BODY_TEXT))
        assertTrue(ThemeContrast.contrastRatio(scheme.onSurface, tiers) >= ThemeContrast.BODY_TEXT)
        // The worst-of-fills number is what the list is judged by.
        assertEquals(
            tiers.minOf { ThemeContrast.contrastRatio(scheme.onSurface, it) },
            ThemeContrast.contrastRatio(scheme.onSurface, tiers),
            1e-12
        )
        // Add a dark fill: onSurface (dark) no longer reads on all of them and is skipped even
        // though it reads on the first; inverseOnSurface fails on the light tiers; nothing meets
        // 4.5 on both tones, so the least-bad candidate (black: ~1.3 worst, vs ~1.0 for the rest) wins.
        val mixed = listOf(scheme.surface, Color(0xFF202020))
        val picked = ThemeContrast.pick(mixed, listOf(scheme.onSurface, scheme.inverseOnSurface), ThemeContrast.BODY_TEXT)
        assertTrue(picked != scheme.onSurface)
        assertEquals(scheme.onSurface, ThemeContrast.pick(listOf(scheme.surface), listOf(scheme.onSurface), ThemeContrast.BODY_TEXT))
        val all = listOf(scheme.onSurface, scheme.inverseOnSurface, Color.White, Color.Black)
        assertEquals(all.maxByOrNull { ThemeContrast.contrastRatio(it, mixed) }, picked)
        // Order of preference is honoured when more than one candidate reads on every fill.
        val containers = listOf(scheme.surfaceContainer, scheme.surfaceContainerHighest)
        assertEquals(
            scheme.onSurfaceVariant,
            ThemeContrast.pick(containers, listOf(scheme.onSurfaceVariant, scheme.onSurface), ThemeContrast.BODY_TEXT)
        )
    }

    @Test fun pickMatchesHandComputedExpectations() {
        // Fixed colours with known WCAG ratios, so these numbers come from the spec, not from
        // the implementation under test. #777777 is the classic 4.48:1-on-white near miss.
        val grey = Color(0xFF777777)
        assertEquals(4.48, ThemeContrast.contrastRatio(grey, Color.White), 0.01)
        assertEquals(4.68, ThemeContrast.contrastRatio(grey, Color.Black), 0.01)

        // Single fill: the preferred candidate is skipped at 4.5 (4.48 < 4.5) and kept at 3.0.
        assertEquals(Color.Black, ThemeContrast.pick(Color.White, listOf(grey), ThemeContrast.BODY_TEXT))
        assertEquals(grey, ThemeContrast.pick(Color.White, listOf(grey), ThemeContrast.ICON))
        assertEquals(grey, ThemeContrast.pick(Color.Black, listOf(grey), ThemeContrast.BODY_TEXT))

        // Two fills of opposite tone: white and black each read on one fill only (21 vs 1), so
        // neither reaches 4.5 on both; grey's worst is 4.48, which also misses, and it wins as
        // the least-bad candidate. At 3.0 it reads on both and is simply chosen.
        val opposite = listOf(Color.White, Color.Black)
        assertEquals(4.48, ThemeContrast.contrastRatio(grey, opposite), 0.01)
        assertEquals(1.0, ThemeContrast.contrastRatio(Color.White, opposite), 1e-9)
        assertEquals(1.0, ThemeContrast.contrastRatio(Color.Black, opposite), 1e-9)
        assertEquals(grey, ThemeContrast.pick(opposite, listOf(grey), ThemeContrast.BODY_TEXT))
        assertEquals(grey, ThemeContrast.pick(opposite, listOf(grey), ThemeContrast.ICON))
        // With no candidate at all, the appended white/black tie at 1.0 and the first wins.
        assertEquals(Color.White, ThemeContrast.pick(opposite, emptyList(), ThemeContrast.ICON))

        // A translucent candidate is composited on each fill before it is judged: 60 % black on
        // white is #666666 (5.74:1), on black it is black (1:1), so it fails the pair at 3.0.
        val translucent = Color.Black.copy(alpha = 0.6f)
        assertEquals(Color(0xFF666666).red, ThemeContrast.compositeOver(translucent, Color.White).red, 1e-6f)
        assertEquals(5.74, ThemeContrast.contrastRatio(translucent, Color.White), 0.01)
        assertEquals(1.0, ThemeContrast.contrastRatio(translucent, Color.Black), 1e-9)
        assertEquals(grey, ThemeContrast.pick(opposite, listOf(translucent, grey), ThemeContrast.ICON))
    }

    @Test(expected = IllegalArgumentException::class)
    fun pickOnNoFillsIsRejected() {
        ThemeContrast.pick(emptyList(), listOf(Color.Black), ThemeContrast.BODY_TEXT)
    }

    @Test fun readableOnFillsExtensionsMatchTheSingleFillOnesAndTheirPreference() {
        for (theme in themes.filter { it.static }) {
            val s = theme.scheme
            val tiers = s.surfaceTiers().map { it.second }
            // One list over every tier equals the single-fill result when that result is shared.
            val singles = tiers.map { s.readableOn(it) }.distinct()
            if (singles.size == 1) assertEquals(theme.name, singles.single(), s.readableOn(tiers))
            // The role each helper prefers is kept on the static tiers (all pass the gate).
            assertEquals(theme.name, s.onSurface, s.readableOn(tiers))
            assertEquals(theme.name, s.onSurfaceVariant, s.readableMutedOn(tiers))
            assertEquals(theme.name, s.primary, s.readableAccentOn(tiers))
            // Every helper's result reads on every fill it was asked about.
            assertTrue(theme.name, ThemeContrast.contrastRatio(s.readableOn(tiers), tiers) >= ThemeContrast.BODY_TEXT)
            assertTrue(theme.name, ThemeContrast.contrastRatio(s.readableMutedOn(tiers), tiers) >= ThemeContrast.BODY_TEXT)
            assertTrue(theme.name, ThemeContrast.contrastRatio(s.readableAccentOn(tiers), tiers) >= ThemeContrast.ICON)
        }
    }

    @Test fun themeAndAssistantContrastAgree() {
        // The assistant's AssistantContrast delegates here (W2); this pins that the delegate
        // still exposes the same thresholds and the same hand-computed results, so a future
        // re-fork of the arithmetic on the assistant side cannot drift silently.
        assertEquals(ThemeContrast.BODY_TEXT, AssistantContrast.BODY_TEXT, 0.0)
        assertEquals(ThemeContrast.ICON, AssistantContrast.ICON, 0.0)
        val grey = Color(0xFF777777)
        val opposite = listOf(Color.White, Color.Black)
        assertEquals(4.48, AssistantContrast.contrastRatio(grey, Color.White), 0.01)
        assertEquals(4.48, AssistantContrast.contrastRatio(grey, opposite), 0.01)
        assertEquals(Color.Black, AssistantContrast.pick(Color.White, listOf(grey), AssistantContrast.BODY_TEXT))
        assertEquals(grey, AssistantContrast.pick(Color.White, listOf(grey), AssistantContrast.ICON))
        assertEquals(grey, AssistantContrast.pick(opposite, listOf(grey), AssistantContrast.BODY_TEXT))
        val samples = listOf(
            Color(0xFF6C4FF5) to Color(0xFFFCF8FF),
            Color(0xFFD2C2FF) to Color(0xFF15101D),
            Color(0x99FFFFFF) to Color(0xFF30263D)
        )
        for ((fg, bg) in samples) {
            assertEquals(AssistantContrast.contrastRatio(fg, bg), ThemeContrast.contrastRatio(fg, bg), 1e-9)
        }
        val fills = samples.map { it.second }
        val candidates = samples.map { it.first }
        for (minimum in listOf(ThemeContrast.ICON, ThemeContrast.BODY_TEXT)) {
            assertEquals(AssistantContrast.pick(fills, candidates, minimum), ThemeContrast.pick(fills, candidates, minimum))
            for (fill in fills) {
                assertEquals(AssistantContrast.pick(fill, candidates, minimum), ThemeContrast.pick(fill, candidates, minimum))
            }
        }
    }

    @Test fun assistantSurfacePaletteReadsOnEveryFillInEveryTheme() {
        // The assistant resolves every text/icon colour through ThemeContrast.pick against the
        // fill(s) its container can present. Read-only, through the palette's public API: every
        // pair it reports must meet its own threshold on every theme in this matrix, including
        // the pitch-black ramp and the three wallpaper-dynamic shapes.
        val failures = buildList {
            for (theme in themes) {
                val palette = AssistantSurfacePalette.resolve(theme.scheme)
                val pairs = palette.pairs()
                assertTrue(theme.name, pairs.size >= 40)
                for (pair in pairs) {
                    if (!pair.passes) add("${theme.name}: ${pair.name} ${pair.ratio.fmt()} < ${pair.minimum}")
                }
                // The pick semantics hold on the resolved outputs, not only on the pair list.
                for (c in listOf(palette.pill, palette.answer, palette.thinking, palette.toolbar, palette.notice)) {
                    assertTrue(theme.name, ThemeContrast.contrastRatio(c.body, c.fills) >= ThemeContrast.BODY_TEXT)
                    assertTrue(theme.name, ThemeContrast.contrastRatio(c.muted, c.fills) >= ThemeContrast.BODY_TEXT)
                    assertTrue(theme.name, ThemeContrast.contrastRatio(c.accent, c.fills) >= ThemeContrast.ICON)
                    assertTrue(theme.name, ThemeContrast.contrastRatio(c.error, c.fills) >= ThemeContrast.BODY_TEXT)
                    assertTrue(theme.name, ThemeContrast.contrastRatio(c.indicator, c.fills) >= ThemeContrast.ICON)
                    // Body keeps the theme role whenever that role reads (no needless repaint).
                    if (ThemeContrast.contrastRatio(theme.scheme.onSurface, c.fills) >= ThemeContrast.BODY_TEXT) {
                        assertEquals(theme.name, theme.scheme.onSurface, c.body)
                    }
                }
            }
        }
        assertTrue("${failures.size} assistant pair(s) below threshold:\n" + failures.joinToString("\n"), failures.isEmpty())
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
