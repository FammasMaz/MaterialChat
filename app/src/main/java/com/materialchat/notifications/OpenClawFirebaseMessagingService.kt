package com.materialchat.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OpenClawFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var appNotificationManager: AppNotificationManager

    @Inject
    lateinit var openClawPushSyncManager: OpenClawPushSyncManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val type = message.data["type"]
        if (type != "openclaw_chat") return

        val content = message.data["contentPreview"]
            ?: message.data["content"]
            ?: message.notification?.body
            ?: return

        val agentId = message.data["agentId"].orEmpty().ifBlank { "main" }
        val sessionKey = message.data["sessionKey"]

        appNotificationManager.notifyOpenClawResponse(
            sessionKey = sessionKey,
            agentId = agentId,
            content = content
        )
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch {
            openClawPushSyncManager.onTokenRefreshed(token)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
