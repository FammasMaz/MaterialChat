package com.materialchat.ui.screens.insights

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.materialchat.domain.model.InsightsData
import com.materialchat.domain.model.InsightsTimeRange
import com.materialchat.ui.screens.insights.components.ActivityHeatmap
import com.materialchat.ui.screens.insights.components.InsightsTopBar
import com.materialchat.ui.screens.insights.components.ModelComparisonCard
import com.materialchat.ui.screens.insights.components.ModelUsageChart
import com.materialchat.ui.screens.insights.components.ResponseTimeChart
import com.materialchat.ui.screens.insights.components.StatCard

/**
 * Conversation Intelligence Dashboard screen.
 *
 * Displays aggregated statistics, model usage charts, response time analysis,
 * activity heatmap, and model comparison data. Supports time range filtering
 * via filter chips.
 *
 * Follows Material 3 Expressive design with spring-based animations,
 * rounded shapes, and dynamic color tokens.
 *
 * @param onNavigateBack Callback for back navigation
 * @param modifier Modifier for the screen
 * @param viewModel The ViewModel managing insights state
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InsightsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedTimeRange by viewModel.selectedTimeRange.collectAsState()

    Scaffold(
        topBar = {
            InsightsTopBar(onNavigateBack = onNavigateBack)
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Time range filter chips
            TimeRangeFilterRow(
                selectedRange = selectedTimeRange,
                onRangeSelected = viewModel::selectTimeRange,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            when (val state = uiState) {
                is InsightsUiState.Loading -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = "Loading insights...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is InsightsUiState.Error -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                is InsightsUiState.Success -> {
                    InsightsContent(
                        data = state.data,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/**
 * Horizontally scrollable row of time range filter chips.
 */
@Composable
private fun TimeRangeFilterRow(
    selectedRange: InsightsTimeRange,
    onRangeSelected: (InsightsTimeRange) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.horizontalScroll(rememberScrollState())
    ) {
        InsightsTimeRange.entries.forEach { range ->
            FilterChip(
                selected = selectedRange == range,
                onClick = { onRangeSelected(range) },
                label = { Text(range.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

/**
 * Main scrollable content area showing all insights cards and charts.
 */
@Composable
private fun InsightsContent(
    data: InsightsData,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // Stat cards row
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            StatCard(
                label = "Conversations",
                value = data.totalConversations,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Messages",
                value = data.totalMessages,
                subtitle = "${data.assistantMessages} from AI",
                modifier = Modifier.weight(1f)
            )
        }

        // Duration stat cards row
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            StatCard(
                label = "Avg Thinking",
                value = data.avgThinkingDuration?.let { (it / 1000).toInt() } ?: 0,
                subtitle = "seconds",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Avg Response",
                value = data.avgTotalDuration?.let { (it / 1000).toInt() } ?: 0,
                subtitle = "seconds",
                modifier = Modifier.weight(1f)
            )
        }

        // Activity heatmap
        ActivityHeatmap(
            dailyActivity = data.dailyActivity,
            modifier = Modifier.fillMaxWidth()
        )

        // Model usage donut chart
        ModelUsageChart(
            modelUsage = data.modelUsage,
            modifier = Modifier.fillMaxWidth()
        )

        // Response time chart
        ResponseTimeChart(
            modelDurations = data.modelDurations,
            modifier = Modifier.fillMaxWidth()
        )

        // Model comparison card
        ModelComparisonCard(
            modelDurations = data.modelDurations,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}
