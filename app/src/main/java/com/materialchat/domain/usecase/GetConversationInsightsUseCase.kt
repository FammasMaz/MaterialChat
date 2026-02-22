package com.materialchat.domain.usecase

import com.materialchat.data.local.database.dao.ConversationDao
import com.materialchat.data.local.database.dao.MessageDao
import com.materialchat.domain.model.DailyActivityItem
import com.materialchat.domain.model.InsightsData
import com.materialchat.domain.model.InsightsTimeRange
import com.materialchat.domain.model.ModelDurationItem
import com.materialchat.domain.model.ModelUsageItem
import javax.inject.Inject

/**
 * Use case for retrieving conversation intelligence insights.
 *
 * Aggregates data from MessageDao and ConversationDao to build
 * a comprehensive insights dashboard including usage statistics,
 * model performance, and activity trends.
 */
class GetConversationInsightsUseCase @Inject constructor(
    private val messageDao: MessageDao,
    private val conversationDao: ConversationDao
) {

    /**
     * Retrieves insights data for the specified time range.
     *
     * @param timeRange The time range to filter data by
     * @return Aggregated insights data
     */
    suspend operator fun invoke(timeRange: InsightsTimeRange): InsightsData {
        val days = timeRange.days
        return if (days != null) {
            val sinceTimestamp = System.currentTimeMillis() - (days * 86400000L)
            getFilteredInsights(sinceTimestamp, timeRange)
        } else {
            getAllTimeInsights(timeRange)
        }
    }

    private suspend fun getAllTimeInsights(timeRange: InsightsTimeRange): InsightsData {
        val totalConversations = conversationDao.getRootConversationCount()
        val totalMessages = messageDao.getTotalMessageCount()
        val assistantMessages = messageDao.getAssistantMessageCount()
        val avgThinkingDuration = messageDao.getAverageThinkingDuration()
        val avgTotalDuration = messageDao.getAverageTotalDuration()
        val modelUsageCounts = messageDao.getModelUsageCounts()
        val modelDurations = messageDao.getAvgDurationByModel()
        // For all time, show last 90 days of daily activity
        val dailySince = System.currentTimeMillis() - (90 * 86400000L)
        val dailyActivity = messageDao.getMessageCountByDay(dailySince)

        return InsightsData(
            totalConversations = totalConversations,
            totalMessages = totalMessages,
            assistantMessages = assistantMessages,
            avgThinkingDuration = avgThinkingDuration,
            avgTotalDuration = avgTotalDuration,
            modelUsage = modelUsageCounts.map { ModelUsageItem(it.model_name ?: "Unknown", it.count) },
            modelDurations = modelDurations.map { ModelDurationItem(it.model_name ?: "Unknown", it.avg_duration) },
            dailyActivity = dailyActivity.map { DailyActivityItem(it.day, it.count) },
            timeRange = timeRange
        )
    }

    private suspend fun getFilteredInsights(
        sinceTimestamp: Long,
        timeRange: InsightsTimeRange
    ): InsightsData {
        val totalConversations = conversationDao.getConversationCountSince(sinceTimestamp)
        val totalMessages = messageDao.getMessageCountSince(sinceTimestamp)
        val assistantMessages = messageDao.getAssistantMessageCountSince(sinceTimestamp)
        val avgThinkingDuration = messageDao.getAverageThinkingDurationSince(sinceTimestamp)
        val avgTotalDuration = messageDao.getAverageTotalDurationSince(sinceTimestamp)
        val modelUsageCounts = messageDao.getModelUsageCountsSince(sinceTimestamp)
        val modelDurations = messageDao.getAvgDurationByModelSince(sinceTimestamp)
        val dailyActivity = messageDao.getMessageCountByDay(sinceTimestamp)

        return InsightsData(
            totalConversations = totalConversations,
            totalMessages = totalMessages,
            assistantMessages = assistantMessages,
            avgThinkingDuration = avgThinkingDuration,
            avgTotalDuration = avgTotalDuration,
            modelUsage = modelUsageCounts.map { ModelUsageItem(it.model_name ?: "Unknown", it.count) },
            modelDurations = modelDurations.map { ModelDurationItem(it.model_name ?: "Unknown", it.avg_duration) },
            dailyActivity = dailyActivity.map { DailyActivityItem(it.day, it.count) },
            timeRange = timeRange
        )
    }
}
