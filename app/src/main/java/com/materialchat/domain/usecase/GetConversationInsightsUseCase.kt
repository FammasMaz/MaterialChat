package com.materialchat.domain.usecase

import com.materialchat.data.local.database.dao.ConversationDao
import com.materialchat.data.local.database.dao.MessageDao
import com.materialchat.data.local.database.entity.ModelAvgDuration
import com.materialchat.data.local.database.entity.ModelUsageCount
import com.materialchat.domain.model.DailyActivityItem
import com.materialchat.domain.model.InsightsData
import com.materialchat.domain.model.InsightsTimeRange
import com.materialchat.domain.model.ModelDurationItem
import com.materialchat.domain.model.ModelUsageItem
import com.materialchat.domain.util.ModelIdentity
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
            modelUsage = mergeModelUsage(modelUsageCounts),
            modelDurations = mergeModelDurations(modelDurations),
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
            modelUsage = mergeModelUsage(modelUsageCounts),
            modelDurations = mergeModelDurations(modelDurations),
            dailyActivity = dailyActivity.map { DailyActivityItem(it.day, it.count) },
            timeRange = timeRange
        )
    }

    /**
     * Aggregates usage counts under canonical model identities so the same
     * model served by different providers counts as ONE model.
     * Display name: the variant with the highest count (most specific).
     */
    private fun mergeModelUsage(counts: List<ModelUsageCount>): List<ModelUsageItem> {
        data class Acc(val key: String, var count: Int, var bestName: String, var bestCount: Int)
        val merged = linkedMapOf<String, Acc>()
        for (row in counts) {
            val raw = row.model_name ?: continue
            val key = ModelIdentity.canonicalKey(raw) ?: continue
            val acc = merged.getOrPut(key) { Acc(key, 0, raw, row.count) }
            acc.count += row.count
            if (row.count > acc.bestCount) {
                acc.bestName = raw
                acc.bestCount = row.count
            }
        }
        return merged.values
            .sortedByDescending { it.count }
            .map { ModelUsageItem(it.bestName, it.count) }
    }

    /**
     * Aggregates average durations under canonical model identities.
     * Merged average = weighted by each variant's message count.
     */
    private fun mergeModelDurations(durations: List<ModelAvgDuration>): List<ModelDurationItem> {
        data class Acc(var weightedSum: Double = 0.0, var weight: Int = 0)
        val accByKey = hashMapOf<String, Acc>()
        for (row in durations) {
            val key = ModelIdentity.canonicalKey(row.model_name) ?: continue
            val acc = accByKey.getOrPut(key) { Acc() }
            acc.weightedSum += row.avg_duration * row.message_count
            acc.weight += row.message_count
        }
        return accByKey.entries
            .map { (key, acc) ->
                ModelDurationItem(
                    modelName = key,
                    avgDurationMs = if (acc.weight > 0) acc.weightedSum / acc.weight else 0.0
                )
            }
            .sortedBy { it.avgDurationMs }
    }
}
