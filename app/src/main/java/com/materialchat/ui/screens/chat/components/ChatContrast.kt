package com.materialchat.ui.screens.chat.components

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.materialchat.assistant.ui.AssistantContrast
import com.materialchat.assistant.ui.AssistantContrastPair
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.ui.theme.LocalChatBubbleStyle

/**
 * WCAG contrast for the chat screen.
 *
 * The chat paints a number of fills that are not plain theme roles: bubbles are lerps between
 * two containers, the thinking header is a translucent wash of the text colour over the bubble,
 * the memory panel is a translucent container over the bubble, and several labels are drawn in
 * the text colour at reduced alpha. A content colour taken from a role by name is only right while
 * the fill really is that role's surface, and an alpha-faded label only reads while the fill under
 * it is far enough from the text colour. [ChatSurfacePalette] resolves every such content colour
 * against the colour(s) the fill actually presents, keeping the preferred role whenever it meets
 * the required ratio and stepping to the next one otherwise (white/black close the list, so a
 * readable colour always exists).
 *
 * Pure Kotlin on top of [AssistantContrast]: no composition, no Android, so the JVM test can
 * enumerate every theme the app can produce. [ChatSurfacePalette.pairs] lists the same values the
 * UI consumes, so the test and the screen cannot drift apart.
 */
internal object ChatContrast {
    /** WCAG AA for body-sized text (labels under 18pt included). */
    const val BODY_TEXT = AssistantContrast.BODY_TEXT

    /** WCAG AA for icons, graphical objects and large text. */
    const val ICON = AssistantContrast.ICON

    fun ratio(content: Color, fill: Color): Double = AssistantContrast.contrastRatio(content, fill)

    /** Source-over composite of a translucent [top] on an opaque [under]. */
    fun over(top: Color, under: Color): Color = AssistantContrast.compositeOver(top, under)

    fun pick(fills: List<Color>, candidates: List<Color>, minimum: Double): Color =
        AssistantContrast.pick(fills, candidates, minimum)

    fun pick(fill: Color, candidates: List<Color>, minimum: Double): Color =
        AssistantContrast.pick(listOf(fill), candidates, minimum)

    // --- the fills exactly as the chat paints them ------------------------------------------

    /** MessageBubble.getBubbleStyle: the user bubble. */
    fun userBubbleFill(scheme: ColorScheme, family: AppPreferences.ChatBubbleStyle): Color = when (family) {
        AppPreferences.ChatBubbleStyle.GEOMETRIC -> lerp(scheme.primaryContainer, scheme.tertiaryContainer, 0.24f)
        else -> scheme.primaryContainer
    }

    /** MessageBubble.getBubbleStyle: the assistant bubble. */
    fun assistantBubbleFill(scheme: ColorScheme, family: AppPreferences.ChatBubbleStyle): Color = when (family) {
        AppPreferences.ChatBubbleStyle.GEOMETRIC -> lerp(scheme.surfaceContainerHigh, scheme.secondaryContainer, 0.22f)
        else -> lerp(scheme.surfaceContainer, scheme.surfaceContainerHighest, 0.92f)
    }

    /** MessageBubble.getBubbleStyle: the errored assistant bubble. */
    fun assistantErroredFill(scheme: ColorScheme, family: AppPreferences.ChatBubbleStyle): Color =
        lerp(assistantBubbleFill(scheme, family), scheme.errorContainer, 0.3f)

    /** MessageBubble.getBubbleStyle: the system bubble. */
    fun systemBubbleFill(scheme: ColorScheme): Color =
        lerp(scheme.surfaceContainer, scheme.tertiaryContainer, 0.62f)

    /** MarkdownText: the fenced code block / math block container. */
    fun codeBlockFill(scheme: ColorScheme): Color =
        if (AssistantContrast.relativeLuminance(scheme.background) < 0.5) scheme.surfaceContainerHighest
        else scheme.surfaceContainerLow
}

