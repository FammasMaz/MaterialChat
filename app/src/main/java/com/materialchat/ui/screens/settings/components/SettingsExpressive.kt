package com.materialchat.ui.screens.settings.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.toShape
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle
import androidx.graphics.shapes.toPath
import com.materialchat.ui.components.ExpressiveSwitch
import androidx.compose.material3.Surface
import com.materialchat.ui.theme.ExpressiveShapes
import com.materialchat.ui.theme.MaterialChatExpressiveTitleFontFamily

/**
 * Modal bottom sheet contour for the settings screen: the app's 32dp squircle on
 * the two top corners only, square at the bottom where the sheet meets the
 * screen edge. Built once; [createOutline] allocates only the polygon path.
 */
val SettingsSheetShape: Shape = object : Shape {
    private val topRounding = CornerRounding(radius = 0f, smoothing = 0.65f)

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        if (size.width <= 0f || size.height <= 0f) return Outline.Rectangle(size.toRect())
        val radiusPx = with(density) { 32.dp.toPx() }
            .coerceIn(0f, minOf(size.width, size.height) / 2f)
        val rounded = CornerRounding(radius = radiusPx, smoothing = topRounding.smoothing)
        // RoundedPolygon.rectangle vertex order: bottom-right, bottom-left, top-left, top-right.
        val polygon = RoundedPolygon.rectangle(
            width = size.width,
            height = size.height,
            perVertexRounding = listOf(CornerRounding.Unrounded, CornerRounding.Unrounded, rounded, rounded),
            centerX = size.width / 2f,
            centerY = size.height / 2f
        )
        return Outline.Generic(polygon.toPath().asComposePath())
    }
}

/**
 * M3 Expressive section header for the settings list: a tonal icon tile, an
 * emphasized title in the expressive title face, and an optional supporting line.
 * Every section uses the same single accent container so the list reads as one
 * designed surface rather than a rainbow.
 *
 * @param title Section title.
 * @param icon Icon drawn inside the tonal tile.
 * @param modifier Optional modifier.
 * @param supporting One-line description shown under the title, if any.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsSectionHeader(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    supporting: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cookie tile: section headers are the one place on Settings that carries an
        // expressive silhouette, so they stay scannable as landmarks.
        Surface(
            modifier = Modifier.size(44.dp),
            shape = MaterialShapes.Cookie9Sided.toShape(),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = MaterialChatExpressiveTitleFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Vertical container for segmented settings rows. Rows inside it are spaced by the
 * M3 Expressive segmented gap so first/middle/last shapes from
 * [ListItemDefaults.segmentedShapes] read as one grouped object.
 *
 * @param modifier Optional modifier.
 * @param content Segmented rows, each given its index and the group count.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        content = content
    )
}

/**
 * Segmented preference row with a trailing [ExpressiveSwitch]. The whole row is
 * toggleable, and its shape comes from its position inside a [SettingsGroup].
 *
 * @param index Position of this row within its group.
 * @param count Number of rows in the group.
 * @param title Headline text.
 * @param checked Current toggle state.
 * @param onCheckedChange Invoked with the new state when the row or switch is toggled.
 * @param modifier Modifier for the row container.
 * @param supporting Supporting text under the headline, if any.
 * @param icon Leading icon, if any.
 * @param switchModifier Modifier applied to the trailing switch (for test tags).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsToggleRow(
    index: Int,
    count: Int,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    switchModifier: Modifier = Modifier
) {
    ListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.fillMaxWidth(),
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            leadingContentColor = MaterialTheme.colorScheme.primary
        ),
        leadingContent = icon?.let { vector ->
            {
                Icon(
                    imageVector = vector,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        supportingContent = supporting?.let { text ->
            {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            ExpressiveSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = switchModifier
            )
        }
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Segmented navigation row: leading icon tile, headline, supporting line and a
 * trailing arrow. Opens a sub-screen on click.
 *
 * @param index Position of this row within its group.
 * @param count Number of rows in the group.
 * @param title Headline text.
 * @param icon Icon drawn inside the leading tile.
 * @param onClick Navigation callback.
 * @param modifier Modifier for the row container.
 * @param supporting Supporting text under the headline, if any.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsNavRow(
    index: Int,
    count: Int,
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null
) {
    ListItem(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        leadingContent = {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = ExpressiveShapes.Tile,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        },
        supportingContent = supporting?.let { text ->
            {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Single-select mode picker built from a connected [ButtonGroup] of [ToggleButton]s.
 * The selected option animates its shape and the pressed option its width per the
 * M3 Expressive spec. Selecting the already-selected option is a no-op.
 *
 * @param options Choices in display order.
 * @param selected Currently selected option.
 * @param onSelect Invoked with the newly selected option.
 * @param label Display label for an option.
 * @param modifier Optional modifier.
 * @param icon Optional leading icon for an option.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> SettingsChoiceGroup(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    icon: ((T) -> ImageVector)? = null
) {
    ButtonGroup(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        options.forEachIndexed { index, option ->
            key(option) {
                val checked = option == selected
                val interactionSource = remember { MutableInteractionSource() }
                ToggleButton(
                    checked = checked,
                    onCheckedChange = { if (!checked) onSelect(option) },
                    modifier = Modifier
                        .weight(1f)
                        .animateWidth(interactionSource),
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    interactionSource = interactionSource,
                    contentPadding = ToggleButtonDefaults.ContentPadding
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon(option),
                            contentDescription = null,
                            modifier = Modifier.size(ToggleButtonDefaults.IconSize)
                        )
                        Spacer(modifier = Modifier.width(ToggleButtonDefaults.IconSpacing))
                    }
                    Text(
                        text = label(option),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
