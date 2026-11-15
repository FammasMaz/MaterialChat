package com.materialchat.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle
import androidx.graphics.shapes.toPath

/**
 * M3 Expressive squircle: a rounded rectangle whose corners are smoothed with a
 * continuous curve instead of a circular arc. Reads softer than [RoundedCornerShape]
 * at the same radius, which is what M3E uses for cards, sheets and leading tiles.
 *
 * @param cornerRadius Corner radius; clamped to half the shorter side.
 * @param smoothing 0 = plain circular corner, 1 = maximum G2 smoothing.
 */
@Stable
class SquircleShape(
    private val cornerRadius: Dp,
    private val smoothing: Float = 0.65f
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        if (size.width <= 0f || size.height <= 0f) return Outline.Rectangle(size.toRect())
        val radiusPx = with(density) { cornerRadius.toPx() }
            .coerceIn(0f, minOf(size.width, size.height) / 2f)
        val polygon = RoundedPolygon.rectangle(
            width = size.width,
            height = size.height,
            rounding = CornerRounding(radius = radiusPx, smoothing = smoothing.coerceIn(0f, 1f)),
            centerX = size.width / 2f,
            centerY = size.height / 2f
        )
        return Outline.Generic(polygon.toPath().asComposePath())
    }

    override fun equals(other: Any?): Boolean =
        other is SquircleShape && other.cornerRadius == cornerRadius && other.smoothing == smoothing

    override fun hashCode(): Int = cornerRadius.hashCode() * 31 + smoothing.hashCode()
}

/**
 * Expressive container shape tokens. Use these for cards, sheets and leading tiles
 * where a squircle fits; keep [MaterialChatShapes] for component defaults.
 */
object ExpressiveShapes {
    /** Leading icon tiles in list rows (48dp). */
    val Tile = SquircleShape(18.dp)

    /** Cards and grouped list containers. */
    val Card = SquircleShape(28.dp)

    /** Modal and bottom sheets. */
    val Sheet = SquircleShape(32.dp)

    /** Selected menu/list rows inside a sheet or dropdown. */
    val MenuSelection = SquircleShape(16.dp)

    /** Dialogs: M3 extraLarge (28dp) as a squircle so they match [Card]. */
    val Dialog = SquircleShape(28.dp)

    /** Expanded disclosure panels inside bubbles (memory references, sources). */
    val DisclosurePanel = SquircleShape(20.dp)

    /**
     * Screen content surface that rounds into the top app bar. Only the top
     * corners are rounded, so this stays a [RoundedCornerShape].
     */
    val ScreenContainer = RoundedCornerShape(
        topStart = 28.dp,
        topEnd = 28.dp,
        bottomStart = 0.dp,
        bottomEnd = 0.dp
    )

    /** Swipe-reveal action buttons: resting radius and the emphasised (armed) radius. */
    object SwipeAction {
        val Resting = 22.dp
        val Emphasized = 28.dp
    }
}

/**
 * Material 3 Expressive Shape system for MaterialChat.
 *
 * Inspired by Google Messages - uses very rounded, friendly shapes
 * for a modern, approachable feel.
 */

/**
 * Standard Material 3 Expressive shape configuration.
 *
 * Radii follow the M3 Expressive shape scale, shifted one step up so the app keeps
 * its soft, rounded identity: 8 / 12 / 16 / 20 / 32 dp are the spec's
 * small, medium, large, largeIncreased and extraLargeIncreased tokens.
 */
val MaterialChatShapes = Shapes(
    // Extra small - chips, small badges (spec small: 8dp)
    extraSmall = RoundedCornerShape(8.dp),

    // Small - buttons, text fields (spec medium: 12dp)
    small = RoundedCornerShape(12.dp),

    // Medium - cards, dialogs (spec large: 16dp)
    medium = RoundedCornerShape(16.dp),

    // Large - bottom sheets, navigation drawers, list cards (spec largeIncreased: 20dp)
    large = RoundedCornerShape(20.dp),

    // Extra large - full-screen dialogs, hero containers, page sheets
    // (spec extraLargeIncreased: 32dp)
    extraLarge = RoundedCornerShape(32.dp)
)

/**
 * Custom shape for message bubbles.
 * Uses larger radii (28dp) for the M3 Expressive friendly feel.
 */