/** A painted container and the content colour resolved to read on it. */
internal data class ChatTonal(val container: Color, val content: Color) {
    /** The container colour itself; kept for callers that paint the fill. */
    val fill: Color get() = container
}

/**
 * Every fill the chat screen paints and the content colour resolved for each, from one
 * [ColorScheme] and the user's bubble family. See [ChatContrast].
 */
internal data class ChatSurfacePalette(
    /** The message list background (ChatScreen content surface: surfaceContainerLow). */
    val listBackground: Color,
    /** The standard masthead: container and title colour. */
    val header: ChatTonal,
    /** Secondary text on the standard masthead (streaming status line). */
    val headerMuted: Color,
    /** The pulse strip on the standard masthead. */
    val headerPulse: Color,
    /** The temporary-chat masthead (tertiaryContainer). */
    val ephemeralHeader: ChatTonal,
    val ephemeralHeaderMuted: Color,
    val ephemeralHeaderPulse: Color,
    /** The user bubble and its body text. */
    val userBubble: ChatTonal,
    /** The quoted lines above a user message's body (faded text colour when it reads). */
    val userQuote: Color,
    /** The assistant bubble and its body text. */
    val assistantBubble: ChatTonal,
    /** The errored assistant bubble (error-container tint) and its body text. */
    val assistantErroredBubble: ChatTonal,
    /** Secondary text in the assistant bubble (working status, memory list). */
    val assistantMuted: Color,
    /** Error text in the assistant bubble ("Response failed"). */
    val assistantError: Color,
    /** Links and the blockquote bar in the assistant bubble. */
    val assistantLink: Color,
    /** The system bubble and its text. */
    val systemBubble: ChatTonal,
    /** The thinking header wash: collapsed (0.07) and expanded (0.04) over the assistant bubble. */
    val thinkingHeaderFills: List<Color>,
    val thinkingTitle: Color,
    val thinkingPreview: Color,
    val thinkingChevron: Color,
    /** The reasoning body under the header, on the assistant bubble. */
    val thinkingBody: Color,
    val memorySavedChip: ChatTonal,
    val memoryUsedChip: ChatTonal,
    /** The expanded memory panel (translucent highest container over the bubble) and its body. */
    val memoryPanel: ChatTonal,
    val memoryPanelTitle: Color,
    /** The web-search sources header: collapsed (surfaceContainerHigh) and expanded (secondaryContainer at 0.72 over the bubble). */
    val sourcesHeaderCollapsed: ChatTonal,
    val sourcesHeaderExpanded: ChatTonal,
    /** The secondary line / duration on the sources header, per state. */
    val sourcesHeaderMutedCollapsed: Color,
    val sourcesHeaderMutedExpanded: Color,
    /** Action row segments: standard, the primary (regenerate), toggled (bookmarked / stats). */
    val actionStandard: ChatTonal,
    val actionPrimary: ChatTonal,
    val actionSelected: ChatTonal,
    /** Small meta labels drawn straight on the list: elapsed time, model name, sibling counter. */
    val metaLabel: Color,
    val datePill: ChatTonal,
    val headerChips: Map<ChatHeaderBadge, ChatTonal>,
    /** The model picker pill: collapsed (secondaryContainer at 0.72 over the masthead) and expanded fills. */
    val modelPickerFills: List<Color>,
    val modelPicker: Color,
    val modelPickerDisabled: Color,
    /** The composer field: resting and focused fills, body text, placeholder. */
    val composerFieldFills: List<Color>,
    val composerText: Color,
    val composerPlaceholder: Color,
    val composerAttach: ChatTonal,
    val composerAttachStreaming: ChatTonal,
    val reasoningNone: ChatTonal,
    val reasoningLow: ChatTonal,
    val reasoningMedium: ChatTonal,
    val reasoningHigh: ChatTonal,
    val sendIdle: ChatTonal,
    val sendReady: ChatTonal,
    val sendStreaming: ChatTonal,
    val scrollFab: ChatTonal,
    val scrollFabBadge: ChatTonal,
    val codeBlock: ChatTonal,
    val codeKeyword: Color,
    val codeString: Color,
    val codeComment: Color,
    val codeNumber: Color,
    val codeChip: ChatTonal,
    /** Inline code: text colour at 0.15 behind the text, over the assistant bubble. */
    val inlineCode: ChatTonal,
    val tableHeader: ChatTonal,
    val tableBody: ChatTonal,
    val tableAlternate: ChatTonal,
    /** The working indicator's contained loading badge. */
    val workingBadge: ChatTonal,
    val emptyCard: ChatTonal,
    val emptyCardMuted: Color,
    val emptyTile: ChatTonal,
    val imageActionChip: ChatTonal,
    /** Image-generation loading card: primaryContainer at 0.55 over the assistant bubble. */
    val imageLoadingCard: ChatTonal,
    val imageLoadingMuted: Color,
    val attachmentRemove: ChatTonal,
    val plusMenuCamera: ChatTonal,
    val plusMenuAttach: ChatTonal,
    val plusMenuCreate: ChatTonal,
    /**
     * The full-screen image viewer: the scrim (0.96 over the screen surface) and the control
     * colour that reads on it, and the translucent control chip (content at 0.14 over the scrim).
     */
    val imageViewerScrim: ChatTonal,
    val imageViewerChip: ChatTonal,
    /** The fusion progress panel above the composer (surfaceContainerHigh) and its body text. */
    val fusionPanel: ChatTonal,
    /** Secondary line on the fusion panel ("n / m models complete"). */
    val fusionPanelMuted: Color,
    /** The fusion status line (tertiary accent) on the panel. */
    val fusionPanelAccent: Color,
    /** A per-model fusion card while streaming: tertiaryContainer at 0.25 over the panel. */
    val fusionCardStreaming: ChatTonal,
    /** The streaming dot and "streaming…" label on the streaming card. */
    val fusionCardStreamingAccent: Color,
    /** A per-model fusion card once done: surfaceContainerHighest at 0.5 over the panel. */
    val fusionCardDone: ChatTonal,
    /** The done dot on the finished card. */
    val fusionCardDoneAccent: Color,
    /** The conversation-load error screen (on the scaffold's surfaceContainer): title and body. */
    val screenError: ChatTonal,
    val screenErrorMuted: Color,
    /** The reasoning selector while streaming: the same neutral fill as the plus button. */
    val reasoningDisabled: ChatTonal
) {
    /** Every container/content pair with its required ratio, one per fill the container can present. */
    fun pairs(): List<AssistantContrastPair> = buildList {
        fun add(name: String, fill: Color, content: Color, minimum: Double) =
            add(AssistantContrastPair(name, fill, content, minimum))
        fun add(name: String, fills: List<Color>, content: Color, minimum: Double) =
            fills.forEachIndexed { i, f -> add(if (fills.size == 1) name else "$name[$i]", f, content, minimum) }
        fun text(name: String, t: ChatTonal) = add(name, t.fill, t.content, ChatContrast.BODY_TEXT)
        fun icon(name: String, t: ChatTonal) = add(name, t.fill, t.content, ChatContrast.ICON)

        text("header title", header)
        add("header muted", header.fill, headerMuted, ChatContrast.BODY_TEXT)
        add("header pulse", header.fill, headerPulse, ChatContrast.ICON)
        text("ephemeral header title", ephemeralHeader)
        add("ephemeral header muted", ephemeralHeader.fill, ephemeralHeaderMuted, ChatContrast.BODY_TEXT)
        add("ephemeral header pulse", ephemeralHeader.fill, ephemeralHeaderPulse, ChatContrast.ICON)
        text("user bubble", userBubble)
        add("user quote", userBubble.fill, userQuote, ChatContrast.BODY_TEXT)
        text("assistant bubble", assistantBubble)
        text("assistant errored bubble", assistantErroredBubble)
        add("assistant muted", assistantBubble.fill, assistantMuted, ChatContrast.BODY_TEXT)
        add("assistant error", listOf(assistantBubble.fill, assistantErroredBubble.fill), assistantError, ChatContrast.BODY_TEXT)
        add("assistant link", assistantBubble.fill, assistantLink, ChatContrast.BODY_TEXT)
        text("system bubble", systemBubble)
        add("thinking title", thinkingHeaderFills, thinkingTitle, ChatContrast.BODY_TEXT)
        add("thinking preview", thinkingHeaderFills, thinkingPreview, ChatContrast.BODY_TEXT)
        add("thinking chevron", thinkingHeaderFills, thinkingChevron, ChatContrast.ICON)
        add("thinking body", assistantBubble.fill, thinkingBody, ChatContrast.BODY_TEXT)
        text("memory saved chip", memorySavedChip)
        text("memory used chip", memoryUsedChip)
        text("memory panel", memoryPanel)
        add("memory panel title", memoryPanel.fill, memoryPanelTitle, ChatContrast.BODY_TEXT)
        text("sources header collapsed", sourcesHeaderCollapsed)
        text("sources header expanded", sourcesHeaderExpanded)
        add("sources header muted collapsed", sourcesHeaderCollapsed.fill, sourcesHeaderMutedCollapsed, ChatContrast.BODY_TEXT)
        add("sources header muted expanded", sourcesHeaderExpanded.fill, sourcesHeaderMutedExpanded, ChatContrast.BODY_TEXT)
        icon("action standard", actionStandard)
        icon("action primary", actionPrimary)
        icon("action selected", actionSelected)
        // The stats pill shows labelSmall text on the selected tone.
        text("action stats text", actionSelected)
        add("meta label", listBackground, metaLabel, ChatContrast.BODY_TEXT)
        text("date pill", datePill)
        for ((badge, chip) in headerChips) text("header chip $badge", chip)
        add("model picker", modelPickerFills, modelPicker, ChatContrast.BODY_TEXT)
        // Disabled while streaming: still identifies the model, so it must stay legible as a graphic.
        add("model picker disabled", modelPickerFills, modelPickerDisabled, ChatContrast.ICON)
        add("composer text", composerFieldFills, composerText, ChatContrast.BODY_TEXT)
        add("composer placeholder", composerFieldFills, composerPlaceholder, ChatContrast.BODY_TEXT)
        icon("composer attach", composerAttach)
        icon("composer attach streaming", composerAttachStreaming)
        icon("reasoning none", reasoningNone)
        icon("reasoning low", reasoningLow)
        icon("reasoning medium", reasoningMedium)
        icon("reasoning high", reasoningHigh)
        icon("send idle", sendIdle)
        icon("send ready", sendReady)
        icon("send streaming", sendStreaming)
        text("scroll fab", scrollFab)
        text("scroll fab badge", scrollFabBadge)
        text("code block", codeBlock)
        add("code keyword", codeBlock.fill, codeKeyword, ChatContrast.BODY_TEXT)
        add("code string", codeBlock.fill, codeString, ChatContrast.BODY_TEXT)
        add("code comment", codeBlock.fill, codeComment, ChatContrast.BODY_TEXT)
        add("code number", codeBlock.fill, codeNumber, ChatContrast.BODY_TEXT)
        text("code chip", codeChip)
        text("inline code", inlineCode)
        text("table header", tableHeader)
        text("table body", tableBody)
        text("table alternate", tableAlternate)
        icon("working badge", workingBadge)
        text("empty card", emptyCard)
        add("empty card muted", emptyCard.fill, emptyCardMuted, ChatContrast.BODY_TEXT)
        icon("empty tile", emptyTile)
        text("image action chip", imageActionChip)
        text("image loading card", imageLoadingCard)
        add("image loading muted", imageLoadingCard.fill, imageLoadingMuted, ChatContrast.BODY_TEXT)
        icon("attachment remove", attachmentRemove)
        text("plus menu camera", plusMenuCamera)
        text("plus menu attach", plusMenuAttach)
        text("plus menu create", plusMenuCreate)
        icon("image viewer scrim", imageViewerScrim)
        icon("image viewer chip", imageViewerChip)
        text("fusion panel", fusionPanel)
        add("fusion panel muted", fusionPanel.fill, fusionPanelMuted, ChatContrast.BODY_TEXT)
        add("fusion panel accent", fusionPanel.fill, fusionPanelAccent, ChatContrast.BODY_TEXT)
        text("fusion card streaming", fusionCardStreaming)
        add("fusion card streaming accent", fusionCardStreaming.fill, fusionCardStreamingAccent, ChatContrast.BODY_TEXT)
        text("fusion card done", fusionCardDone)
        add("fusion card done accent", fusionCardDone.fill, fusionCardDoneAccent, ChatContrast.ICON)
        text("screen error title", screenError)
        add("screen error body", screenError.fill, screenErrorMuted, ChatContrast.BODY_TEXT)
        icon("reasoning disabled", reasoningDisabled)
    }

    companion object {
        fun resolve(scheme: ColorScheme, family: AppPreferences.ChatBubbleStyle): ChatSurfacePalette {
            val body = ChatContrast.BODY_TEXT
            val icon = ChatContrast.ICON
            val onSurfaceOrInverse = listOf(scheme.onSurface, scheme.inverseOnSurface)

            /** Keeps [preferred] when it reads on [fill], else the next readable role. */
            fun tonal(fill: Color, preferred: Color, minimum: Double): ChatTonal =
                ChatTonal(fill, ChatContrast.pick(fill, listOf(preferred) + onSurfaceOrInverse, minimum))

            /** A faded tone when it reads, else the solid roles. */
            fun faded(
                fills: List<Color>,
                base: Color,
                alpha: Float,
                minimum: Double,
                more: List<Color> = emptyList()
            ): Color =
                ChatContrast.pick(fills, listOf(base.copy(alpha = alpha), base) + more + onSurfaceOrInverse, minimum)

            val listBackground = scheme.surfaceContainerLow
            val headerFill = scheme.surfaceContainer
            val ephemeralFill = scheme.tertiaryContainer
            val userFill = ChatContrast.userBubbleFill(scheme, family)
            val assistantFill = ChatContrast.assistantBubbleFill(scheme, family)
            val erroredFill = ChatContrast.assistantErroredFill(scheme, family)
            val systemFill = ChatContrast.systemBubbleFill(scheme)
            val codeFill = ChatContrast.codeBlockFill(scheme)

            val assistant = tonal(assistantFill, scheme.onSurface, body)
            val user = tonal(userFill, scheme.onPrimaryContainer, body)
            val thinkingFills = listOf(
                ChatContrast.over(assistant.content.copy(alpha = 0.07f), assistantFill),
                ChatContrast.over(assistant.content.copy(alpha = 0.04f), assistantFill)
            )
            val memoryPanelFill = ChatContrast.over(scheme.surfaceContainerHighest.copy(alpha = 0.86f), assistantFill)
            val sourcesExpandedFill = ChatContrast.over(scheme.secondaryContainer.copy(alpha = 0.72f), assistantFill)
            val sourcesCollapsed = tonal(scheme.surfaceContainerHigh, scheme.onSurfaceVariant, body)
            val sourcesExpanded = tonal(sourcesExpandedFill, scheme.onSecondaryContainer, body)
            val modelPickerFills = listOf(
                ChatContrast.over(scheme.secondaryContainer.copy(alpha = 0.72f), headerFill),
                scheme.secondaryContainer
            )
            val composerFills = listOf(scheme.surfaceContainerHigh, scheme.surfaceContainerHighest)
            val imageLoadingFill = ChatContrast.over(scheme.primaryContainer.copy(alpha = 0.55f), assistantFill)
            val inlineCodeFill = ChatContrast.over(assistant.content.copy(alpha = 0.15f), assistantFill)
            // ImageViewerDialog: the scrim role at 0.96 over whatever screen is underneath (taken as
            // `surface`); inverseOnSurface is the M3 role for content on an inverse fill, but on a
            // dark theme it is itself dark, so the resolver steps to onSurface there.
            val scrimFill = ChatContrast.over(scheme.scrim.copy(alpha = 0.96f), scheme.surface)
            val imageViewerContent = ChatContrast.pick(
                scrimFill, listOf(scheme.inverseOnSurface, scheme.onSurface, scheme.inverseSurface), icon
            )
            val imageViewerChipFill = ChatContrast.over(imageViewerContent.copy(alpha = 0.14f), scrimFill)
            // FusionProgressView: the panel is surfaceContainerHigh; each model card is a
            // translucent tint over it, so its text is resolved on the composited colour.
            val fusionPanelFill = scheme.surfaceContainerHigh
            val fusionStreamingFill = ChatContrast.over(scheme.tertiaryContainer.copy(alpha = 0.25f), fusionPanelFill)
            val fusionDoneFill = ChatContrast.over(scheme.surfaceContainerHighest.copy(alpha = 0.5f), fusionPanelFill)

            val chips = ChatHeaderBadge.values().associateWith { badge ->
                when (badge) {
                    ChatHeaderBadge.Temporary -> tonal(scheme.surfaceContainerHigh, scheme.onSurfaceVariant, body)
                    ChatHeaderBadge.Archived -> tonal(scheme.secondaryContainer, scheme.onSecondaryContainer, body)
                    ChatHeaderBadge.ContextHigh -> tonal(scheme.tertiaryContainer, scheme.onTertiaryContainer, body)
                    ChatHeaderBadge.ContextCritical -> tonal(scheme.errorContainer, scheme.onErrorContainer, body)
                    ChatHeaderBadge.Branched -> tonal(scheme.primaryContainer, scheme.onPrimaryContainer, body)
                }
            }

            return ChatSurfacePalette(
                listBackground = listBackground,
                header = tonal(headerFill, scheme.onSurface, body),
                headerMuted = ChatContrast.pick(headerFill, listOf(scheme.onSurfaceVariant) + onSurfaceOrInverse, body),
                headerPulse = ChatContrast.pick(headerFill, listOf(scheme.primary, scheme.onSurface, scheme.inverseOnSurface), icon),
                ephemeralHeader = tonal(ephemeralFill, scheme.onTertiaryContainer, body),
                ephemeralHeaderMuted = faded(listOf(ephemeralFill), scheme.onTertiaryContainer, 0.78f, body),
                ephemeralHeaderPulse = ChatContrast.pick(
                    ephemeralFill, listOf(scheme.primary, scheme.onTertiaryContainer) + onSurfaceOrInverse, icon
                ),
                userBubble = user,
                userQuote = faded(listOf(userFill), user.content, 0.65f, body),
                assistantBubble = assistant,
                assistantErroredBubble = tonal(erroredFill, scheme.onSurface, body),
                assistantMuted = ChatContrast.pick(assistantFill, listOf(scheme.onSurfaceVariant) + onSurfaceOrInverse, body),
                assistantError = ChatContrast.pick(
                    listOf(assistantFill, erroredFill),
                    listOf(scheme.error, scheme.onErrorContainer) + onSurfaceOrInverse, body
                ),
                assistantLink = ChatContrast.pick(
                    assistantFill, listOf(scheme.primary, scheme.onPrimaryContainer) + onSurfaceOrInverse, body
                ),
                systemBubble = tonal(systemFill, scheme.onTertiaryContainer, body),
                thinkingHeaderFills = thinkingFills,
                thinkingTitle = faded(thinkingFills, assistant.content, 0.8f, body),
                thinkingPreview = faded(thinkingFills, assistant.content, 0.6f, body, listOf(assistant.content.copy(alpha = 0.8f))),
                thinkingChevron = faded(thinkingFills, assistant.content, 0.75f, icon),
                thinkingBody = faded(listOf(assistantFill), assistant.content, 0.7f, body),
                memorySavedChip = tonal(scheme.tertiaryContainer, scheme.onTertiaryContainer, body),
                memoryUsedChip = tonal(scheme.secondaryContainer, scheme.onSecondaryContainer, body),
                memoryPanel = tonal(memoryPanelFill, scheme.onSurfaceVariant, body),
                memoryPanelTitle = faded(listOf(memoryPanelFill), scheme.onSurfaceVariant, 0.72f, body),
                sourcesHeaderCollapsed = sourcesCollapsed,
                sourcesHeaderExpanded = sourcesExpanded,
                sourcesHeaderMutedCollapsed = faded(listOf(sourcesCollapsed.fill), sourcesCollapsed.content, 0.72f, body),
                sourcesHeaderMutedExpanded = faded(listOf(sourcesExpandedFill), sourcesExpanded.content, 0.72f, body),
                actionStandard = tonal(scheme.surfaceContainerHigh, scheme.onSurfaceVariant, icon),
                actionPrimary = tonal(scheme.secondaryContainer, scheme.onSecondaryContainer, icon),
                actionSelected = tonal(scheme.primaryContainer, scheme.onPrimaryContainer, body),
                metaLabel = faded(listOf(listBackground), scheme.onSurfaceVariant, 0.7f, body),
                datePill = tonal(scheme.secondaryContainer, scheme.onSecondaryContainer, body),
                headerChips = chips,
                modelPickerFills = modelPickerFills,
                modelPicker = ChatContrast.pick(modelPickerFills, listOf(scheme.onSecondaryContainer) + onSurfaceOrInverse, body),
                modelPickerDisabled = faded(modelPickerFills, scheme.onSecondaryContainer, 0.6f, icon),
                composerFieldFills = composerFills,
                composerText = ChatContrast.pick(composerFills, onSurfaceOrInverse, body),
                composerPlaceholder = ChatContrast.pick(composerFills, listOf(scheme.onSurfaceVariant) + onSurfaceOrInverse, body),
                composerAttach = tonal(scheme.tertiaryContainer, scheme.onTertiaryContainer, icon),
                composerAttachStreaming = tonal(scheme.surfaceContainerHigh, scheme.onSurfaceVariant, icon),
                reasoningNone = tonal(scheme.surfaceContainerHigh, scheme.onSurfaceVariant, icon),
                reasoningLow = tonal(scheme.tertiaryContainer, scheme.onTertiaryContainer, icon),
                reasoningMedium = tonal(scheme.secondaryContainer, scheme.onSecondaryContainer, icon),
                reasoningHigh = tonal(scheme.primaryContainer, scheme.onPrimaryContainer, icon),
                sendIdle = tonal(scheme.surfaceContainerHigh, scheme.onSurfaceVariant, icon),
                sendReady = tonal(scheme.primary, scheme.onPrimary, icon),
                sendStreaming = tonal(scheme.errorContainer, scheme.onErrorContainer, icon),
                scrollFab = tonal(scheme.primaryContainer, scheme.onPrimaryContainer, body),
                scrollFabBadge = tonal(scheme.primary, scheme.onPrimary, body),
                codeBlock = tonal(codeFill, scheme.onSurface, body),
                codeKeyword = ChatContrast.pick(codeFill, listOf(scheme.primary, scheme.onPrimaryContainer) + onSurfaceOrInverse, body),
                codeString = ChatContrast.pick(codeFill, listOf(scheme.tertiary, scheme.onTertiaryContainer) + onSurfaceOrInverse, body),
                codeComment = ChatContrast.pick(codeFill, listOf(scheme.onSurfaceVariant) + onSurfaceOrInverse, body),
                codeNumber = ChatContrast.pick(codeFill, listOf(scheme.secondary, scheme.onSecondaryContainer) + onSurfaceOrInverse, body),
                codeChip = tonal(scheme.surfaceContainerHigh, scheme.onSurfaceVariant, body),
                inlineCode = ChatTonal(inlineCodeFill, assistant.content),
                tableHeader = tonal(scheme.surfaceContainerHigh, scheme.onSurface, body),
                tableBody = tonal(scheme.surfaceContainerLow, scheme.onSurface, body),
                tableAlternate = tonal(scheme.surfaceContainer, scheme.onSurface, body),
                workingBadge = tonal(scheme.primaryContainer, scheme.primary, icon),
                emptyCard = tonal(scheme.surfaceContainer, scheme.onSurface, body),
                emptyCardMuted = ChatContrast.pick(scheme.surfaceContainer, listOf(scheme.onSurfaceVariant) + onSurfaceOrInverse, body),
                emptyTile = tonal(scheme.primaryContainer, scheme.onPrimaryContainer, icon),
                imageActionChip = tonal(scheme.surfaceContainerHighest, scheme.onSurfaceVariant, body),
                imageLoadingCard = tonal(imageLoadingFill, scheme.onPrimaryContainer, body),
                imageLoadingMuted = faded(listOf(imageLoadingFill), scheme.onPrimaryContainer, 0.72f, body),
                attachmentRemove = tonal(scheme.errorContainer, scheme.onErrorContainer, icon),
                plusMenuCamera = tonal(scheme.secondaryContainer, scheme.onSecondaryContainer, body),
                plusMenuAttach = tonal(scheme.tertiaryContainer, scheme.onTertiaryContainer, body),
                plusMenuCreate = tonal(scheme.primaryContainer, scheme.onPrimaryContainer, body),
                imageViewerScrim = ChatTonal(scrimFill, imageViewerContent),
                imageViewerChip = ChatTonal(imageViewerChipFill, imageViewerContent),
                fusionPanel = tonal(fusionPanelFill, scheme.onSurface, body),
                fusionPanelMuted = ChatContrast.pick(fusionPanelFill, listOf(scheme.onSurfaceVariant) + onSurfaceOrInverse, body),
                fusionPanelAccent = ChatContrast.pick(
                    fusionPanelFill, listOf(scheme.tertiary, scheme.onTertiaryContainer) + onSurfaceOrInverse, body
                ),
                fusionCardStreaming = tonal(fusionStreamingFill, scheme.onSurfaceVariant, body),
                fusionCardStreamingAccent = ChatContrast.pick(
                    fusionStreamingFill, listOf(scheme.tertiary, scheme.onTertiaryContainer) + onSurfaceOrInverse, body
                ),
                fusionCardDone = tonal(fusionDoneFill, scheme.onSurfaceVariant, body),
                fusionCardDoneAccent = ChatContrast.pick(
                    fusionDoneFill, listOf(scheme.primary, scheme.onPrimaryContainer) + onSurfaceOrInverse, icon
                ),
                screenError = tonal(scheme.surfaceContainer, scheme.error, body),
                screenErrorMuted = ChatContrast.pick(scheme.surfaceContainer, listOf(scheme.onSurfaceVariant) + onSurfaceOrInverse, body),
                reasoningDisabled = tonal(scheme.surfaceContainerHigh, scheme.onSurfaceVariant, icon)
            )
        }
    }
}

/** The palette for the current theme and bubble family; recomputed only when either changes. */
@Composable
internal fun rememberChatSurfacePalette(): ChatSurfacePalette {
    val scheme = MaterialTheme.colorScheme
    val family = LocalChatBubbleStyle.current
    return remember(scheme, family) { ChatSurfacePalette.resolve(scheme, family) }
}
