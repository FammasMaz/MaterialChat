package com.materialchat.domain.usecase.openclaw

import com.materialchat.domain.model.openclaw.GatewayConnectionState
import com.materialchat.domain.repository.OpenClawRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Use case for connecting and disconnecting from the OpenClaw Gateway.
 */
class ConnectGatewayUseCase @Inject constructor(
    private val repository: OpenClawRepository
) {
    /** Current connection state. */
    val connectionState: StateFlow<GatewayConnectionState>
        get() = repository.connectionState

    /** Connect to the gateway. */
    suspend fun connect() {
        repository.connect()
    }

    /** Disconnect from the gateway. */
    fun disconnect() {
        repository.disconnect()
    }

    /** Check if currently connected. */
    fun isConnected(): Boolean {
        return repository.connectionState.value is GatewayConnectionState.Connected
    }
}
