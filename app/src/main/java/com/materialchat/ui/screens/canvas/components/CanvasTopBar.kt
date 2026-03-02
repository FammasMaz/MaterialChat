package com.materialchat.ui.screens.canvas.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.materialchat.domain.model.ArtifactType
import com.materialchat.ui.screens.canvas.CanvasViewMode

/**
 * Top app bar for the Smart Canvas screen.
 *
 * Displays:
 * - Back navigation button
 * - Title indicating the artifact type (e.g., "HTML Preview", "Mermaid Diagram")
 * - Toggle button to switch between Preview and Code view modes
 * - Share button to share the raw artifact code
 * - Copy button to copy the raw artifact code to the clipboard
 *
 * Styled with M3 Expressive `surfaceContainer` background consistent with the app's
 * other top bars (see [ChatTopBar]).
 *
 * @param artifactType The type of artifact being displayed
 * @param viewMode The current view mode (preview or code)
 * @param onNavigateBack Callback for back navigation
 * @param onToggleViewMode Callback to toggle between preview and code modes
 * @param onShare Callback to share the artifact code
 * @param onCopy Callback to copy the artifact code to clipboard
 * @param modifier Modifier for this composable
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasTopBar(
    artifactType: ArtifactType,
    viewMode: CanvasViewMode,
    onNavigateBack: () -> Unit,
    onToggleViewMode: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = when (artifactType) {
        ArtifactType.HTML -> "HTML Preview"
        ArtifactType.MERMAID -> "Mermaid Diagram"
        ArtifactType.SVG -> "SVG Preview"
        ArtifactType.LATEX -> "LaTeX Math"
    }

    val toggleIcon = when (viewMode) {
        CanvasViewMode.PREVIEW -> Icons.Outlined.Code
        CanvasViewMode.CODE -> Icons.Outlined.Visibility
    }

    val toggleDescription = when (viewMode) {
        CanvasViewMode.PREVIEW -> "Show code"
        CanvasViewMode.CODE -> "Show preview"
    }

    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        actions = {
            IconButton(onClick = onToggleViewMode) {
                Icon(
                    imageVector = toggleIcon,
                    contentDescription = toggleDescription
                )
            }
            IconButton(onClick = onCopy) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copy code"
                )
            }
            IconButton(onClick = onShare) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = "Share"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = modifier
    )
}
