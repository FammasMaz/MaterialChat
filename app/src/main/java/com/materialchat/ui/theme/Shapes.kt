package com.materialchat.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive Shape system for MaterialChat.
 *
 * The shape system defines corner radii for different component sizes.
 * Material 3 Expressive uses more pronounced, playful shapes compared
 * to standard Material 3.
 */

/**
 * Standard Material 3 shape configuration with slightly increased corner radii
 * for the expressive style.
 */
val MaterialChatShapes = Shapes(
    // Extra small - chips, small badges
    extraSmall = RoundedCornerShape(8.dp),

    // Small - buttons, text fields
    small = RoundedCornerShape(12.dp),

    // Medium - cards, dialogs
    medium = RoundedCornerShape(16.dp),

    // Large - bottom sheets, navigation drawers
    large = RoundedCornerShape(24.dp),

    // Extra large - full-screen dialogs
    extraLarge = RoundedCornerShape(32.dp)
)

/**
 * Custom shape for message bubbles.
 *
 * User messages have one square corner (top-right) to indicate direction.
 * Assistant messages have one square corner (top-left) to indicate direction.
 */
object MessageBubbleShapes {
    /**
     * User message bubble shape.
     * Rounded on top-left, bottom-left, bottom-right.
     * Square corner on top-right to show message origin.
     */
    val UserBubble = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 4.dp,
        bottomStart = 20.dp,
        bottomEnd = 20.dp
    )

    /**
     * Assistant message bubble shape.
     * Rounded on top-right, bottom-left, bottom-right.
     * Square corner on top-left to show message origin.
     */
    val AssistantBubble = RoundedCornerShape(
        topStart = 4.dp,
        topEnd = 20.dp,
        bottomStart = 20.dp,
        bottomEnd = 20.dp
    )

    /**
     * System message bubble shape.
     * Fully rounded corners for neutral appearance.
     */
    val SystemBubble = RoundedCornerShape(16.dp)

    /**
     * Continuation message shape (for messages in a sequence).
     * Smaller corner radii for compact appearance.
     */
    val ContinuationBubble = RoundedCornerShape(12.dp)
}

/**
 * Custom shapes for various UI components.
 */
object CustomShapes {
    /**
     * Shape for FAB (Floating Action Button).
     */
    val Fab = RoundedCornerShape(16.dp)

    /**
     * Extended FAB shape with more rounded corners.
     */
    val ExtendedFab = RoundedCornerShape(20.dp)

    /**
     * Shape for bottom sheets.
     */
    val BottomSheet = RoundedCornerShape(
        topStart = 28.dp,
        topEnd = 28.dp,
        bottomStart = 0.dp,
        bottomEnd = 0.dp
    )

    /**
     * Shape for code blocks in chat messages.
     */
    val CodeBlock = RoundedCornerShape(12.dp)

    /**
     * Shape for inline code spans.
     */
    val InlineCode = RoundedCornerShape(4.dp)

    /**
     * Shape for action buttons on message bubbles.
     */
    val MessageActionButton = RoundedCornerShape(12.dp)

    /**
     * Shape for the message input field container.
     */
    val MessageInputContainer = RoundedCornerShape(28.dp)

    /**
     * Shape for the send button.
     */
    val SendButton = CircleShape

    /**
     * Shape for provider cards in settings.
     */
    val ProviderCard = RoundedCornerShape(16.dp)

    /**
     * Shape for conversation list items.
     */
    val ConversationItem = RoundedCornerShape(12.dp)

    /**
     * Shape for dropdown menus.
     */
    val Dropdown = RoundedCornerShape(12.dp)

    /**
     * Shape for model picker dropdown.
     */
    val ModelPicker = RoundedCornerShape(16.dp)

    /**
     * Shape for snackbars.
     */
    val Snackbar = RoundedCornerShape(12.dp)

    /**
     * Pill shape for tags and badges.
     */
    val Pill = RoundedCornerShape(50)
}
