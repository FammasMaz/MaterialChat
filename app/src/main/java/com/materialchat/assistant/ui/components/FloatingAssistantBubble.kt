package com.materialchat.assistant.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.materialchat.ui.theme.ExpressiveMotion

/**
 * M3 Expressive floating assistant bubble.
 *
 * A circular button that morphs into a rounded rectangle on press,
 * following M3 Expressive shape morphing guidelines.
 *
 * @param onClick Callback when bubble is clicked
 * @param modifier Modifier for the bubble
 * @param size Size of the bubble
 * @param isExpanded Whether the bubble is in expanded state
 */
@Composable
fun FloatingAssistantBubble(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    isExpanded: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // M3 Expressive shape morphing: circle → rounded square on press
    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed || isExpanded) 20.dp else size / 2,
        animationSpec = ExpressiveMotion.Spatial.shapeMorph(),
        label = "cornerRadius"
    )

    // Scale on press
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = ExpressiveMotion.Spatial.scale(),
        label = "scale"
    )

    // Size expansion
    val currentSize by animateDpAsState(
        targetValue = if (isExpanded) size * 1.5f else size,
        animationSpec = ExpressiveMotion.Spatial.default(),
        label = "bubbleSize"
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .size(currentSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        shadowElevation = 8.dp,
        interactionSource = interactionSource
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Assistant",
                tint = contentColor,
                modifier = Modifier.size(currentSize * 0.4f)
            )
        }
    }
}

/**
 * Pulsing ring effect for the floating bubble when listening.
 */
@Composable
fun PulsingRingEffect(
    isActive: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    AnimatedVisibility(
        visible = isActive,
        enter = fadeIn(animationSpec = ExpressiveMotion.Effects.alpha()) +
                scaleIn(animationSpec = ExpressiveMotion.Spatial.default()),
        exit = fadeOut(animationSpec = ExpressiveMotion.Effects.alpha()) +
                scaleOut(animationSpec = ExpressiveMotion.Spatial.default())
    ) {
        Box(
            modifier = modifier
                .size(size * 1.5f)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.2f))
        )
    }
}
