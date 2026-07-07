package com.materialchat.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.materialchat.ui.theme.ExpressiveMotion
import com.materialchat.ui.theme.MaterialChatExpressiveTitleFontFamily

@Composable
fun ExpressiveScreenBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        AmbientGradientField()
        content()
    }
}

@Composable
private fun AmbientGradientField(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val secondary = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
    val tertiary = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f)
    // Brushes depend only on theme colors (which change rarely), so remember
    // them instead of rebuilding 3 RadialGradient shaders + 3 color lists on
    // every draw pass. The gradient center/radius resolve from draw bounds.
    val primaryBrush = remember(primary) {
        Brush.radialGradient(listOf(primary, Color.Transparent))
    }
    val secondaryBrush = remember(secondary) {
        Brush.radialGradient(listOf(secondary, Color.Transparent))
    }
    val tertiaryBrush = remember(tertiary) {
        Brush.radialGradient(listOf(tertiary, Color.Transparent))
    }
    Canvas(modifier = modifier.fillMaxSize()) {
        drawOval(
            brush = primaryBrush,
            topLeft = Offset(size.width * 0.52f, -size.height * 0.18f),
            size = Size(size.width * 0.72f, size.height * 0.38f)
        )
        drawOval(
            brush = secondaryBrush,
            topLeft = Offset(-size.width * 0.24f, size.height * 0.12f),
            size = Size(size.width * 0.66f, size.height * 0.34f)
        )
        drawOval(
            brush = tertiaryBrush,
            topLeft = Offset(size.width * 0.24f, size.height * 0.74f),
            size = Size(size.width * 0.84f, size.height * 0.34f)
        )
    }
}

@Composable
fun ExpressiveContentSurface(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .padding(padding),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        content = content
    )
}

@Composable
fun ExpressiveTopBarTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    collapsedFraction: Float = 0f,
    maxLines: Int = 1
) {
    val fraction = collapsedFraction.coerceIn(0f, 1f)
    val scale by animateFloatAsState(
        targetValue = 1f - 0.12f * fraction,
        animationSpec = ExpressiveMotion.Spatial.container(),
        label = "expressiveTopBarTitleScale"
    )
    Column(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
        },
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = MaterialChatExpressiveTitleFontFamily,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ExpressiveFilledIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.9f else 1f,
        animationSpec = ExpressiveMotion.Spatial.scale(),
        label = "expressiveIconButtonScale"
    )
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        interactionSource = interactionSource,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.42f),
            disabledContentColor = contentColor.copy(alpha = 0.38f)
        )
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

@Composable
fun ExpressiveSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    subtitle: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ExpressiveCardSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = ExpressiveMotion.Spatial.listItemPress(),
        label = "expressiveCardScale"
    )
    val color by animateColorAsState(
        targetValue = if (pressed) MaterialTheme.colorScheme.surfaceContainerHigh else containerColor,
        animationSpec = ExpressiveMotion.Effects.color(),
        label = "expressiveCardColor"
    )
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = color),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        interactionSource = interactionSource
    ) {
        content()
    }
}

@Composable
fun ExpressiveEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) { Icon(icon, null, Modifier.padding(24.dp).size(44.dp)) }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            ExpressiveButton(onClick = onAction, text = actionText, style = ExpressiveButtonStyle.FilledTonal)
        }
    }
}

@Composable
fun ExpressiveListItemContainer(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp),
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        tonalElevation = 1.dp,
        content = content
    )
}
