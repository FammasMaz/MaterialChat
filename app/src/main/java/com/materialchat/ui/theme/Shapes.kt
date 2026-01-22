package com.materialchat.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive Shape system for MaterialChat.
 *
 * Inspired by Google Messages - uses very rounded, friendly shapes
 * for a modern, approachable feel.
 */

/**
 * Standard Material 3 Expressive shape configuration.
 * Uses larger corner radii for the playful, friendly aesthetic.
 */
val MaterialChatShapes = Shapes(
    // Extra small - chips, small badges
    extraSmall = RoundedCornerShape(12.dp),

    // Small - buttons, text fields
    small = RoundedCornerShape(16.dp),

    // Medium - cards, dialogs
    medium = RoundedCornerShape(24.dp),

    // Large - bottom sheets, navigation drawers
    large = RoundedCornerShape(32.dp),

    // Extra large - full-screen dialogs
    extraLarge = RoundedCornerShape(40.dp)
)

/**
 * Custom shape for message bubbles.
 * Uses larger radii for the expressive, friendly feel.
 */
object MessageBubbleShapes {
    /**
     * User message bubble shape.
     * Very rounded with a small corner on top-right to indicate origin.
     */
    val UserBubble = RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 6.dp,
        bottomStart = 24.dp,
        bottomEnd = 24.dp
    )

    /**
     * Assistant message bubble shape.
     * Very rounded with a small corner on top-left to indicate origin.
     */
    val AssistantBubble = RoundedCornerShape(
        topStart = 6.dp,
        topEnd = 24.dp,
        bottomStart = 24.dp,
        bottomEnd = 24.dp
    )

    /**
     * System message bubble shape.
     * Fully rounded corners for neutral appearance.
     */
    val SystemBubble = RoundedCornerShape(20.dp)

    /**
     * Continuation message shape (for messages in a sequence).
     */
    val ContinuationBubble = RoundedCornerShape(16.dp)
}

/**
 * Custom shapes for various UI components.
 * Updated for M3 Expressive with larger, friendlier radii.
 */
object CustomShapes {
    /**
     * Shape for FAB (Floating Action Button) - rounded rectangle.
     */
    val Fab = RoundedCornerShape(16.dp)

    /**
     * Extended FAB shape - rounded rectangle.
     */
    val ExtendedFab = RoundedCornerShape(16.dp)

    /**
     * Shape for bottom sheets - very rounded top corners.
     */
    val BottomSheet = RoundedCornerShape(
        topStart = 32.dp,
        topEnd = 32.dp,
        bottomStart = 0.dp,
        bottomEnd = 0.dp
    )

    /**
     * Shape for code blocks in chat messages.
     */
    val CodeBlock = RoundedCornerShape(16.dp)

    /**
     * Shape for inline code spans.
     */
    val InlineCode = RoundedCornerShape(6.dp)

    /**
     * Shape for action buttons on message bubbles.
     */
    val MessageActionButton = RoundedCornerShape(16.dp)

    /**
     * Shape for the message input field container - very rounded.
     */
    val MessageInputContainer = RoundedCornerShape(32.dp)

    /**
     * Shape for the send button - circle.
     */
    val SendButton = CircleShape

    /**
     * Shape for provider cards in settings.
     */
    val ProviderCard = RoundedCornerShape(24.dp)

    /**
     * Shape for conversation list items - rounded like Google Messages.
     */
    val ConversationItem = RoundedCornerShape(20.dp)

    /**
     * Shape for dropdown menus.
     */
    val Dropdown = RoundedCornerShape(16.dp)

    /**
     * Shape for model picker dropdown.
     */
    val ModelPicker = RoundedCornerShape(20.dp)

    /**
     * Shape for snackbars.
     */
    val Snackbar = RoundedCornerShape(16.dp)

    /**
     * Pill shape for tags and badges.
     */
    val Pill = RoundedCornerShape(50)

    /**
     * Shape for dialogs - very rounded.
     */
    val Dialog = RoundedCornerShape(28.dp)

    /**
     * Shape for top app bar (when using shape).
     */
    val TopAppBar = RoundedCornerShape(
        topStart = 0.dp,
        topEnd = 0.dp,
        bottomStart = 24.dp,
        bottomEnd = 24.dp
    )

    /**
     * Shape for search bar - fully rounded pill shape.
     */
    val SearchBar = RoundedCornerShape(28.dp)
}
