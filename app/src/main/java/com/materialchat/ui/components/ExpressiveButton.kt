package com.materialchat.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.materialchat.ui.theme.ExpressiveMotion
import com.materialchat.ui.theme.MaterialChatMotion

/**
 * Material 3 Expressive Button with shape morph and spring-physics animations.
 *
 * This button morphs its corner radius and scale when pressed, providing
 * tactile feedback that follows Material 3 Expressive design principles.
 *
 * @param onClick The callback invoked when the button is clicked.
 * @param modifier The modifier to apply to this button.
 * @param enabled Whether the button is enabled.
 * @param text The button label text.
 * @param leadingIcon Optional leading icon.
 * @param trailingIcon Optional trailing icon.
 * @param style The visual style of the button.
 */
@Composable
fun ExpressiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    style: ExpressiveButtonStyle = ExpressiveButtonStyle.Filled
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Animate scale on press with M3 Expressive SPATIAL spring (bouncy)
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = ExpressiveMotion.Spatial.scale(),
        label = "buttonScale"
    )

    // Animate corner radius on press with SPATIAL spring (shape morph effect)
    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed) 12.dp else 20.dp,
        animationSpec = ExpressiveMotion.Spatial.shapeMorph(),
        label = "cornerRadius"
    )

    val shape = RoundedCornerShape(cornerRadius)

    when (style) {
        ExpressiveButtonStyle.Filled -> {
            Button(
                onClick = onClick,
                modifier = modifier.scale(scale),
                enabled = enabled,
                shape = shape,
                interactionSource = interactionSource,
                contentPadding = ButtonDefaults.ContentPadding
            ) {
                ButtonContent(
                    text = text,
                    leadingIcon = leadingIcon,
                    trailingIcon = trailingIcon
                )
            }
        }

        ExpressiveButtonStyle.FilledTonal -> {
            FilledTonalButton(
                onClick = onClick,
                modifier = modifier.scale(scale),
                enabled = enabled,
                shape = shape,
                interactionSource = interactionSource,
                contentPadding = ButtonDefaults.ContentPadding
            ) {
                ButtonContent(
                    text = text,
                    leadingIcon = leadingIcon,
                    trailingIcon = trailingIcon
                )
            }
        }

        ExpressiveButtonStyle.Outlined -> {
            OutlinedButton(
                onClick = onClick,
                modifier = modifier.scale(scale),
                enabled = enabled,
                shape = shape,
                interactionSource = interactionSource,
                contentPadding = ButtonDefaults.ContentPadding
            ) {
                ButtonContent(
                    text = text,
                    leadingIcon = leadingIcon,
                    trailingIcon = trailingIcon
                )
            }
        }

        ExpressiveButtonStyle.Text -> {
            TextButton(
                onClick = onClick,
                modifier = modifier.scale(scale),
                enabled = enabled,
                shape = shape,
                interactionSource = interactionSource,
                contentPadding = ButtonDefaults.TextButtonContentPadding
            ) {
                ButtonContent(
                    text = text,
                    leadingIcon = leadingIcon,
                    trailingIcon = trailingIcon
                )
            }
        }
    }
}

/**
 * Internal button content composable.
 */
