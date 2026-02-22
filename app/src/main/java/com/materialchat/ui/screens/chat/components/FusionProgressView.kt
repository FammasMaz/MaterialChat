package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.materialchat.domain.model.FusionIndividualResponse
import com.materialchat.domain.model.FusionResult
import com.materialchat.ui.theme.ExpressiveMotion
import kotlinx.coroutines.delay

/**
 * Real-time fusion progress view showing parallel model streams.
 *
 * Displays per-model response cards with live streaming text, a convergence
 * animation during synthesis, and the synthesized response as it streams.
 * Follows M3 Expressive design with spring-physics animations.
 *
 * @param fusionResult The current fusion result with per-model responses
 * @param isFusionRunning Whether the fusion operation is active
 * @param modifier Modifier for the view
 */
@Composable
fun FusionProgressView(
    fusionResult: FusionResult?,
    isFusionRunning: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isFusionRunning,
        enter = expandVertically(
            animationSpec = ExpressiveMotion.Spatial.container()
        ) + fadeIn(
            animationSpec = ExpressiveMotion.Effects.alpha()
        ),
        exit = shrinkVertically(
            animationSpec = ExpressiveMotion.Spatial.container()
        ) + fadeOut(
            animationSpec = ExpressiveMotion.Effects.alpha()
        )
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header with fusion animation and status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FusionAnimation(
                        isActive = isFusionRunning,
                        modifier = Modifier.size(36.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                fusionResult?.isSynthesizing == true -> "Synthesizing best response..."
                                fusionResult?.individualResponses?.any { it.isStreaming } == true -> "Querying models in parallel..."
                                fusionResult?.individualResponses?.isNotEmpty() == true -> "All models responded"
                                else -> "Starting fusion..."
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.SemiBold
                        )

                        val completedCount = fusionResult?.individualResponses?.count { !it.isStreaming } ?: 0
                        val totalCount = fusionResult?.individualResponses?.size ?: 0
                        if (totalCount > 0 && fusionResult?.isSynthesizing != true) {
                            Text(
                                text = "$completedCount / $totalCount models complete",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Per-model response cards
                fusionResult?.individualResponses?.forEachIndexed { index, response ->
                    FusionModelCard(response = response)
                    if (index < (fusionResult.individualResponses.size - 1)) {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                // Synthesis progress
                if (fusionResult?.isSynthesizing == true) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Judge model label
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "Judge: synthesizing",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    if (!fusionResult.synthesizedResponse.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = fusionResult.synthesizedResponse,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual model response card within the fusion progress view.
 *
 * Shows model name, streaming status indicator, duration, and content preview.
 */
@Composable
private fun FusionModelCard(
    response: FusionIndividualResponse,
    modifier: Modifier = Modifier
) {
    // Pulsing alpha for streaming indicator
    val pulseAlpha = if (response.isStreaming) {
        var alpha by remember { mutableFloatStateOf(1f) }
        LaunchedEffect(Unit) {
            while (true) {
                alpha = 0.4f
                delay(500)
                alpha = 1f
                delay(500)
            }
        }
        alpha
    } else {
        1f
    }

    val animatedAlpha by animateFloatAsState(
        targetValue = pulseAlpha,
        animationSpec = ExpressiveMotion.Effects.alpha(),
        label = "modelCardPulse"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (response.isStreaming) {
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
        }
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Status dot
            Box(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .size(8.dp)
                    .alpha(if (response.isStreaming) animatedAlpha else 1f)
                    .background(
                        color = if (response.isStreaming) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    )
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Model name badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (response.isStreaming) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        }
                    ) {
                        Text(
                            text = response.modelName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (response.isStreaming) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    // Duration or streaming indicator
                    if (response.durationMs != null) {
                        Text(
                            text = formatDuration(response.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    } else if (response.isStreaming && response.content.isNotEmpty()) {
                        Text(
                            text = "streaming...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f),
                            fontStyle = FontStyle.Italic
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Content preview or status
                if (response.content.isNotEmpty()) {
                    Text(
                        text = response.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (response.isStreaming) {
                    Text(
                        text = "Generating...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic
                    )
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    return when {
        totalSeconds < 1 -> "<1s"
        totalSeconds < 60 -> "${totalSeconds}s"
        else -> {
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            if (seconds == 0L) "${minutes}m" else "${minutes}m ${seconds}s"
        }
    }
}