object MessageBubbleShapes {
    /**
     * User message bubble shape.
     * Very rounded with a small corner on top-right to indicate origin.
     */
    val UserBubble = RoundedCornerShape(
        topStart = 28.dp,
        topEnd = 8.dp,
        bottomStart = 28.dp,
        bottomEnd = 28.dp
    )

    /**
     * Assistant message bubble shape.
     * Very rounded with a small corner on top-left to indicate origin.
     */
    val AssistantBubble = RoundedCornerShape(
        topStart = 8.dp,
        topEnd = 28.dp,
        bottomStart = 28.dp,
        bottomEnd = 28.dp
    )

    /**
     * System message bubble shape.
     * Fully rounded corners for neutral appearance.
     */
    val SystemBubble = RoundedCornerShape(24.dp)

    /**
     * Continuation message shape (for messages in a sequence).
     */
    val ContinuationBubble = RoundedCornerShape(24.dp)
    
    /**
     * Grouped message shapes - for consecutive messages from same sender.
     * Creates a visual "stack" effect with varying corner radii.
     */
    object Grouped {
        // First message in a group (has tail corner)
        val UserFirst = RoundedCornerShape(
            topStart = 24.dp, topEnd = 8.dp,
            bottomStart = 24.dp, bottomEnd = 8.dp
        )
        val UserMiddle = RoundedCornerShape(
            topStart = 24.dp, topEnd = 8.dp,
            bottomStart = 24.dp, bottomEnd = 8.dp
        )
        val UserLast = RoundedCornerShape(
            topStart = 24.dp, topEnd = 8.dp,
            bottomStart = 24.dp, bottomEnd = 24.dp
        )
        
        val AssistantFirst = RoundedCornerShape(
            topStart = 8.dp, topEnd = 24.dp,
            bottomStart = 8.dp, bottomEnd = 24.dp
        )
        val AssistantMiddle = RoundedCornerShape(
            topStart = 8.dp, topEnd = 24.dp,
            bottomStart = 8.dp, bottomEnd = 24.dp
        )
        val AssistantLast = RoundedCornerShape(
            topStart = 8.dp, topEnd = 24.dp,
            bottomStart = 24.dp, bottomEnd = 24.dp
        )
    }
}

/**
 * Custom shapes for various UI components.
 * Updated for M3 Expressive with larger, friendlier radii.
 */
object CustomShapes {
    /**
     * Shape for FAB (Floating Action Button) - M3 Expressive spec.
     * 16dp corners for standard FAB (56dp container).
     */
    val Fab = RoundedCornerShape(16.dp)

    /**
     * Extended FAB shape - M3 Expressive spec.
     * 16dp corners matching standard FAB.
     */
    val ExtendedFab = RoundedCornerShape(16.dp)

    /**
     * FAB shape when pressed - slightly less rounded for shape morphing.
     */
    val FabPressed = RoundedCornerShape(12.dp)

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
    val CodeBlock = RoundedCornerShape(20.dp)

    /**
     * Shape for inline code spans.
     */
    val InlineCode = RoundedCornerShape(6.dp)

    /**
     * Shape for action buttons on message bubbles.
     */
    val MessageActionButton = RoundedCornerShape(24.dp)
    
    /**
     * Button shape - M3 Expressive round buttons use pill-like corners.
     */
    val Button = RoundedCornerShape(24.dp)
    
    /**
     * Button shape when pressed - round buttons become less rounded.
     */
    val ButtonPressed = RoundedCornerShape(12.dp)

    /**
     * Shape for the message input field container - matches FAB for consistency.
     */
    val MessageInputContainer = RoundedCornerShape(28.dp)

    /**
     * Shape for the send button - circle.
     */
    val SendButton = CircleShape

    /**
     * Shape for provider cards in settings.
     */
    val ProviderCard = RoundedCornerShape(28.dp)

    /**
     * Shape for conversation list items - rounded like Google Messages.
     */
    val ConversationItem = RoundedCornerShape(24.dp)

    /**
     * Shape for dropdown menus.
     */
    val Dropdown = RoundedCornerShape(24.dp)

    /**
     * Shape for model picker dropdown.
     */
    val ModelPicker = RoundedCornerShape(24.dp)

    /**
     * Shape for snackbars.
     */
    val Snackbar = RoundedCornerShape(24.dp)

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
    val SearchBar = RoundedCornerShape(32.dp)
}
