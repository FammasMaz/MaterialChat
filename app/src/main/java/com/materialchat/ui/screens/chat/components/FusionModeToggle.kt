package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallMerge
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.materialchat.ui.theme.ExpressiveMotion

/**
 * Toggle button for enabling/disabling fusion mode.
 *
 * Uses M3 Expressive design with shape morphing (squircle → circle on active),
 * spring-animated color transitions, and a badge showing selected model count.
 * Minimum 48dp touch target per M3 accessibility requirements.
 *
 * @param isEnabled Whether fusion mode is currently enabled
 * @param modelCount The number of models selected for fusion
 * @param enabled Whether the button is interactive
 * @param onClick Callback when the button is tapped
 * @param modifier Modifier for the button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FusionModeToggle(
    isEnabled: Boolean,
    modelCount: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // M3 Expressive: Container color animates with effects spring (no overshoot)
    val containerColor by animateColorAsState(
        targetValue = if (isEnabled) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = ExpressiveMotion.SpringSpecs.ColorTransition,
        label = "fusionToggleColor"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isEnabled) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = ExpressiveMotion.SpringSpecs.ColorTransition,
        label = "fusionToggleContentColor"
    )

    // M3 Expressive: Shape morphs from squircle (12dp) to circle (24dp) when active
    val cornerRadius by animateDpAsState(
        targetValue = if (isEnabled) 24.dp else 12.dp,
        animationSpec = ExpressiveMotion.Spatial.shapeMorph(),
        label = "fusionToggleCorner"
    )

    // M3 Expressive: Subtle scale bounce on state change
    val scale by animateFloatAsState(
        targetValue = if (isEnabled) 1.05f else 1f,
        animationSpec = ExpressiveMotion.Spatial.scale(),
        label = "fusionToggleScale"
    )

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        contentColor = contentColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isEnabled && modelCount > 0) {
                BadgedBox(
                    badge = {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        ) {
                            Text(text = modelCount.toString())
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.CallMerge,
                        contentDescription = "Fusion mode enabled with $modelCount models"
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.CallMerge,
                    contentDescription = "Enable fusion mode"
                )
            }
        }
    }
}
