package com.materialchat.domain.usecase.openclaw

import com.materialchat.domain.model.openclaw.OpenClawConfig
import com.materialchat.domain.repository.OpenClawRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Use case for managing OpenClaw Gateway configuration.
 */
class ManageOpenClawConfigUseCase @Inject constructor(
    private val repository: OpenClawRepository
) {
    /** Observe the current OpenClaw config. */
    fun observeConfig(): Flow<OpenClawConfig> {
        return repository.observeConfig()
    }

    /** Update the OpenClaw config. */
    suspend fun updateConfig(config: OpenClawConfig) {
        repository.updateConfig(config)
    }

    /** Set the gateway authentication token. */
    suspend fun setToken(token: String) {
        repository.setToken(token)
    }

    /** Get the gateway authentication token. */
    suspend fun getToken(): String? {
        return repository.getToken()
    }

    /** Check if a gateway token is stored. */
    suspend fun hasToken(): Boolean {
        return repository.hasToken()
    }

    /** Delete the gateway token. */
    suspend fun deleteToken() {
        repository.deleteToken()
    }

    /** Check if the gateway is fully configured (URL + token). */
    suspend fun isConfigured(): Boolean {
        val config = repository.observeConfig().first()
        return config.isConfigured && repository.hasToken()
    }
}
