package com.materialchat.ui.screens.chat.components

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.materialchat.assistant.ui.AssistantContrastPair
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.ui.theme.MaterialChatThemePalettes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Every text/icon colour the chat screen draws must read against the fill it is drawn on: WCAG
 * AA, 4.5:1 for text and 3:1 for icons, for every theme the app can produce and every bubble
 * family.
 *
 * Enumerated like the assistant's test: the static palettes in light, dark and pitch-black
 * dark, plus three dynamic-seed-like schemes in the same three modes; each crossed with the
 * four bubble families.
 */
class ChatContrastTest {

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

    private val families = AppPreferences.ChatBubbleStyle.values().toList()

    @Test fun themeMatrixCoversEveryAppearance() {
        assertEquals(AppPreferences.ThemePalette.values().size * 3 + 9, themes.size)
        assertEquals(themes.size, themes.map { it.name }.distinct().size)
        assertTrue(families.size >= 4)
    }

    @Test fun everyResolvedPairMeetsWcagAaInEveryThemeAndBubbleFamily() {
        val failures = buildList {
            for (theme in themes) for (family in families) {
                val palette = ChatSurfacePalette.resolve(theme.scheme, family)
                for (pair in palette.pairs()) if (!pair.passes) {
                    add("${theme.name} / $family: ${pair.name} ${pair.ratio.fmt()} < ${pair.minimum}")
                }
            }
        }
        assertTrue("${failures.size} pair(s) below WCAG AA:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun resolvedPairsCoverEverySurfaceTheChatDraws() {
        val names = ChatSurfacePalette.resolve(themes.first().scheme, families.first()).pairs().map { it.name }
        for (expected in listOf(
            "header title", "header muted", "header pulse", "ephemeral header title",
            "user bubble", "user quote", "assistant bubble", "assistant errored bubble", "assistant muted",
            "assistant error[0]", "assistant link", "system bubble",
            "thinking title[0]", "thinking preview[1]", "thinking chevron[0]", "thinking body",
            "memory saved chip", "memory used chip", "memory panel", "memory panel title",
            "sources header collapsed", "sources header expanded",
            "sources header muted collapsed", "sources header muted expanded",
            "action standard", "action primary", "action selected", "action stats text", "meta label",
            "date pill", "header chip Temporary", "header chip ContextCritical",
            "model picker[0]", "model picker disabled[1]", "composer text[0]", "composer placeholder[1]",
            "composer attach", "send idle", "send ready", "send streaming", "scroll fab", "scroll fab badge",
            "code block", "code keyword", "code string", "code comment", "code number", "code chip", "inline code",
            "table header", "table body", "table alternate", "working badge", "empty card", "empty tile",
            "image action chip", "image loading card", "attachment remove", "plus menu create",
            "image viewer scrim", "image viewer chip"
        )) assertTrue("missing pair $expected in $names", expected in names)
    }

    @Test fun thePreferredRoleIsKeptWhereverItAlreadyReads() {
        // The resolver must not repaint themes that were fine: on the default palette the
        // bubbles keep the theme's own roles exactly.
        val default = AppPreferences.ThemePalette.values().first()
        for (dark in listOf(false, true)) {
            val scheme = MaterialChatThemePalettes.colorScheme(default, darkTheme = dark)
            val palette = ChatSurfacePalette.resolve(scheme, AppPreferences.ChatBubbleStyle.EXPRESSIVE)
            assertEquals(scheme.onSurface, palette.assistantBubble.content)
            assertEquals(scheme.onPrimaryContainer, palette.userBubble.content)
            assertEquals(scheme.onSurfaceVariant, palette.actionStandard.content)
            assertEquals(scheme.onPrimary, palette.sendReady.content)
        }
    }

    /**
     * The alpha-faded labels exactly as MessageBubble / MessageActions / ChatTopBar drew them at
     * v2.41.0 (574b5c3): a role's colour at a fixed alpha, never checked against the fill. This
     * is the baseline the resolver replaces; the test records which themes it broke.
     */
    private fun legacyPairs(scheme: ColorScheme, family: AppPreferences.ChatBubbleStyle): List<AssistantContrastPair> {
        val assistant = ChatContrast.assistantBubbleFill(scheme, family)
        val user = ChatContrast.userBubbleFill(scheme, family)
        val list = scheme.surfaceContainerLow
        val thinkingHeader = ChatContrast.over(scheme.onSurface.copy(alpha = 0.07f), assistant)
        val memoryPanel = ChatContrast.over(scheme.surfaceContainerHighest.copy(alpha = 0.86f), assistant)
        return listOf(
            AssistantContrastPair("model label onSurfaceVariant@0.5", list, scheme.onSurfaceVariant.copy(alpha = 0.5f), ChatContrast.BODY_TEXT),
            AssistantContrastPair("sibling label onSurfaceVariant@0.5", list, scheme.onSurfaceVariant.copy(alpha = 0.5f), ChatContrast.BODY_TEXT),
            AssistantContrastPair("elapsed label onSurfaceVariant@0.7", list, scheme.onSurfaceVariant.copy(alpha = 0.7f), ChatContrast.BODY_TEXT),
            AssistantContrastPair("thinking preview onSurface@0.6", thinkingHeader, scheme.onSurface.copy(alpha = 0.6f), ChatContrast.BODY_TEXT),
            AssistantContrastPair("thinking body onSurface@0.7", assistant, scheme.onSurface.copy(alpha = 0.7f), ChatContrast.BODY_TEXT),
            AssistantContrastPair("user quote onPrimaryContainer@0.65", user, scheme.onPrimaryContainer.copy(alpha = 0.65f), ChatContrast.BODY_TEXT),
            AssistantContrastPair("memory panel title onSurfaceVariant@0.72", memoryPanel, scheme.onSurfaceVariant.copy(alpha = 0.72f), ChatContrast.BODY_TEXT),
            AssistantContrastPair("system bubble onTertiaryContainer", ChatContrast.systemBubbleFill(scheme), scheme.onTertiaryContainer, ChatContrast.BODY_TEXT)
        )
    }

    @Test fun legacyAlphaFadedLabelsFailWhereTheFillLeavesTheRole() {
        val failures = buildList {
            for (theme in themes) for (family in families) {
                for (pair in legacyPairs(theme.scheme, family)) if (!pair.passes) add(Triple(theme, family, pair))
            }
        }
        val report = failures.joinToString("\n") { (t, f, p) -> "${t.name} / $f: ${p.name} ${p.ratio.fmt()} < ${p.minimum}" }
        println("LEGACY FAILURES (${failures.size}):\n$report")
        assertTrue("the alpha-faded baseline was expected to fail somewhere:\n$report", failures.isNotEmpty())
        // The 50% model/sibling labels were the worst offenders: they fail in every theme.
        val halfAlpha = failures.filter { (_, _, p) -> p.name.contains("@0.5") }
        assertTrue(report, halfAlpha.map { it.first.name }.distinct().size == themes.size)
        // And the resolver reads on every one of those fills.
        for ((theme, family, _) in failures.distinctBy { it.first.name to it.second }) {
            val resolved = ChatSurfacePalette.resolve(theme.scheme, family).pairs().filterNot { it.passes }
            assertTrue("${theme.name} / $family: $resolved", resolved.isEmpty())
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

    /** A scheme shaped like Material You's output for a seed (same construction as AssistantContrastTest). */
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
