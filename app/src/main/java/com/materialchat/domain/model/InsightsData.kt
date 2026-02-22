package com.materialchat.domain.model

/**
 * Domain model containing all conversation intelligence insights data.
 *
 * Aggregates various statistics about usage patterns, model performance,
 * and activity trends for display in the Insights dashboard.
 */
data class InsightsData(
    val totalConversations: Int,
    val totalMessages: Int,
    val assistantMessages: Int,
    val avgThinkingDuration: Double?,
    val avgTotalDuration: Double?,
    val modelUsage: List<ModelUsageItem>,
    val modelDurations: List<ModelDurationItem>,
    val dailyActivity: List<DailyActivityItem>,
    val timeRange: InsightsTimeRange
)

/**
 * Represents a single model's usage count.
 */
data class ModelUsageItem(
    val modelName: String,
    val count: Int
)

/**
 * Represents a single model's average response duration.
 */
data class ModelDurationItem(
    val modelName: String,
    val avgDurationMs: Double
)

/**
 * Represents the message count for a single day.
 */
data class DailyActivityItem(
    val date: String,
    val count: Int
)

/**
 * Time range options for filtering insights data.
 *
 * @property label Human-readable label for display
 * @property days Number of days to look back, or null for all time
 */
enum class InsightsTimeRange(val label: String, val days: Int?) {
    WEEK("7 Days", 7),
    MONTH("30 Days", 30),
    QUARTER("90 Days", 90),
    ALL_TIME("All Time", null)
}