@Composable
private fun ButtonContent(
    text: String,
    leadingIcon: ImageVector?,
    trailingIcon: ImageVector?
) {
    if (leadingIcon != null) {
        Icon(
            imageVector = leadingIcon,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
    }

    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium
    )

    if (trailingIcon != null) {
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = trailingIcon,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Button style variants for ExpressiveButton.
 */
enum class ExpressiveButtonStyle {
    Filled,
    FilledTonal,
    Outlined,
    Text
}

/**
 * An expressive icon button with shape morph animation.
 *
 * @param onClick The callback invoked when the button is clicked.
 * @param icon The icon to display.
 * @param contentDescription Accessibility description for the icon.
 * @param modifier The modifier to apply.
 * @param enabled Whether the button is enabled.
 * @param containerColor The background color of the button.
 * @param contentColor The color of the icon.
 * @param size The size of the button.
 */
@Composable
fun ExpressiveIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    size: Dp = 48.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Animate scale on press with M3 Expressive SPATIAL spring
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = ExpressiveMotion.Spatial.scale(),
        label = "iconButtonScale"
    )

    // Animate corner radius on press with SPATIAL spring (more rounded when pressed)
    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed) size / 2 else size / 4,
        animationSpec = ExpressiveMotion.Spatial.shapeMorph(),
        label = "iconButtonCornerRadius"
    )

    // Animate container color with M3 Expressive EFFECTS spring (no bounce!)
    val animatedContainerColor by animateColorAsState(
        targetValue = if (isPressed) {
            containerColor.copy(alpha = 0.85f)
        } else {
            containerColor
        },
        animationSpec = ExpressiveMotion.Effects.color(),
        label = "iconButtonContainerColor"
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        enabled = enabled,
        shape = RoundedCornerShape(cornerRadius),
        color = animatedContainerColor,
        contentColor = contentColor,
        interactionSource = interactionSource
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.semantics { role = Role.Button }
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * A small expressive action button typically used in message bubbles.
 *
 * @param onClick The callback invoked when the button is clicked.
 * @param icon The icon to display.
 * @param contentDescription Accessibility description for the icon.
 * @param modifier The modifier to apply.
 * @param enabled Whether the button is enabled.
 */
@Composable
fun ExpressiveActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // M3 Expressive SPATIAL spring for scale
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = ExpressiveMotion.Spatial.scale(),
        label = "actionButtonScale"
    )

    // M3 Expressive SPATIAL spring for shape morph
    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed) 12.dp else 8.dp,
        animationSpec = ExpressiveMotion.Spatial.shapeMorph(),
        label = "actionButtonCornerRadius"
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .size(32.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        enabled = enabled,
        shape = RoundedCornerShape(cornerRadius),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        interactionSource = interactionSource
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.semantics { role = Role.Button }
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * An expressive floating action button with spring physics.
 *
 * @param onClick The callback invoked when the button is clicked.
 * @param icon The icon to display.
 * @param contentDescription Accessibility description for the icon.
 * @param modifier The modifier to apply.
 * @param expanded Whether to show the extended version with text.
 * @param text The text to display when expanded.
 */
@Composable
fun ExpressiveFab(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    text: String = ""
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // M3 Expressive SPATIAL spring for playful scale (FAB is a hero element)
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = ExpressiveMotion.Spatial.playful(),
        label = "fabScale"
    )

    // M3 Expressive SPATIAL spring for shape morph
    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed) 20.dp else 28.dp,
        animationSpec = ExpressiveMotion.Spatial.shapeMorph(),
        label = "fabCornerRadius"
    )

    // M3 Expressive EFFECTS spring for elevation (no bounce)
    val elevation by animateDpAsState(
        targetValue = if (isPressed) 2.dp else 6.dp,
        animationSpec = ExpressiveMotion.Effects.elevation(),
        label = "fabElevation"
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .defaultMinSize(minWidth = 56.dp, minHeight = 56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(cornerRadius),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = elevation,
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (expanded) 20.dp else 16.dp,
                vertical = 16.dp
            ),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = if (!expanded) contentDescription else null,
                modifier = Modifier.size(24.dp)
            )

            if (expanded && text.isNotEmpty()) {
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * An animated extended FAB that expands/collapses based on scroll state.
 * 
 * M3 Expressive behavior:
 * - Scroll DOWN: FAB collapses to icon-only (width shrinks with bounce, label fades)
 * - Scroll UP: FAB expands back with label (width grows with bouncy overshoot)
 * - At top of list: FAB always expanded
 *
 * @param expanded Whether the FAB should be in expanded state (controlled by scroll)
 * @param onClick The callback invoked when the button is clicked
 * @param icon The icon composable to display
 * @param text The text composable to display when expanded
 * @param modifier The modifier to apply
 * @param containerColor The background color of the FAB
 * @param contentColor The color of the icon and text
 */
@Composable
fun AnimatedExtendedFab(
    expanded: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    text: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // M3 Expressive SPATIAL spring for width (bouncy expand/collapse)
    val width by animateDpAsState(
        targetValue = if (expanded) 140.dp else 56.dp,
        animationSpec = ExpressiveMotion.Spatial.fabExpand(),
        label = "fabWidth"
    )

    // M3 Expressive EFFECTS spring for label alpha (smooth fade, no bounce)
    val labelAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = ExpressiveMotion.Effects.alpha(),
        label = "fabLabelAlpha"
    )

    // M3 Expressive SPATIAL spring for press scale (playful)
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = ExpressiveMotion.Spatial.playful(),
        label = "fabScale"
    )

    // M3 Expressive SPATIAL spring for corner radius morph
    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed) 20.dp else 28.dp,
        animationSpec = ExpressiveMotion.Spatial.shapeMorph(),
        label = "fabCornerRadius"
    )

    // M3 Expressive EFFECTS spring for elevation (no bounce)
    val elevation by animateDpAsState(
        targetValue = if (isPressed) 2.dp else 6.dp,
        animationSpec = ExpressiveMotion.Effects.elevation(),
        label = "fabElevation"
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .width(width)
            .height(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = elevation,
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds() // Clip text cleanly as FAB shrinks
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            
            // Only render label content when visible - clip prevents text wrap
            if (labelAlpha > 0.01f) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .graphicsLayer { alpha = labelAlpha }
                        .clipToBounds() // Ensure text doesn't wrap/overflow
                ) {
                    text()
                }
            }
        }
    }
}
