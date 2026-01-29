package com.materialchat.assistant.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.materialchat.ui.components.M3ExpressivePulsingDots
import com.materialchat.ui.components.MarkdownText
import com.materialchat.ui.theme.ExpressiveMotion

/**
 * M3 Expressive response card for the assistant overlay.
 *
 * Displays the AI's response with:
 * - Streaming text with markdown formatting
 * - Loading indicator during processing
 * - Always-visible "Continue in App" action when content exists
 * - Max height constraint for long responses
 *
 * @param userQuery The user's original query
 * @param response The AI's response (streaming or complete)
 * @param isLoading Whether the response is still loading
 * @param isStreaming Whether the response is currently streaming
 * @param onOpenInApp Callback to open the conversation in the main app
 * @param modifier Modifier for the card
 */
@Composable
fun AssistantResponseCard(
    userQuery: String,
    response: String,
    isLoading: Boolean,
    isStreaming: Boolean,
    onOpenInApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val alpha by animateFloatAsState(
        targetValue = if (response.isNotEmpty() || isLoading) 1f else 0f,
        animationSpec = ExpressiveMotion.Effects.alpha(),
        label = "cardAlpha"
    )

    AnimatedVisibility(
        visible = response.isNotEmpty() || isLoading,
        enter = fadeIn(animationSpec = ExpressiveMotion.Effects.alpha()) +
                slideInVertically(
                    animationSpec = ExpressiveMotion.Spatial.default(),
                    initialOffsetY = { it / 4 }
                ),
        exit = fadeOut(animationSpec = ExpressiveMotion.Effects.alpha()) +
                slideOutVertically(
                    animationSpec = ExpressiveMotion.Spatial.default(),
                    targetOffsetY = { it / 4 }
                )
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp) // M3 Expressive: Constrain height for long responses
                .alpha(alpha),
            shape = RoundedCornerShape(28.dp), // M3 Expressive: Large rounded corners
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Scrollable content area
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // User query
                    if (userQuery.isNotEmpty()) {
                        UserQuerySection(query = userQuery)
                    }

                    // Response content or loading
                    if (isLoading && response.isEmpty()) {
                        LoadingSection()
                    } else if (response.isNotEmpty()) {
                        ResponseSection(
                            response = response,
                            isStreaming = isStreaming
                        )
                    }
                }

                // ALWAYS visible action bar when there's a query (M3 Expressive prominent button)
                if (userQuery.isNotEmpty()) {
                    ActionBar(
                        onOpenInApp = onOpenInApp,
                        isLoading = isLoading && response.isEmpty()
                    )
                }
            }
        }
    }
}

@Composable
private fun UserQuerySection(query: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "You asked",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = query,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun LoadingSection() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        M3ExpressivePulsingDots(
            color = MaterialTheme.colorScheme.primary,
            dotSize = 8.dp
        )
    }
}

@Composable
private fun ResponseSection(
    response: String,
    isStreaming: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Response with markdown rendering
        MarkdownText(
            markdown = response,
            textColor = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium
        )

        // Streaming indicator
        if (isStreaming) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                M3ExpressivePulsingDots(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    dotSize = 4.dp
                )
            }
        }
    }
}

/**
 * M3 Expressive action bar with prominent "Continue in App" button.
 * Always visible when content exists - not just after streaming completes.
 */
@Composable
private fun ActionBar(
    onOpenInApp: () -> Unit,
    isLoading: Boolean
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(
            topStart = 0.dp,
            topEnd = 0.dp,
            bottomStart = 28.dp,
            bottomEnd = 28.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // M3 Expressive: Prominent FilledTonalButton with pill shape
            FilledTonalButton(
                onClick = onOpenInApp,
                enabled = !isLoading,
                shape = RoundedCornerShape(28.dp), // M3 Expressive pill shape
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.heightIn(min = 48.dp) // M3 48dp minimum touch target
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Continue in App",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

/**
 * Compact response bubble for quick answers.
 */
@Composable
fun QuickResponseBubble(
    response: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.primaryContainer,
    textColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor
    ) {
        Text(
            text = response,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}
