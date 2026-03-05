package com.materialchat.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.materialchat.domain.model.openclaw.GatewayConnectionState
import com.materialchat.domain.model.openclaw.OpenClawChatRole
import com.materialchat.domain.usecase.openclaw.ConnectGatewayUseCase
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

class OpenClawNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val UNIQUE_WORK_NAME: String = "openclaw_notification_sync"
        private const val CURSOR_PREFS_NAME: String = "openclaw_notification_cursor"
        private const val CONNECTION_TIMEOUT_MS: Long = 15_000L
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            OpenClawNotificationEntryPoint::class.java
        )

        val appPreferences = entryPoint.appPreferences()
        if (!appPreferences.notificationsEnabled.first()) return Result.success()

        val connectGatewayUseCase = entryPoint.connectGatewayUseCase()
        val manageOpenClawConfigUseCase = entryPoint.manageOpenClawConfigUseCase()
        val manageOpenClawSessionsUseCase = entryPoint.manageOpenClawSessionsUseCase()
        val notificationManager = entryPoint.appNotificationManager()

        val targetAgentId = manageOpenClawConfigUseCase.observeConfig().first().agentId.ifBlank { "main" }

        var connectedLocally = false

        return try {
            if (!connectGatewayUseCase.isConnected()) {
                connectGatewayUseCase.connect()
                connectedLocally = true
                awaitConnected(connectGatewayUseCase)
            }

            val cursorPrefs = applicationContext.getSharedPreferences(CURSOR_PREFS_NAME, Context.MODE_PRIVATE)

            val latestSession = manageOpenClawSessionsUseCase.listSessions()
                .filter { it.agentId == targetAgentId }
                .sortedByDescending { it.lastActivity }
                .firstOrNull()

            if (latestSession != null) {
                val messages = manageOpenClawSessionsUseCase.getChatHistory(latestSession.key, limit = 20)
                val latestAssistantMessage = messages
                    .asReversed()
                    .firstOrNull { message ->
                        message.role == OpenClawChatRole.ASSISTANT && message.content.isNotBlank()
                    }

                if (latestAssistantMessage != null) {
                    val sessionCursorKey = "session_${latestSession.key}"
                    val messageTimestamp = when {
                        latestAssistantMessage.timestamp > 0L -> latestAssistantMessage.timestamp
                        latestSession.lastActivity > 0L -> latestSession.lastActivity
                        else -> System.currentTimeMillis()
                    }

                    if (!cursorPrefs.contains(sessionCursorKey)) {
                        cursorPrefs.edit().putLong(sessionCursorKey, messageTimestamp).apply()
                    } else {
                        val previousTimestamp = cursorPrefs.getLong(sessionCursorKey, 0L)
                        if (messageTimestamp > previousTimestamp) {
                            notificationManager.notifyOpenClawResponse(
                                sessionKey = latestSession.key,
                                agentId = targetAgentId,
                                content = latestAssistantMessage.content
                            )
                            cursorPrefs.edit().putLong(sessionCursorKey, messageTimestamp).apply()
                        }
                    }
                }
            }

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        } finally {
            if (connectedLocally) {
                connectGatewayUseCase.disconnect()
            }
        }
    }

    private suspend fun awaitConnected(connectGatewayUseCase: ConnectGatewayUseCase) {
        withTimeout(CONNECTION_TIMEOUT_MS) {
            val state = connectGatewayUseCase.connectionState
                .filter {
                    it is GatewayConnectionState.Connected ||
                        it is GatewayConnectionState.Error ||
                        it is GatewayConnectionState.Disconnected
                }
                .first()

            if (state !is GatewayConnectionState.Connected) {
                throw IllegalStateException("Unable to connect to OpenClaw")
            }
        }
    }
}
