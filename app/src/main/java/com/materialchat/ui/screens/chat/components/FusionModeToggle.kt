package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CallMerge
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.materialchat.ui.theme.ExpressiveMotion

/**
 * Toggle button for enabling/disabling fusion mode.
 *
 * Displays a merge icon with an animated badge showing the number of selected
 * models when fusion is enabled. Uses M3 Expressive spring animations for
 * color and scale transitions.
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
            .scale(scale),
        shape = CircleShape,
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
                        imageVector = Icons.Outlined.CallMerge,
                        contentDescription = "Fusion mode enabled with $modelCount models"
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Outlined.CallMerge,
                    contentDescription = "Enable fusion mode"
                )
            }
        }
    }
}
