package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Action buttons for chat messages (copy, regenerate).
 *
 * This component provides quick actions for messages following Material 3 Expressive design:
 * - Copy button: Copies message content to clipboard (available for all messages)
 * - Regenerate button: Regenerates the AI response (only for last assistant message)
 *
 * Features:
 * - Animated visibility with scale and fade transitions
 * - Press feedback with spring-physics scale animation
 * - Consistent Material 3 styling
 *
 * @param showCopy Whether to show the copy button
 * @param showRegenerate Whether to show the regenerate button
 * @param onCopy Callback when copy button is clicked
 * @param onRegenerate Callback when regenerate button is clicked
 * @param modifier Modifier for the action row
 */
@Composable
fun MessageActions(
    showCopy: Boolean = true,
    showRegenerate: Boolean = false,
    onCopy: () -> Unit,
    onRegenerate: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Copy button
        AnimatedVisibility(
            visible = showCopy,
            enter = fadeIn(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + scaleIn(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ),
            exit = fadeOut() + scaleOut()
        ) {
            ActionButton(
                icon = Icons.Default.ContentCopy,
                contentDescription = "Copy message",
                onClick = onCopy
            )
        }

        // Regenerate button (only for last assistant message)
        AnimatedVisibility(
            visible = showRegenerate && onRegenerate != null,
            enter = fadeIn(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + scaleIn(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ),
            exit = fadeOut() + scaleOut()
        ) {
            ActionButton(
                icon = Icons.Default.Refresh,
                contentDescription = "Regenerate response",
                onClick = { onRegenerate?.invoke() }
            )
        }
    }
}

/**
 * Individual action button with press animation.
 *
 * Features spring-physics scale animation on press for Material 3 Expressive feel.
 *
 * @param icon The icon to display
 * @param contentDescription Accessibility description for the button
 * @param onClick Callback when button is clicked
 * @param modifier Modifier for the button
 */
@Composable
private fun ActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "action_button_scale"
    )

    IconButton(
        onClick = {
            isPressed = true
            onClick()
            isPressed = false
        },
        modifier = modifier
            .size(48.dp) // M3 Expressive: 48dp minimum touch target
            .scale(scale),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp) // Slightly larger icon for 48dp container
        )
    }
}

/**
 * Compact version of message actions shown inline.
 *
 * @param isUser Whether this is a user message (affects alignment)
 * @param showRegenerate Whether to show the regenerate button
 * @param onCopy Callback when copy button is clicked
 * @param onRegenerate Optional callback when regenerate button is clicked
 */
@Composable
fun CompactMessageActions(
    isUser: Boolean,
    showRegenerate: Boolean,
    onCopy: () -> Unit,
    onRegenerate: (() -> Unit)?
) {
    MessageActions(
        showCopy = true,
        showRegenerate = showRegenerate && onRegenerate != null,
        onCopy = onCopy,
        onRegenerate = onRegenerate
    )
}
