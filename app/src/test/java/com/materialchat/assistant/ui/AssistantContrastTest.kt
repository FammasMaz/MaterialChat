package com.materialchat.assistant.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.ui.theme.MaterialChatThemePalettes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Every text/icon colour the assistant window draws must read against the fill it is drawn on:
 * WCAG AA, 4.5:1 for body text and 3:1 for icons, for every theme the app can produce.
 *
 * Enumerated: the ten static palettes in light, dark and pitch-black dark; three
 * dynamic-seed-like schemes (built with lightColorScheme/darkColorScheme from varied seeds, the
 * way Material You tones come out) in the same three modes: 39 appearances in all.
 */
class AssistantContrastTest {

    private data class Theme(val name: String, val scheme: ColorScheme)

    private val themes: List<Theme> by lazy {
        buildList {
        val statics = AppPreferences.ThemePalette.values().flatMap { palette ->
            listOf(
                "$palette light" to MaterialChatThemePalettes.colorScheme(palette, darkTheme = false),
                "$palette dark" to MaterialChatThemePalettes.colorScheme(palette, darkTheme = true),
                "$palette pitch-black" to MaterialChatThemePalettes.colorScheme(palette, darkTheme = true).pitchBlack()
            )
        }
        val dynamics = listOf(
            "dynamic blue light" to dynamicLike(seedBlue, dark = false),
            "dynamic blue dark" to dynamicLike(seedBlue, dark = true),
            "dynamic blue pitch-black" to dynamicLike(seedBlue, dark = true).pitchBlack(),
            "dynamic green light" to dynamicLike(seedGreen, dark = false),
            "dynamic green dark" to dynamicLike(seedGreen, dark = true),
            "dynamic green pitch-black" to dynamicLike(seedGreen, dark = true).pitchBlack(),
            "dynamic amber light" to dynamicLike(seedAmber, dark = false),
            "dynamic amber dark" to dynamicLike(seedAmber, dark = true),
            "dynamic amber pitch-black" to dynamicLike(seedAmber, dark = true).pitchBlack()
        )
        for ((name, scheme) in statics + dynamics) add(Theme(name, scheme))
        }
    }

    @Test fun themeMatrixCoversEveryAppearance() {
        // 10 static palettes x (light, dark, pitch-black) + 3 dynamic seeds x the same three.
        assertEquals(39, themes.size)
        assertEquals(themes.size, themes.map { it.name }.distinct().size)
    }

    @Test fun contrastRatioMatchesWcagReferenceValues() {
        assertEquals(21.0, AssistantContrast.contrastRatio(Color.White, Color.Black), 1e-6)
        assertEquals(1.0, AssistantContrast.contrastRatio(Color.White, Color.White), 1e-6)
        // #777777 on white is the classic 4.48:1 just-below-AA grey.
        assertEquals(4.48, AssistantContrast.contrastRatio(Color(0xFF777777), Color.White), 0.01)
        assertEquals(4.48, AssistantContrast.contrastRatio(Color.White, Color(0xFF777777)), 0.01)
    }

    @Test fun translucentForegroundIsCompositedBeforeMeasuring() {
        val halfBlack = Color.Black.copy(alpha = 0.5f)
        val composited = AssistantContrast.compositeOver(halfBlack, Color.White)
        // Color stores 8-bit channels: 0.5 * 255 rounds to 127/255.
        assertEquals(0.5f, composited.red, 0.01f)
        assertEquals(1f, composited.alpha, 0f)
        assertTrue(AssistantContrast.contrastRatio(halfBlack, Color.White) < AssistantContrast.contrastRatio(Color.Black, Color.White))
    }

    @Test fun pickKeepsThePreferredRoleWhenItReadsAndFallsBackOtherwise() {
        val dark = Color(0xFF1E1237)
        val light = Color(0xFFEDE5F4)
        assertEquals(dark, AssistantContrast.pick(Color.White, listOf(dark, light), AssistantContrast.BODY_TEXT))
        assertEquals(light, AssistantContrast.pick(Color.Black, listOf(dark, light), AssistantContrast.BODY_TEXT))
        // Neither role reads on mid grey at 4.5:1; white/black close the list and black wins on #808080.
        val mid = Color(0xFF808080)
        val picked = AssistantContrast.pick(mid, listOf(Color(0xFF606060), Color(0xFFA0A0A0)), AssistantContrast.BODY_TEXT)
        assertTrue(picked == Color.Black || picked == Color.White)
        assertTrue(AssistantContrast.contrastRatio(picked, mid) >= 3.9)
    }

