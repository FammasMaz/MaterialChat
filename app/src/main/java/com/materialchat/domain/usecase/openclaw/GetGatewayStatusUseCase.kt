package com.materialchat.domain.usecase.openclaw

import com.materialchat.domain.model.openclaw.GatewayConnectionState
import com.materialchat.domain.model.openclaw.GatewayEvent
import com.materialchat.domain.model.openclaw.GatewayStatus
import com.materialchat.domain.repository.OpenClawRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Use case for observing gateway status, connection state, and events.
 */
class GetGatewayStatusUseCase @Inject constructor(
    private val repository: OpenClawRepository
) {
    /** Observe the current connection state. */
    val connectionState: StateFlow<GatewayConnectionState>
        get() = repository.connectionState

    /** Observe real-time gateway events. */
    val events: Flow<GatewayEvent>
        get() = repository.gatewayEvents

    /** Observe connection latency. */
    val latencyMs: StateFlow<Long?>
        get() = repository.latencyMs

    /** Fetch the current gateway status (requires active connection). */
    suspend fun getStatus(): GatewayStatus {
        return repository.getGatewayStatus()
    }
}
