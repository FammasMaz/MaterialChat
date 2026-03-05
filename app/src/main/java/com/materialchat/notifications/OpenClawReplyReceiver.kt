package com.materialchat.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.materialchat.domain.model.openclaw.GatewayConnectionState
import com.materialchat.domain.usecase.openclaw.ConnectGatewayUseCase
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class OpenClawReplyReceiver : BroadcastReceiver() {

    companion object {
        private const val CONNECTION_TIMEOUT_MS: Long = 15_000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AppNotificationManager.ACTION_OPENCLAW_REPLY) return

        val sessionKey = intent.getStringExtra(AppNotificationManager.EXTRA_SESSION_KEY)
            ?.takeIf { it.isNotBlank() }
            ?: return

        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val replyText = remoteInput
            ?.getCharSequence(AppNotificationManager.REPLY_REMOTE_INPUT_KEY)
            ?.toString()
            ?.trim()
            .orEmpty()

        if (replyText.isBlank()) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            val entryPoint = EntryPointAccessors.fromApplication(
                appContext,
                OpenClawNotificationEntryPoint::class.java
            )

            val connectGatewayUseCase = entryPoint.connectGatewayUseCase()
            val openClawChatUseCase = entryPoint.openClawChatUseCase()
            val notificationManager = entryPoint.appNotificationManager()

            var connectedLocally = false

            try {
                if (!connectGatewayUseCase.isConnected()) {
                    connectGatewayUseCase.connect()
                    connectedLocally = true
                    awaitConnected(connectGatewayUseCase)
                }

                openClawChatUseCase.sendMessage(sessionKey = sessionKey, message = replyText)
                notificationManager.notifyReplySent(sessionKey)
            } catch (e: Exception) {
                notificationManager.notifyReplyFailed(sessionKey, e.message)
            } finally {
                if (connectedLocally) {
                    connectGatewayUseCase.disconnect()
                }
                pendingResult.finish()
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