    @Test fun everyResolvedPairMeetsWcagAaInEveryTheme() {
        val failures = buildList {
            for (theme in themes) {
                val palette = AssistantSurfacePalette.resolve(theme.scheme)
                for (pair in palette.pairs()) if (!pair.passes) add("${theme.name}: ${pair.name} ${pair.ratio.fmt()} < ${pair.minimum}")
            }
        }
        assertTrue("${failures.size} pair(s) below WCAG AA:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun resolvedPairsCoverEverySurfaceTheOverlayDraws() {
        val names = AssistantSurfacePalette.resolve(themes.first().scheme).pairs().map { it.name }
        for (expected in listOf(
            "pill body", "pill muted", "pill accent", "pill error", "pill indicator",
            "answer body", "answer muted", "answer indicator", "thinking body", "thinking muted",
            "toolbar muted", "notice muted", "error notice", "query bubble", "chip", "continue chip",
            "primary button", "stop button", "interrupt button", "thinking badge",
            // 2.41.0: the Screen chip and its remove icon, the Using-screen marker, the
            // unavailable notice's settings chip, the toolbar's icon actions, the thumbnails'
            // remove buttons. The thinking surface's two lines are "thinking body"/"thinking muted".
            "screen chip", "screen chip remove", "screen used marker", "screen settings button",
            "toolbar action", "attachment remove",
            // W2: the notice's outlined action chip on both notice fills.
            "notice action", "error notice action",
            "glyph Idle", "glyph Capturing", "glyph Thinking", "glyph Speaking"
        )) assertTrue("missing pair $expected in $names", expected in names)
    }

    @Test fun noticeActionChipReadsOnBothNoticeFillsInEveryTheme() {
        // The chip is outlined in the notice's resolved content colour: its label must read at
        // body size on the plain notice fill and on the error container in all 39 appearances.
        val failures = buildList {
            for (theme in themes) {
                val pairs = AssistantSurfacePalette.resolve(theme.scheme).pairs()
                    .filter { it.name.startsWith("notice action") || it.name.startsWith("error notice action") }
                assertEquals("${theme.name}: both notice fills must be enumerated", 2, pairs.size)
                for (pair in pairs) if (!pair.passes) add("${theme.name}: ${pair.name} ${pair.ratio.fmt()} < ${pair.minimum}")
            }
        }
        assertTrue("${failures.size} notice action pair(s) below WCAG AA:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun surfacesAddedIn2410ReadInEveryTheme() {
        // Same enumeration as everyResolvedPairMeetsWcagAaInEveryTheme, scoped so a regression on
        // one of the newer surfaces names itself.
        val newer = listOf(
            "screen chip", "screen chip remove", "screen used marker", "screen settings button",
            "toolbar action", "attachment remove", "thinking body", "thinking muted"
        )
        val failures = buildList {
            for (theme in themes) {
                val pairs = AssistantSurfacePalette.resolve(theme.scheme).pairs()
                    .filter { p -> newer.any { p.name == it || p.name.startsWith("$it[") } }
                assertTrue("${theme.name}: no pairs found", pairs.isNotEmpty())
                for (pair in pairs) if (!pair.passes) add("${theme.name}: ${pair.name} ${pair.ratio.fmt()} < ${pair.minimum}")
            }
        }
        assertTrue("${failures.size} newer pair(s) below WCAG AA:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun attachmentRemoveIsCheckedAsTheSharedRowPaintsIt() {
        // The thumbnail remove button lives in the chat composer's AttachmentPreviewRow (not
        // assistant-owned) and paints onErrorContainer on errorContainer unconditionally; the
        // palette records exactly that pair rather than a resolved substitute.
        for (theme in themes) {
            val t = AssistantSurfacePalette.resolve(theme.scheme).attachmentRemove
            assertEquals(theme.name, theme.scheme.errorContainer, t.container)
            assertEquals(theme.name, theme.scheme.onErrorContainer, t.content)
        }
    }

    @Test fun thePreferredRoleIsKeptWhereverItAlreadyReads() {
        // The resolver must not repaint themes that were fine: on every static palette the
        // pill keeps onSurface / onSurfaceVariant exactly as the theme defines them.
        for (theme in themes.filter { it.name.startsWith("VIOLET") }) {
            val palette = AssistantSurfacePalette.resolve(theme.scheme)
            assertEquals(theme.name, theme.scheme.onSurface, palette.pill.body)
            assertEquals(theme.name, theme.scheme.onSurfaceVariant, palette.pill.muted)
            assertEquals(theme.name, theme.scheme.onPrimary, palette.primaryButton.content)
        }
    }

    /**
     * The pairs exactly as AssistantOverlay / AssistantResponseCard drew them at main 476349a:
     * each content colour read from a theme role by name, never from the fill. This is the
     * baseline the resolver replaces; the test below records which themes it broke.
     */
    private fun legacyPairs(scheme: ColorScheme): List<AssistantContrastPair> {
        fun fills(container: Color) = assistantFills(container, scheme)
        val pill = fills(scheme.surfaceContainerHigh)
        val answer = fills(lerp(scheme.surfaceContainerHigh, scheme.primaryContainer, 0.22f))
        val thinking = fills(lerp(scheme.surfaceContainerHighest, scheme.secondaryContainer, 0.35f))
        val toolbar = fills(scheme.surfaceContainerLow)
        val errorNotice = fills(scheme.errorContainer)
        val query = fills(scheme.primaryContainer)
        return buildList {
            fun add(name: String, fills: List<Color>, content: Color, minimum: Double) =
                fills.forEachIndexed { i, f -> add(AssistantContrastPair("$name[$i]", f, content, minimum)) }
            add("pill transcript onSurface", pill, scheme.onSurface, AssistantContrast.BODY_TEXT)
            add("pill prompt onSurfaceVariant", pill, scheme.onSurfaceVariant, AssistantContrast.BODY_TEXT)
            add("pill idle glyph primary", pill, scheme.primary, AssistantContrast.ICON)
            add("pill icons onSurface", pill, scheme.onSurface, AssistantContrast.ICON)
            add("answer text onSurface", answer, scheme.onSurface, AssistantContrast.BODY_TEXT)
            add("answer notice onSurfaceVariant", answer, scheme.onSurfaceVariant, AssistantContrast.BODY_TEXT)
            add("answer error", answer, scheme.error, AssistantContrast.BODY_TEXT)
            add("answer typing dots onSurfaceVariant@0.6", answer, scheme.onSurfaceVariant.copy(alpha = 0.6f), AssistantContrast.ICON)
            add("thinking title onSurface", thinking, scheme.onSurface, AssistantContrast.BODY_TEXT)
            add("thinking status onSurfaceVariant", thinking, scheme.onSurfaceVariant, AssistantContrast.BODY_TEXT)
            add("toolbar icons onSurfaceVariant", toolbar, scheme.onSurfaceVariant, AssistantContrast.ICON)
            add("notice onSurfaceVariant", pill, scheme.onSurfaceVariant, AssistantContrast.BODY_TEXT)
            add("error notice onErrorContainer", errorNotice, scheme.onErrorContainer, AssistantContrast.BODY_TEXT)
            add("query onPrimaryContainer", query, scheme.onPrimaryContainer, AssistantContrast.BODY_TEXT)
        }
    }

    @Test fun legacyRoleOnlyColoursFailWhereTheFillLeavesTheRole() {
        val failures = buildList {
            for (theme in themes) {
                for (pair in legacyPairs(theme.scheme)) if (!pair.passes) add(theme to pair)
            }
        }
        val report = failures.joinToString("\n") { (t, p) -> "${t.name}: ${p.name} ${p.ratio.fmt()} < ${p.minimum}" }
        println("LEGACY FAILURES (${failures.size}):\n$report")
        assertTrue("the role-only baseline was expected to fail somewhere:\n$report", failures.isNotEmpty())
        // Light-on-light: the typing dots (onSurfaceVariant at 60% alpha) washed into the light
        // answer surface under every light palette (2.86..2.92 < 3:1).
        assertTrue(report, failures.any { (t, p) ->
            t.name.contains("light") && p.name.contains("typing dots") &&
                AssistantContrast.relativeLuminance(p.container) > 0.6
        })
        // Negative finding, kept on purpose: no dark or pitch-black theme fails at the baseline.
        // The dark role set stays readable and the pitch-black ramp only flattens the
        // screen-filling surfaces the assistant never paints text on.
        assertTrue(report, failures.all { (t, _) -> t.name.contains("light") })
        // And the resolver reads on every one of those fills.
        for ((theme, _) in failures.distinctBy { it.first.name }) {
            val resolved = AssistantSurfacePalette.resolve(theme.scheme).pairs().filterNot { it.passes }
            assertTrue("${theme.name}: $resolved", resolved.isEmpty())
        }
    }

    // --- helpers -----------------------------------------------------------------------------

    private fun Double.fmt() = String.format(Locale.US, "%.2f", this)

    /** MaterialChatTheme.withPitchBlackSurfaces (private there): the screen-filling surfaces go black. */
    private fun ColorScheme.pitchBlack(): ColorScheme = copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color.Black,
        surfaceContainer = Color.Black,
        surfaceDim = Color.Black
    )

    private data class Seed(val primary: Color, val secondary: Color, val tertiary: Color, val neutral: Color)

    private val seedBlue = Seed(Color(0xFF0B57D0), Color(0xFF535F70), Color(0xFF6B5778), Color(0xFF5F6368))
    private val seedGreen = Seed(Color(0xFF2E6B3A), Color(0xFF52634F), Color(0xFF38656A), Color(0xFF5E615A))
    private val seedAmber = Seed(Color(0xFF7A5A00), Color(0xFF6C5D3F), Color(0xFF4D6544), Color(0xFF66605A))

    /**
     * A scheme shaped like Material You's output for a seed: tonal steps of the seed hues for the
     * accents and of the neutral hue for the surface ramp, light tones for a light scheme and
     * dark for a dark one, built with the same lightColorScheme/darkColorScheme constructors.
     */
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
