package com.materialchat.ui.screens.chat.components

/**
 * How the chat screen answers the system back gesture.
 *
 * [System]: nothing in the chat intercepts back. The NavHost pops the chat itself, which is the
 * only way the native predictive-back animation (the cross-screen pop-exit driven by the
 * gesture) can play. A plain `BackHandler` that is always enabled registers an app callback and
 * the system then shows no animation at all.
 *
 * [InterceptForEphemeralCleanup]: a temporary conversation must be deleted (or, if a reply is
 * still streaming, promoted to a normal chat) before the screen leaves, so the chat intercepts
 * back with a `PredictiveBackHandler`, drives the screen with the gesture progress
 * ([chatBackTransform]) and runs the cleanup when the gesture commits.
 */
enum class ChatBackHandling { System, InterceptForEphemeralCleanup }

/** Only a temporary conversation has work to do before it can be left. */
fun resolveChatBackHandling(isEphemeral: Boolean): ChatBackHandling =
    if (isEphemeral) ChatBackHandling.InterceptForEphemeralCleanup else ChatBackHandling.System

/** The screen transform at a given back-gesture progress. */
data class ChatBackTransform(
    val scale: Float,
    /** Horizontal shift as a fraction of the screen width, toward the trailing edge. */
    val translationXFraction: Float,
    val alpha: Float
)

/** Matches the NavHost pop-exit transition so the committed pop continues the gesture. */
internal const val CHAT_BACK_MIN_SCALE = 0.9f
internal const val CHAT_BACK_MAX_TRANSLATION_FRACTION = 0.25f
internal const val CHAT_BACK_MIN_ALPHA = 0.7f

/**
 * Maps gesture progress (0 = untouched, 1 = committed) onto the screen transform. Linear in
 * progress so the screen tracks the finger; the spring only runs on cancel.
 */
fun chatBackTransform(progress: Float): ChatBackTransform {
    val p = if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)
    return ChatBackTransform(
        scale = 1f - (1f - CHAT_BACK_MIN_SCALE) * p,
        translationXFraction = CHAT_BACK_MAX_TRANSLATION_FRACTION * p,
        alpha = 1f - (1f - CHAT_BACK_MIN_ALPHA) * p
    )
}
