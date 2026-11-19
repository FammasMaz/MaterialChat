package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.materialchat.domain.usecase.ExportConversationUseCase
import com.materialchat.ui.components.SheetGutter
import com.materialchat.ui.components.SheetHeader
import com.materialchat.ui.screens.settings.components.SettingsSheetShape
import com.materialchat.ui.theme.ExpressiveMotion
import com.materialchat.ui.theme.ExpressiveShapes
import com.materialchat.ui.components.HapticPattern
import com.materialchat.ui.components.rememberHapticFeedback

/**
 * Bottom sheet for selecting export format.
 *
 * Features:
 * - Material 3 Expressive styling
 * - Two export options: JSON and Markdown
 * - Spring-physics press animations
 * - Loading state while exporting
 *
 * @param isVisible Whether the bottom sheet is visible
 * @param isExporting Whether an export is in progress
 * @param onDismiss Callback when the sheet is dismissed
 * @param onExportFormat Callback when a format is selected
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportBottomSheet(
    isVisible: Boolean,
    isExporting: Boolean,
    onDismiss: () -> Unit,
    onExportFormat: (ExportConversationUseCase.ExportFormat) -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState()
) {
    if (isVisible) {
        ModalBottomSheet(
            onDismissRequest = {
                if (!isExporting) {
                    onDismiss()
                }
            },
            sheetState = sheetState,
            // Same sheet family as settings: top-only squircle, low container tier, default
            // drag handle, headline title with a close affordance.
            shape = SettingsSheetShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            ExportBottomSheetContent(
                isExporting = isExporting,
                onExportFormat = onExportFormat,
                onClose = { if (!isExporting) onDismiss() }
            )
        }
    }
}

/**
 * Content of the export bottom sheet.
 */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun ExportBottomSheetContent(
    isExporting: Boolean,
    onExportFormat: (ExportConversationUseCase.ExportFormat) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SheetGutter)
            .padding(bottom = 32.dp)
            .animateContentSize(
                animationSpec = ExpressiveMotion.Spatial.contentSize()
            )
    ) {
        SheetHeader(title = "Export conversation", onClose = onClose)

        Text(
            text = "Choose a format to export this conversation",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        if (isExporting) {
            // Loading state
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // M3 Expressive working state: the official LoadingIndicator, not a spinner.
                LoadingIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Exporting...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Export options
            ExportFormatOption(
                icon = Icons.Outlined.Code,
                title = "JSON",
                description = "Full structured data format",
                onClick = { onExportFormat(ExportConversationUseCase.ExportFormat.JSON) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            ExportFormatOption(
                icon = Icons.Outlined.Description,
                title = "Markdown",
                description = "Human-readable text format",
                onClick = { onExportFormat(ExportConversationUseCase.ExportFormat.MARKDOWN) }
            )
        }
    }
}

/**
 * Individual export format option item.
 */
@Composable
private fun ExportFormatOption(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    val haptics = rememberHapticFeedback()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = ExpressiveMotion.Press.Scale,
        label = "export_option_scale"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { haptics.perform(HapticPattern.CLICK); onClick() }
            ),
        // Card tier over the sheet's low container: the same squircle card as a settings
        // group, one tier up, no tonal overlay so the fill is the role the text is resolved on.
        shape = ExpressiveShapes.Card,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = ExpressiveShapes.Tile,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(24.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
