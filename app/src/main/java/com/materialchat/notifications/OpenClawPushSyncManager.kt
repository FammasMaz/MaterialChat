package com.materialchat.notifications

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.data.local.preferences.OpenClawPreferences
import com.materialchat.domain.usecase.openclaw.ManageOpenClawConfigUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class OpenClawPushSyncManager @Inject constructor(
    private val appPreferences: AppPreferences,
    private val manageOpenClawConfigUseCase: ManageOpenClawConfigUseCase,
    private val relayClient: OpenClawFcmRelayClient
) {

    suspend fun checkServerPushEndpoint(): Boolean {
        val config = manageOpenClawConfigUseCase.observeConfig().first()
        val gatewayToken = manageOpenClawConfigUseCase.getToken().orEmpty().trim()
        if (!config.isConfigured || gatewayToken.isBlank()) return false

        return runCatching {
            relayClient.isPushEndpointAvailable(
                gatewayBaseUrl = config.httpUrl.trimEnd('/'),
                gatewayToken = gatewayToken
            )
        }.getOrDefault(false)
    }

    suspend fun syncRegistration(): Boolean {
        return syncRegistrationInternal(tokenOverride = null)
    }

    suspend fun onTokenRefreshed(newToken: String): Boolean {
        return syncRegistrationInternal(tokenOverride = newToken.trim())
    }

    private suspend fun syncRegistrationInternal(tokenOverride: String?): Boolean {
        val notificationsEnabled = appPreferences.notificationsEnabled.first()
        val config = manageOpenClawConfigUseCase.observeConfig().first()
        val previousToken = appPreferences.openClawFcmLastToken.first()
        val gatewayBaseUrl = config.httpUrl.trimEnd('/')
        val gatewayToken = manageOpenClawConfigUseCase.getToken().orEmpty().trim()
        val hasGatewayAuth = gatewayToken.isNotBlank()

        val shouldRegister =
            notificationsEnabled && config.isEnabled && config.isConfigured && hasGatewayAuth
        if (!shouldRegister) {
            if (previousToken.isNotBlank()) {
                runCatching {
                    relayClient.unregisterToken(
                        gatewayBaseUrl = gatewayBaseUrl,
                        gatewayToken = gatewayToken,
                        token = previousToken
                    )
                }
                appPreferences.setOpenClawFcmLastToken("")
            }
            return false
        }

        val resolvedAgentId = config.agentId.ifBlank { OpenClawPreferences.DEFAULT_AGENT_ID }
        val currentToken = tokenOverride.takeUnless { it.isNullOrBlank() } ?: fetchCurrentToken()
        if (currentToken.isNullOrBlank()) return false

        if (previousToken.isNotBlank() && previousToken != currentToken) {
            runCatching {
                relayClient.unregisterToken(
                    gatewayBaseUrl = gatewayBaseUrl,
                    gatewayToken = gatewayToken,
                    token = previousToken
                )
            }
        }

        val registered = runCatching {
            relayClient.registerToken(
                gatewayBaseUrl = gatewayBaseUrl,
                gatewayToken = gatewayToken,
                token = currentToken,
                agentId = resolvedAgentId
            )
        }.getOrElse {
            Log.w("OpenClawPushSync", "Token registration failed: ${it.message}")
            false
        }

        if (registered) {
            appPreferences.setOpenClawFcmLastToken(currentToken)
        }
        return registered
    }

    private suspend fun fetchCurrentToken(): String? {
        return runCatching {
            FirebaseMessaging.getInstance().token.awaitResult().trim()
        }.getOrElse {
            Log.d("OpenClawPushSync", "FCM token unavailable: ${it.message}")
            null
        }
    }

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (!continuation.isActive) return@addOnCompleteListener
            val result = task.result
            if (task.isSuccessful && result != null) {
                continuation.resume(result)
            } else {
                continuation.resumeWith(Result.failure(task.exception ?: IllegalStateException("Task failed")))
            }
        }
    }
}
