package com.materialchat.ui.components

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.materialchat.ui.theme.ThemeContrast
import com.materialchat.ui.theme.ThemeContrastPair
import com.materialchat.ui.theme.readableOn

/**
 * Every text/icon colour the home list and the settings screens draw, named, with the fill it
 * actually sits on. [HomeSettingsPalette.drawn] is the colour each call site reads from the theme
 * role by name; [HomeSettingsPalette.resolve] keeps that role wherever it reads (WCAG AA: 4.5:1
 * for text, 3:1 for icons) and otherwise falls back, in order, to the listed alternates and
 * finally pure white/black. Pure Kotlin, so the JVM test enumerates every theme the app can
 * produce (static palettes, dynamic-like seeds, light/dark/pitch-black).
 *
 * Call sites that were found to fail in some theme read their colour from
 * [rememberHomeSettingsPalette]; the rest keep reading the role directly, and the test keeps
 * them honest.
 */
internal enum class HomeSettingsSlot(
    val minimum: Double,
    val fill: (ColorScheme) -> Color,
    val preferred: (ColorScheme) -> Color,
    val alternates: (ColorScheme) -> List<Color> = { emptyList() }
) {
    // --- Home: conversation rows -------------------------------------------------------
    RowTitle(ThemeContrast.BODY_TEXT, { it.surfaceContainer }, { it.onSurface }),
    RowTitleArchived(ThemeContrast.BODY_TEXT, { it.surfaceContainerHigh }, { it.onSurface }),
    RowTitlePressed(ThemeContrast.BODY_TEXT, { pressedRow(it) }, { it.onSurface }),
    RowTitleStreaming(ThemeContrast.BODY_TEXT, { streamingRow(it) }, { it.onSurface }),
    RowMeta(ThemeContrast.BODY_TEXT, { it.surfaceContainer }, { it.onSurfaceVariant }, { listOf(it.onSurface) }),
    RowMetaArchived(ThemeContrast.BODY_TEXT, { it.surfaceContainerHigh }, { it.onSurfaceVariant }, { listOf(it.onSurface) }),
    RowMetaPressed(ThemeContrast.BODY_TEXT, { pressedRow(it) }, { it.onSurfaceVariant }, { listOf(it.onSurface) }),
    RowMetaStreaming(ThemeContrast.BODY_TEXT, { streamingRow(it) }, { it.onSurfaceVariant }, { listOf(it.onSurface) }),
    RowActiveLabel(ThemeContrast.BODY_TEXT, { streamingRow(it) }, { it.primary }, { listOf(it.onPrimaryContainer, it.onSurface) }),
    RowTileIcon(ThemeContrast.ICON, { it.primaryContainer }, { it.onPrimaryContainer }),
    ProviderChip(ThemeContrast.BODY_TEXT, { it.primaryContainer }, { it.onPrimaryContainer }),
    GroupTileIcon(ThemeContrast.ICON, { over(it.primaryContainer, 0.7f, it.surfaceContainer) }, { it.onPrimaryContainer }, { listOf(it.onSurface) }),
    GroupProviderChip(ThemeContrast.BODY_TEXT, { over(it.primaryContainer, 0.7f, it.surfaceContainer) }, { it.onPrimaryContainer }, { listOf(it.onSurface) }),
    ArchivedChip(ThemeContrast.BODY_TEXT, { it.secondaryContainer }, { it.onSecondaryContainer }),
    BranchChip(ThemeContrast.BODY_TEXT, { over(it.secondaryContainer, 0.7f, it.surfaceContainer) }, { it.onSecondaryContainer }, { listOf(it.onSurface) }),
    BranchRowTitle(ThemeContrast.BODY_TEXT, { it.surfaceContainerHigh }, { it.onSurface }),
    BranchRowIcon(ThemeContrast.ICON, { it.surfaceContainerHigh }, { it.secondary }, { listOf(it.onSurfaceVariant, it.onSurface) }),

    // --- Home: swipe actions at every drag fraction ---------------------------------------
    SwipeDeleteText(ThemeContrast.BODY_TEXT, { it.errorContainer }, { it.onErrorContainer }),
    SwipeDeleteIcon(ThemeContrast.ICON, { it.errorContainer }, { it.onErrorContainer }),
    SwipeDeleteArmedText(ThemeContrast.BODY_TEXT, { it.error }, { it.onError }),
    SwipeDeleteArmedIcon(ThemeContrast.ICON, { it.error }, { it.onError }),
    SwipeArchiveText(ThemeContrast.BODY_TEXT, { it.secondaryContainer }, { it.onSecondaryContainer }),
    SwipeArchiveIcon(ThemeContrast.ICON, { it.secondaryContainer }, { it.onSecondaryContainer }),
    SwipeRestoreText(ThemeContrast.BODY_TEXT, { it.tertiaryContainer }, { it.onTertiaryContainer }),
    SwipeRestoreIcon(ThemeContrast.ICON, { it.tertiaryContainer }, { it.onTertiaryContainer }),
    SwipeEditText(ThemeContrast.BODY_TEXT, { it.primaryContainer }, { it.onPrimaryContainer }),
    SwipeEditIcon(ThemeContrast.ICON, { it.primaryContainer }, { it.onPrimaryContainer }),

    // --- Home: header, filters, empty state, fast scroll, dialogs, snackbar ---------------
    Wordmark(ThemeContrast.ICON, { it.surfaceContainer }, { it.primary }, { listOf(it.onSurface) }),
    StatusChipText(ThemeContrast.BODY_TEXT, { it.secondaryContainer }, { it.onSecondaryContainer }),
    StatusChipDot(ThemeContrast.ICON, { it.secondaryContainer }, { it.primary }, { listOf(it.onSecondaryContainer) }),
    TempChatAction(ThemeContrast.ICON, { it.tertiaryContainer }, { it.onTertiaryContainer }),
    SearchAction(ThemeContrast.ICON, { it.surfaceContainerHighest }, { it.onSurfaceVariant }, { listOf(it.onSurface) }),
    FilterChatsChecked(ThemeContrast.BODY_TEXT, { it.primaryContainer }, { it.onPrimaryContainer }),
    FilterArchiveChecked(ThemeContrast.BODY_TEXT, { it.secondaryContainer }, { it.onSecondaryContainer }),
    FilterUnchecked(ThemeContrast.BODY_TEXT, { it.surfaceContainer }, { it.onSurface }),
    EmptyHeroPrimary(ThemeContrast.ICON, { it.primaryContainer }, { it.onPrimaryContainer }),
    EmptyHeroSecondary(ThemeContrast.ICON, { it.secondaryContainer }, { it.onSecondaryContainer }),
    EmptyHeadline(ThemeContrast.BODY_TEXT, { it.surfaceContainerLow }, { it.onSurface }),
    EmptyBody(ThemeContrast.BODY_TEXT, { it.surfaceContainerLow }, { it.onSurfaceVariant }, { listOf(it.onSurface) }),
    FastScrollThumb(ThemeContrast.ICON, { it.surfaceContainerLow }, { it.primary }, { listOf(it.onSurface) }),
    FastScrollThumbIcon(ThemeContrast.ICON, { it.primary }, { it.onPrimary }),
    FastScrollLabel(ThemeContrast.BODY_TEXT, { it.secondaryContainer }, { it.onSecondaryContainer }),
    DialogBody(ThemeContrast.BODY_TEXT, { it.surfaceContainerHigh }, { it.onSurface }),
    DialogLabel(ThemeContrast.BODY_TEXT, { it.surfaceContainerHigh }, { it.primary }, { listOf(it.onSurface) }),
    DialogDestructive(ThemeContrast.BODY_TEXT, { it.surfaceContainerHigh }, { it.error }, { listOf(it.onErrorContainer, it.onSurface) }),
    SnackbarBody(ThemeContrast.BODY_TEXT, { it.inverseSurface }, { it.inverseOnSurface }),
    SnackbarAction(ThemeContrast.BODY_TEXT, { it.inverseSurface }, { it.inversePrimary }, { listOf(it.inverseOnSurface) }),

    // --- Settings ----------------------------------------------------------------------
    SectionTileIcon(ThemeContrast.ICON, { it.primaryContainer }, { it.onPrimaryContainer }),
    SettingsRowTitle(ThemeContrast.BODY_TEXT, { it.surfaceContainerHigh }, { it.onSurface }),
    SettingsRowSupporting(ThemeContrast.BODY_TEXT, { it.surfaceContainerHigh }, { it.onSurfaceVariant }, { listOf(it.onSurface) }),
    SettingsRowChevron(ThemeContrast.ICON, { it.surfaceContainerHigh }, { it.onSurfaceVariant }, { listOf(it.onSurface) }),
    SettingsRowLeadingIcon(ThemeContrast.ICON, { it.surfaceContainerHigh }, { it.primary }, { listOf(it.onSurface) }),
    SettingsNavTileIcon(ThemeContrast.ICON, { it.surfaceContainerHighest }, { it.primary }, { listOf(it.onSurface) }),
    SwitchOnThumb(ThemeContrast.ICON, { it.primary }, { it.onPrimary }),
    SwitchOnThumbIcon(ThemeContrast.ICON, { it.onPrimary }, { it.primary }),
    SwitchOffThumb(ThemeContrast.ICON, { it.surfaceVariant }, { it.onSurface }),
    SwitchOffThumbIcon(ThemeContrast.ICON, { it.onSurface }, { it.surfaceVariant }),
    SliderActiveTrackOnLow(ThemeContrast.ICON, { it.surfaceContainerLow }, { it.primary }, { listOf(it.onSurface) }),
    SliderActiveTrackOnHigh(ThemeContrast.ICON, { it.surfaceContainerHigh }, { it.primary }, { listOf(it.onSurface) }),
    // The slider's inactive track (surfaceContainerHighest on a surfaceContainerHigh row) is
    // deliberately not a slot: it measures 1.07:1 light / 1.17:1 dark on every palette, and no
    // M3 track role reaches 3:1 there. WCAG 1.4.11 covers the thumb and active track, which are
    // the SliderActiveTrack* slots above.
    ProviderTitle(ThemeContrast.BODY_TEXT, { it.surfaceContainerHigh }, { it.onSurface }),
    ProviderMeta(ThemeContrast.BODY_TEXT, { it.surfaceContainerHigh }, { it.onSurfaceVariant }, { listOf(it.onSurface) }),
    ProviderTitleActive(ThemeContrast.BODY_TEXT, { it.primaryContainer }, { it.onSurface }, { listOf(it.onPrimaryContainer) }),
    ProviderMetaActive(ThemeContrast.BODY_TEXT, { it.primaryContainer }, { it.onSurfaceVariant }, { listOf(it.onPrimaryContainer, it.onSurface) }),
    ProviderCredentialError(ThemeContrast.BODY_TEXT, { it.surfaceContainerHigh }, { it.error }, { listOf(it.onErrorContainer, it.onSurface) }),
    ProviderCredentialErrorActive(ThemeContrast.BODY_TEXT, { it.primaryContainer }, { it.error }, { listOf(it.onErrorContainer, it.onPrimaryContainer) }),
    ProviderCredentialOk(ThemeContrast.ICON, { it.surfaceContainerHigh }, { it.primary }, { listOf(it.onSurface) }),
    ProviderCredentialOkActive(ThemeContrast.ICON, { it.primaryContainer }, { it.primary }, { listOf(it.onPrimaryContainer) }),
    ProviderTypeIcon(ThemeContrast.ICON, { it.secondaryContainer }, { it.onSecondaryContainer }),
    ProviderActiveBadge(ThemeContrast.ICON, { it.onPrimaryContainer }, { it.primaryContainer }),
    ProviderSplitButton(ThemeContrast.BODY_TEXT, { it.secondaryContainer }, { it.onSecondaryContainer }),
    // Supporting lines on a role container use that role's on* colour at full opacity: the
    // theme audit bans alpha on on* text (78-80% measured 4.0-4.3:1 on the dark palettes).
    PremiumDescription(ThemeContrast.BODY_TEXT, { it.primaryContainer }, { it.onPrimaryContainer }),
    SecondaryCardDescription(ThemeContrast.BODY_TEXT, { it.secondaryContainer }, { it.onSecondaryContainer }),
    TertiaryCardDescription(ThemeContrast.BODY_TEXT, { it.tertiaryContainer }, { it.onTertiaryContainer }),
    SystemPromptPlaceholder(ThemeContrast.BODY_TEXT, { it.surfaceContainerLow }, { it.onSurfaceVariant }, { listOf(it.onSurface) }),
    AssistantDefaultCardText(ThemeContrast.BODY_TEXT, { over(it.primaryContainer, 0.5f, it.surfaceContainerLow) }, { it.onSurface }),
    SearchNativeNote(ThemeContrast.BODY_TEXT, { over(it.secondaryContainer, 0.58f, it.surfaceContainerHigh) }, { it.onSecondaryContainer }, { listOf(it.onSurface) }),
    UpdateBannerMuted(ThemeContrast.BODY_TEXT, { it.primaryContainer }, { it.onPrimaryContainer }),
    MemoryMuted(ThemeContrast.BODY_TEXT, { it.secondaryContainer }, { it.onSecondaryContainer }),

    // --- Shared components -------------------------------------------------------------
    // (ImageViewerDialog is owned by the chat pass and gated in ChatContrastTest.)
    // Insights stat cards: label and value text on the surfaceContainerHigh card.
    StatCardLabel(ThemeContrast.BODY_TEXT, { it.surfaceContainerHigh }, { it.onSurfaceVariant }, { listOf(it.onSurface) }),
    StatCardValue(ThemeContrast.BODY_TEXT, { it.surfaceContainerHigh }, { it.onSurface }),

    // --- Full-screen error state (LoadErrorContent on HomeEmptyState) ---------------------
    // Home, settings, bookmarks and workflow execution draw it inside ExpressiveContentSurface
    // (surfaceContainerLow); Persona Studio draws it straight on the scaffold (surfaceContainer).
    ErrorHeadline(ThemeContrast.BODY_TEXT, { it.surfaceContainerLow }, { it.onSurface }),
    ErrorBody(ThemeContrast.BODY_TEXT, { it.surfaceContainerLow }, { it.onSurfaceVariant }, { listOf(it.onSurface) }),
    ErrorHeadlineOnContainer(ThemeContrast.BODY_TEXT, { it.surfaceContainer }, { it.onSurface }),
    ErrorBodyOnContainer(ThemeContrast.BODY_TEXT, { it.surfaceContainer }, { it.onSurfaceVariant }, { listOf(it.onSurface) }),
    ErrorHeroIcon(ThemeContrast.ICON, { it.secondaryContainer }, { it.onSecondaryContainer }),
    ErrorPrimaryAction(ThemeContrast.BODY_TEXT, { it.primary }, { it.onPrimary }, { listOf(it.onPrimaryContainer) }),
    ErrorSecondaryAction(ThemeContrast.BODY_TEXT, { it.surfaceContainerLow }, { it.primary }, { listOf(it.onSurface) }),
    ErrorSecondaryActionOnContainer(ThemeContrast.BODY_TEXT, { it.surfaceContainer }, { it.primary }, { listOf(it.onSurface) });

    companion object {
        private fun over(top: Color, alpha: Float, under: Color): Color =
            ThemeContrast.compositeOver(top.copy(alpha = alpha), under)

        /** ConversationItem pressed fill: onSurface at 8% over surfaceContainerHigh. */
        fun pressedRow(scheme: ColorScheme): Color = over(scheme.onSurface, 0.08f, scheme.surfaceContainerHigh)

        /** ConversationItem streaming fill: primaryContainer at 48% over the list surface. */
        fun streamingRow(scheme: ColorScheme): Color = over(scheme.primaryContainer, 0.48f, scheme.surfaceContainerLow)
    }
}

