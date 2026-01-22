package com.materialchat.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive Shape System for MaterialChat.
 *
 * M3 Expressive uses larger, friendlier corner radii for a more
 * approachable, playful feel. Key changes from standard M3:
 *
 * - FABs use pill-like shapes (28dp+ corners)
 * - Message bubbles have larger, asymmetric corners
 * - Cards and containers use generous rounding
 * - Interactive elements morph their shape on press
 *
 * Reference: https://m3.material.io/styles/shape/overview
 */

/**
 * Standard Material 3 Expressive shape configuration.
 * These are the base shapes used by Material components.
 */
val ExpressiveShapes = Shapes(
    // Extra small - chips, small badges, compact elements
    extraSmall = RoundedCornerShape(12.dp),

    // Small - buttons, text fields, smaller cards
    small = RoundedCornerShape(16.dp),

    // Medium - cards, dialogs, menus
    medium = RoundedCornerShape(24.dp),

    // Large - bottom sheets, navigation drawers, large cards
    large = RoundedCornerShape(32.dp),

    // Extra large - full-screen dialogs, hero containers
    extraLarge = RoundedCornerShape(40.dp)
)

/**
 * Message bubble shapes for chat UI.
 * Uses asymmetric corners to indicate message direction.
 */
object ExpressiveMessageBubbleShapes {

    // =========================================================================
    // SINGLE MESSAGE SHAPES (standalone messages)
    // =========================================================================

    /**
     * User message bubble - tail on top-right.
     * Large friendly corners with a small directional corner.
     */
    val UserBubble = RoundedCornerShape(
        topStart = 28.dp,
        topEnd = 8.dp,      // Directional tail
        bottomStart = 28.dp,
        bottomEnd = 28.dp
    )

    /**
     * Assistant message bubble - tail on top-left.
     */
    val AssistantBubble = RoundedCornerShape(
        topStart = 8.dp,    // Directional tail
        topEnd = 28.dp,
        bottomStart = 28.dp,
        bottomEnd = 28.dp
    )

    /**
     * System message bubble - symmetric, neutral appearance.
     */
    val SystemBubble = RoundedCornerShape(20.dp)

    // =========================================================================
    // GROUPED MESSAGE SHAPES (consecutive messages from same sender)
    // =========================================================================

    /**
     * First message in a group - has the directional tail.
     */
    val UserBubbleFirst = RoundedCornerShape(
        topStart = 28.dp,
        topEnd = 8.dp,      // Tail
        bottomStart = 28.dp,
        bottomEnd = 8.dp    // Tight corner for grouping
    )

    val AssistantBubbleFirst = RoundedCornerShape(
        topStart = 8.dp,    // Tail
        topEnd = 28.dp,
        bottomStart = 8.dp, // Tight corner for grouping
        bottomEnd = 28.dp
    )

    /**
     * Middle messages in a group - tight corners on sender's side.
     */
    val UserBubbleMiddle = RoundedCornerShape(
        topStart = 28.dp,
        topEnd = 8.dp,
        bottomStart = 28.dp,
        bottomEnd = 8.dp
    )

    val AssistantBubbleMiddle = RoundedCornerShape(
        topStart = 8.dp,
        topEnd = 28.dp,
        bottomStart = 8.dp,
        bottomEnd = 28.dp
    )

    /**
     * Last message in a group - returns to full rounding at bottom.
     */
    val UserBubbleLast = RoundedCornerShape(
        topStart = 28.dp,
        topEnd = 8.dp,
        bottomStart = 28.dp,
        bottomEnd = 28.dp   // Full rounding
    )

    val AssistantBubbleLast = RoundedCornerShape(
        topStart = 8.dp,
        topEnd = 28.dp,
        bottomStart = 28.dp, // Full rounding
        bottomEnd = 28.dp
    )

    /**
     * Continuation bubble - used when time gap is small.
     */
    val ContinuationBubble = RoundedCornerShape(16.dp)
}

/**
 * Custom shapes for various UI components.
 * Updated for M3 Expressive with larger, friendlier radii.
 */
object ExpressiveCustomShapes {

    // =========================================================================
    // FLOATING ACTION BUTTONS - Pill-like for M3 Expressive
    // =========================================================================

