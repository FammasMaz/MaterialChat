package com.materialchat.ui.screens.insights.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.materialchat.domain.model.ModelDurationItem

/**
 * Horizontal bar chart showing average response times by model.
 *
 * Uses Compose Canvas to draw rounded rectangles for each model's bar.
 * Bars are animated with spring physics for M3 Expressive motion.
 * Model labels appear to the left and duration labels to the right.
 *
 * @param modelDurations List of model duration items to visualize
 * @param modifier Modifier for the card
 */
@Composable
fun ResponseTimeChart(
    modelDurations: List<ModelDurationItem>,
    modifier: Modifier = Modifier
) {
    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(modelDurations) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.7f,
                stiffness = 60f
            )
        )
    }

    val barColor = MaterialTheme.colorScheme.primary
    val barBackground = MaterialTheme.colorScheme.surfaceContainerHighest

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Response Times",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (modelDurations.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                ) {
                    Text(
                        text = "No data yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val maxDuration = modelDurations.maxOf { it.avgDurationMs }

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    modelDurations.forEach { item ->
                        val fraction = if (maxDuration > 0) (item.avgDurationMs / maxDuration).toFloat() else 0f

                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = item.modelName.substringAfterLast('/'),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = formatDuration(item.avgDurationMs),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(12.dp)
                            ) {
                                val barHeight = size.height
                                val cornerRadiusPx = barHeight / 2f
                                // Background bar
                                drawRoundRect(
                                    color = barBackground,
                                    topLeft = Offset.Zero,
                                    size = Size(size.width, barHeight),
                                    cornerRadius = CornerRadius(cornerRadiusPx)
                                )
                                // Animated foreground bar
                                val barWidth = size.width * fraction * animationProgress.value
                                if (barWidth > 0f) {
                                    drawRoundRect(
                                        color = barColor,
                                        topLeft = Offset.Zero,
                                        size = Size(barWidth, barHeight),
                                        cornerRadius = CornerRadius(cornerRadiusPx)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Formats a duration in milliseconds to a human-readable string.
 */
private fun formatDuration(ms: Double): String {
    return when {
        ms < 1000 -> "${ms.toInt()}ms"
        ms < 60000 -> String.format("%.1fs", ms / 1000)
        else -> String.format("%.1fm", ms / 60000)
    }
}