/** The content colour of every [HomeSettingsSlot] for one colour scheme. */
internal class HomeSettingsPalette private constructor(
    private val scheme: ColorScheme,
    private val contents: Map<HomeSettingsSlot, Color>
) {
    /** The colour to draw [slot]'s content with. */
    operator fun get(slot: HomeSettingsSlot): Color = contents.getValue(slot)

    /** The fill [slot]'s content sits on. */
    fun fill(slot: HomeSettingsSlot): Color = slot.fill(scheme)

    fun pairs(): List<ThemeContrastPair> =
        HomeSettingsSlot.values().map { ThemeContrastPair(it.name, it.fill(scheme), this[it], it.minimum) }

    companion object {
        /** Each slot exactly as its call site reads the theme role: the baseline. */
        fun drawn(scheme: ColorScheme): HomeSettingsPalette =
            HomeSettingsPalette(scheme, HomeSettingsSlot.values().associateWith { it.preferred(scheme) })

        /** Each slot's preferred role where it reads, else the first alternate that does. */
        fun resolve(scheme: ColorScheme): HomeSettingsPalette =
            HomeSettingsPalette(
                scheme,
                HomeSettingsSlot.values().associateWith { slot ->
                    // The theme's runtime guard: preferred role, then the alternates, then
                    // onSurface / inverseOnSurface / white / black.
                    scheme.readableOn(
                        fill = slot.fill(scheme),
                        minimum = slot.minimum,
                        preferred = listOf(slot.preferred(scheme)) + slot.alternates(scheme)
                    )
                }
            )
    }
}

/** The resolved palette for the current theme; recomputed only when the scheme changes. */
@Composable
internal fun rememberHomeSettingsPalette(): HomeSettingsPalette {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) { HomeSettingsPalette.resolve(scheme) }
}
