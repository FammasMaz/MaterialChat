package com.materialchat.domain.usecase.openclaw

import com.materialchat.domain.model.openclaw.OpenClawChatMessage
import com.materialchat.domain.model.openclaw.OpenClawSession
import com.materialchat.domain.repository.OpenClawRepository
import javax.inject.Inject

/**
 * Use case for managing OpenClaw sessions.
 */
class ManageOpenClawSessionsUseCase @Inject constructor(
    private val repository: OpenClawRepository
) {
    /** List all active sessions on the gateway. */
    suspend fun listSessions(): List<OpenClawSession> {
        return repository.listSessions()
    }

    /** Delete a session by its key. */
    suspend fun deleteSession(sessionKey: String) {
        repository.deleteSession(sessionKey)
    }

    /** Get chat history for a session. */
    suspend fun getChatHistory(sessionKey: String, limit: Int = 100): List<OpenClawChatMessage> {
        return repository.getChatHistory(sessionKey, limit)
    }
}
