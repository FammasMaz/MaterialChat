package com.materialchat.ui.components

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.materialchat.assistant.ui.AssistantContrast
import com.materialchat.assistant.ui.AssistantContrastPair

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
    RowTitle(AssistantContrast.BODY_TEXT, { it.surfaceContainer }, { it.onSurface }),
    RowTitleArchived(AssistantContrast.BODY_TEXT, { it.surfaceContainerHigh }, { it.onSurface }),
    RowTitlePressed(AssistantContrast.BODY_TEXT, { pressedRow(it) }, { it.onSurface }),
    RowTitleStreaming(AssistantContrast.BODY_TEXT, { streamingRow(it) }, { it.onSurface }),
    RowMeta(AssistantContrast.BODY_TEXT, { it.surfaceContainer }, { it.onSurfaceVariant }, { listOf(it.onSurface) }),
    RowMetaArchived(AssistantContrast.BODY_TEXT, { it.surfaceContainerHigh }, { it.onSurfaceVariant }, { listOf(it.onSurface) }),
    RowMetaPressed(AssistantContrast.BODY_TEXT, { pressedRow(it) }, { it.onSurfaceVariant }, { listOf(it.onSurface) }),
    RowMetaStreaming(AssistantContrast.BODY_TEXT, { streamingRow(it) }, { it.onSurfaceVariant }, { listOf(it.onSurface) }),
    RowActiveLabel(AssistantContrast.BODY_TEXT, { streamingRow(it) }, { it.primary }, { listOf(it.onPrimaryContainer, it.onSurface) }),
    RowTileIcon(AssistantContrast.ICON, { it.primaryContainer }, { it.onPrimaryContainer }),
    ProviderChip(AssistantContrast.BODY_TEXT, { it.primaryContainer }, { it.onPrimaryContainer }),
    GroupTileIcon(AssistantContrast.ICON, { over(it.primaryContainer, 0.7f, it.surfaceContainer) }, { it.onPrimaryContainer }, { listOf(it.onSurface) }),
    GroupProviderChip(AssistantContrast.BODY_TEXT, { over(it.primaryContainer, 0.7f, it.surfaceContainer) }, { it.onPrimaryContainer }, { listOf(it.onSurface) }),
    ArchivedChip(AssistantContrast.BODY_TEXT, { it.secondaryContainer }, { it.onSecondaryContainer }),
    BranchChip(AssistantContrast.BODY_TEXT, { over(it.secondaryContainer, 0.7f, it.surfaceContainer) }, { it.onSecondaryContainer }, { listOf(it.onSurface) }),
    BranchRowTitle(AssistantContrast.BODY_TEXT, { it.surfaceContainerHigh }, { it.onSurface }),
    BranchRowIcon(AssistantContrast.ICON, { it.surfaceContainerHigh }, { it.secondary }, { listOf(it.onSurfaceVariant, it.onSurface) }),

    // --- Home: swipe actions at every drag fraction ---------------------------------------
    SwipeDeleteText(AssistantContrast.BODY_TEXT, { it.errorContainer }, { it.onErrorContainer }),
    SwipeDeleteIcon(AssistantContrast.ICON, { it.errorContainer }, { it.onErrorContainer }),
    SwipeDeleteArmedText(AssistantContrast.BODY_TEXT, { it.error }, { it.onError }),
    SwipeDeleteArmedIcon(AssistantContrast.ICON, { it.error }, { it.onError }),
    SwipeArchiveText(AssistantContrast.BODY_TEXT, { it.secondaryContainer }, { it.onSecondaryContainer }),
    SwipeArchiveIcon(AssistantContrast.ICON, { it.secondaryContainer }, { it.onSecondaryContainer }),
    SwipeRestoreText(AssistantContrast.BODY_TEXT, { it.tertiaryContainer }, { it.onTertiaryContainer }),
    SwipeRestoreIcon(AssistantContrast.ICON, { it.tertiaryContainer }, { it.onTertiaryContainer }),
    SwipeEditText(AssistantContrast.BODY_TEXT, { it.primaryContainer }, { it.onPrimaryContainer }),
    SwipeEditIcon(AssistantContrast.ICON, { it.primaryContainer }, { it.onPrimaryContainer }),

    // --- Home: header, filters, empty state, fast scroll, dialogs, snackbar ---------------
    Wordmark(AssistantContrast.ICON, { it.surfaceContainer }, { it.primary }, { listOf(it.onSurface) }),
    StatusChipText(AssistantContrast.BODY_TEXT, { it.secondaryContainer }, { it.onSecondaryContainer }),
    StatusChipDot(AssistantContrast.ICON, { it.secondaryContainer }, { it.primary }, { listOf(it.onSecondaryContainer) }),
    TempChatAction(AssistantContrast.ICON, { it.tertiaryContainer }, { it.onTertiaryContainer }),
    SearchAction(AssistantContrast.ICON, { it.surfaceContainerHighest }, { it.onSurfaceVariant }, { listOf(it.onSurface) }),
    FilterChatsChecked(AssistantContrast.BODY_TEXT, { it.primaryContainer }, { it.onPrimaryContainer }),
    FilterArchiveChecked(AssistantContrast.BODY_TEXT, { it.secondaryContainer }, { it.onSecondaryContainer }),
    FilterUnchecked(AssistantContrast.BODY_TEXT, { it.surfaceContainer }, { it.onSurface }),
    EmptyHeroPrimary(AssistantContrast.ICON, { it.primaryContainer }, { it.onPrimaryContainer }),
    EmptyHeroSecondary(AssistantContrast.ICON, { it.secondaryContainer }, { it.onSecondaryContainer }),
    EmptyHeadline(AssistantContrast.BODY_TEXT, { it.surfaceContainerLow }, { it.onSurface }),
    EmptyBody(AssistantContrast.BODY_TEXT, { it.surfaceContainerLow }, { it.onSurfaceVariant }, { listOf(it.onSurface) }),
    FastScrollThumb(AssistantContrast.ICON, { it.surfaceContainerLow }, { it.primary }, { listOf(it.onSurface) }),
    FastScrollThumbIcon(AssistantContrast.ICON, { it.primary }, { it.onPrimary }),
    FastScrollLabel(AssistantContrast.BODY_TEXT, { it.secondaryContainer }, { it.onSecondaryContainer }),
    DialogBody(AssistantContrast.BODY_TEXT, { it.surfaceContainerHigh }, { it.onSurface }),
    DialogLabel(AssistantContrast.BODY_TEXT, { it.surfaceContainerHigh }, { it.primary }, { listOf(it.onSurface) }),
    DialogDestructive(AssistantContrast.BODY_TEXT, { it.surfaceContainerHigh }, { it.error }, { listOf(it.onErrorContainer, it.onSurface) }),
    SnackbarBody(AssistantContrast.BODY_TEXT, { it.inverseSurface }, { it.inverseOnSurface }),
    SnackbarAction(AssistantContrast.BODY_TEXT, { it.inverseSurface }, { it.inversePrimary }, { listOf(it.inverseOnSurface) }),

    // --- Settings ----------------------------------------------------------------------
    SectionTileIcon(AssistantContrast.ICON, { it.primaryContainer }, { it.onPrimaryContainer }),
    SettingsRowTitle(AssistantContrast.BODY_TEXT, { it.surfaceContainerHigh }, { it.onSurface }),
    SettingsRowSupporting(AssistantContrast.BODY_TEXT, { it.surfaceContainerHigh }, { it.onSurfaceVariant }, { listOf(it.onSurface) }),
    SettingsRowChevron(AssistantContrast.ICON, { it.surfaceContainerHigh }, { it.onSurfaceVariant }, { listOf(it.onSurface) }),
    SettingsRowLeadingIcon(AssistantContrast.ICON, { it.surfaceContainerHigh }, { it.primary }, { listOf(it.onSurface) }),
    SettingsNavTileIcon(AssistantContrast.ICON, { it.surfaceContainerHighest }, { it.primary }, { listOf(it.onSurface) }),
    SwitchOnThumb(AssistantContrast.ICON, { it.primary }, { it.onPrimary }),
    SwitchOnThumbIcon(AssistantContrast.ICON, { it.onPrimary }, { it.primary }),
    SwitchOffThumb(AssistantContrast.ICON, { it.surfaceVariant }, { it.onSurface }),
    SwitchOffThumbIcon(AssistantContrast.ICON, { it.onSurface }, { it.surfaceVariant }),
    SliderActiveTrackOnLow(AssistantContrast.ICON, { it.surfaceContainerLow }, { it.primary }, { listOf(it.onSurface) }),
    SliderActiveTrackOnHigh(AssistantContrast.ICON, { it.surfaceContainerHigh }, { it.primary }, { listOf(it.onSurface) }),
    // The slider's inactive track (surfaceContainerHighest on a surfaceContainerHigh row) is
    // deliberately not a slot: it measures 1.07:1 light / 1.17:1 dark on every palette, and no
    // M3 track role reaches 3:1 there. WCAG 1.4.11 covers the thumb and active track, which are
    // the SliderActiveTrack* slots above.
    ProviderTitle(AssistantContrast.BODY_TEXT, { it.surfaceContainerHigh }, { it.onSurface }),
    ProviderMeta(AssistantContrast.BODY_TEXT, { it.surfaceContainerHigh }, { it.onSurfaceVariant }, { listOf(it.onSurface) }),
    ProviderTitleActive(AssistantContrast.BODY_TEXT, { it.primaryContainer }, { it.onSurface }, { listOf(it.onPrimaryContainer) }),
    ProviderMetaActive(AssistantContrast.BODY_TEXT, { it.primaryContainer }, { it.onSurfaceVariant }, { listOf(it.onPrimaryContainer, it.onSurface) }),
    ProviderCredentialError(AssistantContrast.BODY_TEXT, { it.surfaceContainerHigh }, { it.error }, { listOf(it.onErrorContainer, it.onSurface) }),
    ProviderCredentialErrorActive(AssistantContrast.BODY_TEXT, { it.primaryContainer }, { it.error }, { listOf(it.onErrorContainer, it.onPrimaryContainer) }),
    ProviderCredentialOk(AssistantContrast.ICON, { it.surfaceContainerHigh }, { it.primary }, { listOf(it.onSurface) }),
    ProviderCredentialOkActive(AssistantContrast.ICON, { it.primaryContainer }, { it.primary }, { listOf(it.onPrimaryContainer) }),
    ProviderTypeIcon(AssistantContrast.ICON, { it.secondaryContainer }, { it.onSecondaryContainer }),
    ProviderActiveBadge(AssistantContrast.ICON, { it.onPrimaryContainer }, { it.primaryContainer }),
    ProviderSplitButton(AssistantContrast.BODY_TEXT, { it.secondaryContainer }, { it.onSecondaryContainer }),
    PremiumDescription(AssistantContrast.BODY_TEXT, { it.primaryContainer }, { it.onPrimaryContainer.copy(alpha = 0.78f) }, { listOf(it.onPrimaryContainer) }),
    SecondaryCardDescription(AssistantContrast.BODY_TEXT, { it.secondaryContainer }, { it.onSecondaryContainer.copy(alpha = 0.78f) }, { listOf(it.onSecondaryContainer) }),
    TertiaryCardDescription(AssistantContrast.BODY_TEXT, { it.tertiaryContainer }, { it.onTertiaryContainer.copy(alpha = 0.78f) }, { listOf(it.onTertiaryContainer) }),
    SystemPromptPlaceholder(AssistantContrast.BODY_TEXT, { it.surfaceContainerLow }, { it.onSurfaceVariant.copy(alpha = 0.6f) }, { listOf(it.onSurfaceVariant, it.onSurface) }),
    AssistantDefaultCardText(AssistantContrast.BODY_TEXT, { over(it.primaryContainer, 0.5f, it.surfaceContainerLow) }, { it.onSurface }),
    SearchNativeNote(AssistantContrast.BODY_TEXT, { over(it.secondaryContainer, 0.58f, it.surfaceContainerHigh) }, { it.onSecondaryContainer }, { listOf(it.onSurface) }),
    UpdateBannerMuted(AssistantContrast.BODY_TEXT, { it.primaryContainer }, { it.onPrimaryContainer.copy(alpha = 0.8f) }, { listOf(it.onPrimaryContainer) }),
    MemoryMuted(AssistantContrast.BODY_TEXT, { it.secondaryContainer }, { it.onSecondaryContainer.copy(alpha = 0.66f) }, { listOf(it.onSecondaryContainer) });

    companion object {
        private fun over(top: Color, alpha: Float, under: Color): Color =
            AssistantContrast.compositeOver(top.copy(alpha = alpha), under)

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

    fun pairs(): List<AssistantContrastPair> =
        HomeSettingsSlot.values().map { AssistantContrastPair(it.name, it.fill(scheme), this[it], it.minimum) }

    companion object {
        /** Each slot exactly as its call site reads the theme role: the baseline. */
        fun drawn(scheme: ColorScheme): HomeSettingsPalette =
            HomeSettingsPalette(scheme, HomeSettingsSlot.values().associateWith { it.preferred(scheme) })

        /** Each slot's preferred role where it reads, else the first alternate that does. */
        fun resolve(scheme: ColorScheme): HomeSettingsPalette =
            HomeSettingsPalette(
                scheme,
                HomeSettingsSlot.values().associateWith { slot ->
                    AssistantContrast.pick(
                        fill = slot.fill(scheme),
                        candidates = listOf(slot.preferred(scheme)) + slot.alternates(scheme),
                        minimum = slot.minimum
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

/**
 * A muted secondary line on [fill]: [content] at [alpha] where that still reads at [minimum]
 * (WCAG AA body text by default), otherwise [content] at full opacity. Keeps supporting text
 * quieter than its headline without letting a dark container push it below AA.
 */
internal fun mutedContentOn(
    fill: Color,
    content: Color,
    alpha: Float,
    minimum: Double = AssistantContrast.BODY_TEXT
): Color = AssistantContrast.pick(fill, listOf(content.copy(alpha = alpha), content), minimum)

/** [mutedContentOn] at the icon threshold (3:1). */
internal fun mutedIconOn(fill: Color, content: Color, alpha: Float): Color =
    mutedContentOn(fill, content, alpha, AssistantContrast.ICON)
