package com.materialchat.domain.usecase

import com.materialchat.domain.repository.ConversationRepository

private const val STREAMING_DB_UPDATE_INTERVAL_MS = 64L

/**
 * Coalesces high-frequency provider token events before writing them to Room.
 *
 * Room invalidates the observed message list on every update, which causes the
 * chat screen to recompose. Persisting at roughly display-frame cadence keeps
 * streaming responsive without turning every token into a full UI/database pass.
 */
internal class StreamingMessageContentUpdater(
    private val conversationRepository: ConversationRepository,
    private val messageId: String,
    private val minIntervalMs: Long = STREAMING_DB_UPDATE_INTERVAL_MS
) {
    private var latestContent = ""
    private var latestThinking: String? = null
    private var hasPending = false
    private var lastPersistedAtMs = 0L

    suspend fun onStreaming(content: String, thinkingContent: String?): Boolean {
        latestContent = content
        latestThinking = thinkingContent
        hasPending = true

        val now = System.currentTimeMillis()
        if (lastPersistedAtMs == 0L || now - lastPersistedAtMs >= minIntervalMs) {
            flush(now)
            return true
        }
        return false
    }

    suspend fun flush(now: Long = System.currentTimeMillis()) {
        if (!hasPending) return
        if (latestThinking != null) {
            conversationRepository.updateMessageContentWithThinking(
                messageId,
                latestContent,
                latestThinking
            )
        } else {
            conversationRepository.updateMessageContent(messageId, latestContent)
        }
        lastPersistedAtMs = now
        hasPending = false
    }

    suspend fun persistFinal(content: String, thinkingContent: String?) {
        latestContent = content
        latestThinking = thinkingContent
        hasPending = true
        flush()
    }

    suspend fun persistPartial(content: String) {
        latestContent = content
        hasPending = true
        flush()
    }
}
