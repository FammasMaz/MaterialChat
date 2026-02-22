package com.materialchat.ui.screens.insights.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.materialchat.domain.model.DailyActivityItem
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * GitHub-style activity heatmap showing daily message activity.
 *
 * Displays a grid of small rounded boxes arranged in 7 rows (days of week)
 * by ~13 columns (weeks). Color intensity is derived from primaryContainer
 * with alpha proportional to activity level.
 *
 * @param dailyActivity List of daily activity items with date and count
 * @param modifier Modifier for the card
 */
@Composable
fun ActivityHeatmap(
    dailyActivity: List<DailyActivityItem>,
    modifier: Modifier = Modifier
) {
    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(dailyActivity) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.9f,
                stiffness = 40f
            )
        )
    }

    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val surfaceVariant = MaterialTheme.colorScheme.surfaceContainerLowest

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
                text = "Activity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Build lookup map from date string -> count
            val activityMap = dailyActivity.associate { it.date to it.count }
            val maxCount = dailyActivity.maxOfOrNull { it.count } ?: 1

            // Generate 13 weeks (91 days) ending today
            val today = LocalDate.now()
            val numWeeks = 13
            val totalDays = numWeeks * 7
            val startDate = today.minusDays(totalDays.toLong() - 1)
            // Adjust startDate to the previous Monday
            val adjustedStart = startDate.with(DayOfWeek.MONDAY)
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

            val dayLabels = listOf("M", "", "W", "", "F", "", "S")

            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Day-of-week labels column
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.width(16.dp)
                ) {
                    dayLabels.forEach { label ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(12.dp)
                        ) {
                            if (label.isNotEmpty()) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.7f
                                )
                            }
                        }
                    }
                }

                // Heatmap grid - each column is a week
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    for (week in 0 until numWeeks) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            for (dayOfWeek in 0 until 7) {
                                val date = adjustedStart.plusDays((week * 7 + dayOfWeek).toLong())
                                val dateStr = date.format(formatter)
                                val count = activityMap[dateStr] ?: 0
                                val intensity = if (maxCount > 0 && count > 0) {
                                    (count.toFloat() / maxCount).coerceIn(0.15f, 1f) * animationProgress.value
                                } else {
                                    0f
                                }

                                val isFuture = date.isAfter(today)
                                val cellColor = when {
                                    isFuture -> surfaceVariant.copy(alpha = 0.3f)
                                    intensity > 0f -> primaryContainer.copy(alpha = intensity)
                                    else -> surfaceVariant
                                }

                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(cellColor)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
