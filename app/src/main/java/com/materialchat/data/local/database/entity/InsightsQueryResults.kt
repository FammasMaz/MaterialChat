package com.materialchat.data.local.database.entity

import androidx.room.ColumnInfo

/**
 * POJO classes for aggregate query results used by the Insights dashboard.
 * These are not Room entities — they are result types for DAO aggregate queries.
 */

/**
 * Result of grouping assistant messages by model name.
 */
data class ModelUsageCount(
    @ColumnInfo(name = "model_name") val modelName: String,
    @ColumnInfo(name = "count") val count: Int
)

/**
 * Result of averaging response duration by model.
 */
data class ModelAvgDuration(
    @ColumnInfo(name = "model_name") val modelName: String,
    @ColumnInfo(name = "avg_duration") val avgDuration: Double
)

/**
 * Result of counting messages per day for the activity heatmap.
 */
data class DailyMessageCount(
    @ColumnInfo(name = "day") val day: String,
    @ColumnInfo(name = "count") val count: Int
)
