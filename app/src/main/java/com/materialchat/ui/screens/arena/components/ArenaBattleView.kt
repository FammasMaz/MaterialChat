package com.materialchat.ui.screens.arena.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.materialchat.domain.model.StreamingState

/**
 * Split-screen battle view displaying both model responses side by side.
 *
 * Uses primaryContainer for the left panel, tertiaryContainer for the right panel,
 * and errorContainer for the VS badge. Panels animate their content size with
 * M3 Expressive spring physics.
 *
 * @param leftModelName Display name for the left model
 * @param rightModelName Display name for the right model
 * @param leftContent Accumulated text from the left model
 * @param rightContent Accumulated text from the right model
 * @param leftStreamingState Streaming state for the left model
 * @param rightStreamingState Streaming state for the right model
 */
@Composable
fun ArenaBattleView(
    leftModelName: String,
    rightModelName: String,
    leftContent: String,
    rightContent: String,
    leftStreamingState: StreamingState,
    rightStreamingState: StreamingState,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Left panel (Model A) - primaryContainer
            ResponsePanel(
                modelName = leftModelName,
                content = leftContent,
                streamingState = leftStreamingState,
                containerColor = PanelSide.LEFT,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )

            // Right panel (Model B) - tertiaryContainer
            ResponsePanel(
                modelName = rightModelName,
                content = rightContent,
                streamingState = rightStreamingState,
                containerColor = PanelSide.RIGHT,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }

        // VS badge centered between panels
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shadowElevation = 4.dp,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Text(
                text = "VS",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

private enum class PanelSide { LEFT, RIGHT }

@Composable
private fun ResponsePanel(
    modelName: String,
    content: String,
    streamingState: StreamingState,
    containerColor: PanelSide,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when (containerColor) {
        PanelSide.LEFT -> MaterialTheme.colorScheme.primaryContainer
        PanelSide.RIGHT -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val contentColor = when (containerColor) {
        PanelSide.LEFT -> MaterialTheme.colorScheme.onPrimaryContainer
        PanelSide.RIGHT -> MaterialTheme.colorScheme.onTertiaryContainer
    }

    val isLoading = streamingState is StreamingState.Starting
    val isStreaming = streamingState is StreamingState.Streaming
    val isComplete = streamingState is StreamingState.Completed
    val isError = streamingState is StreamingState.Error
    val isIdle = streamingState is StreamingState.Idle

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = backgroundColor,
        contentColor = contentColor,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = 0.7f,
                        stiffness = 300f
                    )
                )
        ) {
            // Model name header
            Text(
                text = modelName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            when {
                isIdle -> {
                    Text(
                        text = "Waiting for prompt...",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.6f)
                    )
                }
                isLoading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = contentColor
                        )
                        Text(
                            text = "Thinking...",
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.8f)
                        )
                    }
                }
                isStreaming || isComplete -> {
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = content,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                isError -> {
                    Text(
                        text = content.ifBlank { "An error occurred" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
