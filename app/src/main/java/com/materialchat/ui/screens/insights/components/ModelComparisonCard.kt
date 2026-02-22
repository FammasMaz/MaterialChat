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
 * Model comparison card showing fastest vs slowest model.
 *
 * Displays a side-by-side comparison of the fastest and slowest responding
 * models with animated horizontal bars. Uses Canvas drawRoundRect for bars
 * and spring animations for M3 Expressive motion.
 *
 * @param modelDurations List of model duration items to compare
 * @param modifier Modifier for the card
 */
@Composable
fun ModelComparisonCard(
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

    val fastColor = MaterialTheme.colorScheme.primary
    val slowColor = MaterialTheme.colorScheme.tertiary
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
                text = "Model Comparison",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (modelDurations.size < 2) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                ) {
                    Text(
                        text = if (modelDurations.isEmpty()) "No data yet" else "Need at least 2 models to compare",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val sorted = modelDurations.sortedBy { it.avgDurationMs }
                val fastest = sorted.first()
                val slowest = sorted.last()
                val maxDuration = slowest.avgDurationMs

                // Fastest model
                ComparisonBarItem(
                    label = "Fastest",
                    modelName = fastest.modelName.substringAfterLast('/'),
                    durationMs = fastest.avgDurationMs,
                    fraction = if (maxDuration > 0) (fastest.avgDurationMs / maxDuration).toFloat() else 0f,
                    barColor = fastColor,
                    barBackground = barBackground,
                    animationProgress = animationProgress.value
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Slowest model
                ComparisonBarItem(
                    label = "Slowest",
                    modelName = slowest.modelName.substringAfterLast('/'),
                    durationMs = slowest.avgDurationMs,
                    fraction = if (maxDuration > 0) (slowest.avgDurationMs / maxDuration).toFloat() else 0f,
                    barColor = slowColor,
                    barBackground = barBackground,
                    animationProgress = animationProgress.value
                )

                // Speed difference
                if (fastest.avgDurationMs > 0 && slowest.avgDurationMs > fastest.avgDurationMs) {
                    val speedup = slowest.avgDurationMs / fastest.avgDurationMs
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "${fastest.modelName.substringAfterLast('/')} is ${String.format("%.1f", speedup)}x faster",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun ComparisonBarItem(
    label: String,
    modelName: String,
    durationMs: Double,
    fraction: Float,
    barColor: androidx.compose.ui.graphics.Color,
    barBackground: androidx.compose.ui.graphics.Color,
    animationProgress: Float
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = modelName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = formatComparisonDuration(durationMs),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
        ) {
            val barHeight = size.height
            val cornerRadiusPx = barHeight / 2f
            // Background
            drawRoundRect(
                color = barBackground,
                topLeft = Offset.Zero,
                size = Size(size.width, barHeight),
                cornerRadius = CornerRadius(cornerRadiusPx)
            )
            // Foreground
            val barWidth = size.width * fraction * animationProgress
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

private fun formatComparisonDuration(ms: Double): String {
    return when {
        ms < 1000 -> "${ms.toInt()}ms"
        ms < 60000 -> String.format("%.1fs", ms / 1000)
        else -> String.format("%.1fm", ms / 60000)
    }
}
