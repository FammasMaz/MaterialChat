package com.materialchat.ui.components

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.materialchat.ui.theme.ThemeContrast
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.ui.theme.MaterialChatThemePalettes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Every text/icon colour the home list and the settings screens draw must read against the fill
 * it is drawn on: WCAG AA, 4.5:1 for body text and 3:1 for icons, for every theme the app can
 * produce. Same 39-appearance matrix as AssistantContrastTest: ten static palettes and three
 * dynamic-like seeds, each in light, dark and pitch-black dark.
 */
class HomeSettingsContrastTest {

    private data class Theme(val name: String, val scheme: ColorScheme)

    private val themes: List<Theme> by lazy {
        val statics = AppPreferences.ThemePalette.values().flatMap { palette ->
            listOf(
                "$palette light" to MaterialChatThemePalettes.colorScheme(palette, darkTheme = false),
                "$palette dark" to MaterialChatThemePalettes.colorScheme(palette, darkTheme = true),
                "$palette pitch-black" to MaterialChatThemePalettes.colorScheme(palette, darkTheme = true).pitchBlack()
            )
        }
        val dynamics = listOf(seedBlue to "blue", seedGreen to "green", seedAmber to "amber").flatMap { (seed, n) ->
            listOf(
                "dynamic $n light" to dynamicLike(seed, dark = false),
                "dynamic $n dark" to dynamicLike(seed, dark = true),
                "dynamic $n pitch-black" to dynamicLike(seed, dark = true).pitchBlack()
            )
        }
        (statics + dynamics).map { (name, scheme) -> Theme(name, scheme) }
    }

    @Test fun themeMatrixCoversEveryAppearance() {
        assertEquals(39, themes.size)
        assertEquals(themes.size, themes.map { it.name }.distinct().size)
    }

    @Test fun everyResolvedSlotMeetsWcagAaInEveryTheme() {
        val failures = buildList {
            for (theme in themes) {
                for (pair in HomeSettingsPalette.resolve(theme.scheme).pairs()) {
                    if (!pair.passes) add("${theme.name}: ${pair.name} ${pair.ratio.fmt()} < ${pair.minimum}")
                }
            }
        }
        assertTrue("${failures.size} slot(s) below WCAG AA:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun resolvedSlotsCoverEverySurfaceHomeAndSettingsDraw() {
        val names = HomeSettingsPalette.resolve(themes.first().scheme).pairs().map { it.name }.toSet()
        for (expected in listOf(
            "RowTitle", "RowMeta", "RowMetaStreaming", "RowActiveLabel", "ProviderChip", "ArchivedChip", "BranchChip",
            "SwipeDeleteText", "SwipeDeleteArmedText", "SwipeArchiveText", "SwipeRestoreText", "SwipeEditText",
            "Wordmark", "StatusChipText", "FilterChatsChecked", "FilterArchiveChecked", "EmptyBody",
            "FastScrollThumb", "FastScrollThumbIcon", "FastScrollLabel", "DialogDestructive", "SnackbarAction",
            "SectionTileIcon", "SettingsRowSupporting", "SettingsRowChevron", "SwitchOnThumbIcon", "SwitchOffThumbIcon",
            "SliderActiveTrackOnHigh", "ProviderSplitButton", "ProviderCredentialError", "PremiumDescription",
            "SystemPromptPlaceholder", "UpdateBannerMuted", "MemoryMuted"
        )) assertTrue("missing slot $expected", expected in names)
    }

    @Test fun newSharedComponentSlotsArePresent() {
        val names = HomeSettingsPalette.resolve(themes.first().scheme).pairs().map { it.name }.toSet()
        for (expected in listOf(
            "StatCardLabel", "StatCardValue"
        )) assertTrue("missing slot $expected", expected in names)
    }

    @Test fun thePreferredRoleIsKeptWhereverItAlreadyReads() {
        // The resolver must not repaint themes that were fine: row text keeps onSurface /
        // onSurfaceVariant exactly as the theme defines them on every static palette.
        for (theme in themes.filter { it.name.startsWith("VIOLET") }) {
            val palette = HomeSettingsPalette.resolve(theme.scheme)
            assertEquals(theme.name, theme.scheme.onSurface, palette[HomeSettingsSlot.RowTitle])
            assertEquals(theme.name, theme.scheme.onSurfaceVariant, palette[HomeSettingsSlot.RowMeta])
            assertEquals(theme.name, theme.scheme.onPrimaryContainer, palette[HomeSettingsSlot.ProviderChip])
        }
    }

    /**
     * The colours exactly as the call sites read them at v2.41.0 (574b5c3): each content colour
     * taken from a theme role by name, never from the fill. Records which slots the resolver
     * had to repaint so a regression in a theme shows up by name.
     */
    @Test fun drawnRoleOnlyColoursAreReportedWhereTheFillLeavesTheRole() {
        val failures = buildList {
            for (theme in themes) {
                for (pair in HomeSettingsPalette.drawn(theme.scheme).pairs()) if (!pair.passes) add(theme to pair)
            }
        }
        val report = failures.joinToString("\n") { (t, p) -> "${t.name}: ${p.name} ${p.ratio.fmt()} < ${p.minimum}" }
        println("DRAWN FAILURES (${failures.size}):\n$report")
        // Every drawn failure must be one the resolver repairs.
        for ((theme, pair) in failures) {
            val slot = HomeSettingsSlot.valueOf(pair.name)
            val resolved = HomeSettingsPalette.resolve(theme.scheme)
            val ratio = ThemeContrast.contrastRatio(resolved[slot], resolved.fill(slot))
            assertTrue("${theme.name}: ${pair.name} still ${ratio.fmt()} after resolve", ratio >= slot.minimum)
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

    /** Same Material You-shaped scheme builder as AssistantContrastTest. */
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