    /**
     * Standard FAB - pill-shaped (was 16dp, now 28dp)
     */
    val Fab = RoundedCornerShape(28.dp)

    /**
     * Small FAB
     */
    val SmallFab = RoundedCornerShape(16.dp)

    /**
     * Large FAB - even more rounded
     */
    val LargeFab = RoundedCornerShape(32.dp)

    /**
     * Extended FAB - maintains pill shape
     */
    val ExtendedFab = RoundedCornerShape(28.dp)

    // =========================================================================
    // BUTTONS
    // =========================================================================

    /**
     * Standard button shape
     */
    val Button = RoundedCornerShape(16.dp)

    /**
     * Large button shape
     */
    val ButtonLarge = RoundedCornerShape(20.dp)

    /**
     * Icon button (circular)
     */
    val IconButton = CircleShape

    /**
     * Tonal button
     */
    val TonalButton = RoundedCornerShape(16.dp)

    // =========================================================================
    // CONTAINERS & SURFACES
    // =========================================================================

    /**
     * Bottom sheet - very rounded top corners
     */
    val BottomSheet = RoundedCornerShape(
        topStart = 32.dp,
        topEnd = 32.dp,
        bottomStart = 0.dp,
        bottomEnd = 0.dp
    )

    /**
     * Dialog shape - very rounded
     */
    val Dialog = RoundedCornerShape(28.dp)

    /**
     * Card shape
     */
    val Card = RoundedCornerShape(20.dp)

    /**
     * Elevated card shape
     */
    val ElevatedCard = RoundedCornerShape(24.dp)

    /**
     * Provider card in settings
     */
    val ProviderCard = RoundedCornerShape(24.dp)

    /**
     * Conversation list item
     */
    val ConversationItem = RoundedCornerShape(20.dp)

    // =========================================================================
    // INPUT FIELDS
    // =========================================================================

    /**
     * Message input container - very rounded pill shape
     */
    val MessageInputContainer = RoundedCornerShape(32.dp)

    /**
     * Search bar - pill shape
     */
    val SearchBar = RoundedCornerShape(28.dp)

    /**
     * Text field
     */
    val TextField = RoundedCornerShape(16.dp)

    // =========================================================================
    // CODE & CONTENT
    // =========================================================================

    /**
     * Code block in chat messages
     */
    val CodeBlock = RoundedCornerShape(16.dp)

    /**
     * Inline code spans
     */
    val InlineCode = RoundedCornerShape(6.dp)

    // =========================================================================
    // MISC COMPONENTS
    // =========================================================================

    /**
     * Action buttons on message bubbles
     */
    val MessageActionButton = RoundedCornerShape(16.dp)

    /**
     * Send button - circular
     */
    val SendButton = CircleShape

    /**
     * Dropdown menus
     */
    val Dropdown = RoundedCornerShape(16.dp)

    /**
     * Model picker dropdown
     */
    val ModelPicker = RoundedCornerShape(20.dp)

    /**
     * Snackbar
     */
    val Snackbar = RoundedCornerShape(16.dp)

    /**
     * Pill shape for tags and badges
     */
    val Pill = RoundedCornerShape(50)

    /**
     * Top app bar with bottom rounding (for layered effect)
     */
    val TopAppBarLayered = RoundedCornerShape(
        topStart = 0.dp,
        topEnd = 0.dp,
        bottomStart = 24.dp,
        bottomEnd = 24.dp
    )

    /**
     * Main content container (rounded top for layered effect)
     */
    val MainContentContainer = RoundedCornerShape(
        topStart = 28.dp,
        topEnd = 28.dp,
        bottomStart = 0.dp,
        bottomEnd = 0.dp
    )

    /**
     * Chip shape
     */
    val Chip = RoundedCornerShape(8.dp)

    /**
     * Filter chip (slightly more rounded)
     */
    val FilterChip = RoundedCornerShape(12.dp)

    /**
     * Tooltip
     */
    val Tooltip = RoundedCornerShape(8.dp)

    /**
     * Progress indicator track
     */
    val ProgressTrack = RoundedCornerShape(4.dp)
}
