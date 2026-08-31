package com.materialchat.data.local.database.entity

import androidx.room.ColumnInfo

/**
 * POJO for model usage count aggregate query results.
 *
 * Maps to the result of grouping messages by model_name and counting them.
 */
data class ModelUsageCount(
    @ColumnInfo(name = "model_name")
    val model_name: String?,
    @ColumnInfo(name = "count")
    val count: Int
)

/**
 * POJO for model average duration aggregate query results.
 *
 * Maps to the result of grouping messages by model_name and averaging total_duration_ms.
 * The message_count is the number of messages behind the average, used for
 * weighted merging of provider variants under one canonical model identity.
 */
data class ModelAvgDuration(
    @ColumnInfo(name = "model_name")
    val model_name: String?,
    @ColumnInfo(name = "avg_duration")
    val avg_duration: Double,
    @ColumnInfo(name = "message_count")
    val message_count: Int
)

/**
 * POJO for daily message count aggregate query results.
 *
 * Maps to the result of grouping messages by day and counting them.
 * The day field is a date string in YYYY-MM-DD format.
 */
data class DailyMessageCount(
    @ColumnInfo(name = "day")
    val day: String,
    @ColumnInfo(name = "count")
    val count: Int
)
