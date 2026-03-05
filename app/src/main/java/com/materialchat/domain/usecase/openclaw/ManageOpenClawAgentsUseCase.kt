package com.materialchat.domain.usecase.openclaw

import com.materialchat.domain.repository.OpenClawRepository
import javax.inject.Inject

/**
 * Use case for managing OpenClaw agents from the app.
 */
class ManageOpenClawAgentsUseCase @Inject constructor(
    private val repository: OpenClawRepository
) {
    /**
     * Ensures an agent exists on the gateway.
     *
     * @return true when the agent was created, false when it already existed.
     */
    suspend fun ensureAgentExists(agentId: String, workspaceDir: String): Boolean {
        return repository.ensureAgentExists(agentId, workspaceDir)
    }
}
