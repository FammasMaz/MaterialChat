package com.materialchat.ui.screens.chat.components

import com.materialchat.ui.screens.chat.ContextWindowLevel

/** Title a conversation carries before the model has named it. */
internal const val NEW_CHAT_TITLE = "New Chat"

/**
 * Status chips the header shows under the title, in display order. The model chip is not a
 * status and is always present, so it is not listed here.
 */
enum class ChatHeaderBadge {
    Temporary,
    Archived,
    ContextHigh,
    ContextCritical,
    Branched
}

/** Resolves which status chips the header shows for the given conversation state. */
fun resolveChatHeaderBadges(
    isEphemeral: Boolean,
    isArchived: Boolean,
    hasBranches: Boolean,
    contextLevel: ContextWindowLevel
): List<ChatHeaderBadge> = buildList {
    if (isEphemeral) add(ChatHeaderBadge.Temporary)
    if (isArchived) add(ChatHeaderBadge.Archived)
    when (contextLevel) {
        ContextWindowLevel.Critical -> add(ChatHeaderBadge.ContextCritical)
        ContextWindowLevel.High -> add(ChatHeaderBadge.ContextHigh)
        else -> Unit
    }
    if (hasBranches) add(ChatHeaderBadge.Branched)
}

/**
 * The presence line the header shows while a reply is generating; null when idle. Shares the
 * working indicator's wording so the bubble and the header say the same thing.
 */
fun chatHeaderStreamingLabel(isStreaming: Boolean): String? =
    if (isStreaming) CHAT_WORKING_STATUS else null

/**
 * Whether a title change is the model naming a fresh conversation, which gets the character
 * reveal. Renames of an already-named chat and the initial placeholder update directly.
 */
fun shouldRevealTitle(previous: String, next: String): Boolean =
    next != previous && previous == NEW_CHAT_TITLE && next != NEW_CHAT_TITLE

/** The prefix of [title] visible at reveal [progress] in 0..1; the whole title at 1. */
fun revealedTitle(title: String, progress: Float): String {
    if (progress >= 1f) return title
    val visible = (title.length * progress.coerceIn(0f, 1f)).toInt()
    return title.take(visible)
}

/**
 * Container tone of the chat header. [Standard] is the scaffold's own `surfaceContainer`,
 * [Ephemeral] the temporary-chat `tertiaryContainer`.
 */
enum class ChatHeaderTone { Standard, Ephemeral }

/**
 * Resolves the header's container tone. Deliberately independent of scroll position and of
 * the fold state: the header is cut with 24dp bottom corners over a scaffold of the same
 * colour, so any tonal lift while content scrolls under it both shifts the colour and reveals
 * those corners. Only the conversation kind changes the tone.
 */
@Suppress("UNUSED_PARAMETER")
fun resolveChatHeaderTone(
    isEphemeral: Boolean,
    contentScrolledUnder: Boolean = false,
    isCollapsed: Boolean = false
): ChatHeaderTone =
    if (isEphemeral) ChatHeaderTone.Ephemeral else ChatHeaderTone.Standard
