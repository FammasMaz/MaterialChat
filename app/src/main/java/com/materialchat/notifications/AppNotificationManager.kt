package com.materialchat.notifications

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.materialchat.MainActivity
import com.materialchat.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Handles local notifications for app-level events.
 */
@Singleton
class AppNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val ACTION_OPENCLAW_REPLY: String = "com.materialchat.action.OPENCLAW_REPLY"
        const val EXTRA_SESSION_KEY: String = "extra_session_key"
        const val REPLY_REMOTE_INPUT_KEY: String = "reply_remote_input_key"

        private const val OPENCLAW_CHANNEL_ID = "openclaw_updates"
        private const val OPENCLAW_CHANNEL_NAME = "OpenClaw Updates"
        private const val OPENCLAW_CHANNEL_DESCRIPTION = "Agent responses and background updates"
        private const val OPENCLAW_NOTIFICATION_BASE_ID = 7_000
        private const val OPENCLAW_REPLY_STATUS_ID = 8_000
    }

    private val notificationManagerCompat by lazy { NotificationManagerCompat.from(context) }
    private val recentFingerprintTimestamps = LinkedHashMap<String, Long>()

    @Volatile
    private var channelsCreated: Boolean = false

    /**
     * Shows a notification for a completed OpenClaw assistant response.
     */
    fun notifyOpenClawResponse(sessionKey: String?, agentId: String, content: String) {
        if (!canPostNotifications()) return
        if (isAppInForeground()) return

        ensureChannels()

        val preview = content
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(220)
            .ifBlank { "Your agent sent a new response." }

        val fingerprint = "${sessionKey.orEmpty()}|$agentId|$preview"
        if (isDuplicateNotification(fingerprint)) return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val requestCode = (sessionKey ?: "openclaw").hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val titleAgent = agentId.ifBlank { "main" }
        val notificationBuilder = NotificationCompat.Builder(context, OPENCLAW_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("OpenClaw - $titleAgent")
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)

        if (!sessionKey.isNullOrBlank()) {
            val remoteInput = RemoteInput.Builder(REPLY_REMOTE_INPUT_KEY)
                .setLabel("Reply to agent")
                .build()

            val replyIntent = Intent(context, OpenClawReplyReceiver::class.java).apply {
                action = ACTION_OPENCLAW_REPLY
                putExtra(EXTRA_SESSION_KEY, sessionKey)
            }

            val replyPendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val replyAction = NotificationCompat.Action.Builder(
                R.drawable.ic_launcher_monochrome,
                "Reply",
                replyPendingIntent
            )
                .addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(true)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                .build()

            notificationBuilder.addAction(replyAction)
        }

        val notification = notificationBuilder.build()

        val stableId = OPENCLAW_NOTIFICATION_BASE_ID + abs(requestCode % 10_000)
        notificationManagerCompat.notify(stableId, notification)
    }

    fun notifyReplySent(sessionKey: String?) {
        if (!canPostNotifications()) return
        ensureChannels()

        val notification = NotificationCompat.Builder(context, OPENCLAW_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Reply sent")
            .setContentText("Your message was sent to the agent.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()

        val requestCode = (sessionKey ?: "reply").hashCode()
        val stableId = OPENCLAW_REPLY_STATUS_ID + abs(requestCode % 10_000)
        notificationManagerCompat.notify(stableId, notification)
    }

    fun notifyReplyFailed(sessionKey: String?, error: String?) {
        if (!canPostNotifications()) return
        ensureChannels()

        val notification = NotificationCompat.Builder(context, OPENCLAW_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Reply failed")
            .setContentText(error?.takeIf { it.isNotBlank() } ?: "Unable to send your reply.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()

        val requestCode = (sessionKey ?: "reply").hashCode()
        val stableId = OPENCLAW_REPLY_STATUS_ID + abs(requestCode % 10_000)
        notificationManagerCompat.notify(stableId, notification)
    }

    private fun ensureChannels() {
        if (channelsCreated) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                OPENCLAW_CHANNEL_ID,
                OPENCLAW_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = OPENCLAW_CHANNEL_DESCRIPTION
            }
            manager.createNotificationChannel(channel)
        }

        channelsCreated = true
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isAppInForeground(): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false
        val currentPid = Process.myPid()
        val processInfo = activityManager.runningAppProcesses
            ?.firstOrNull { it.pid == currentPid }
            ?: return false

        return processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
            processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
    }

    private fun isDuplicateNotification(fingerprint: String): Boolean {
        val now = System.currentTimeMillis()
        val previous = recentFingerprintTimestamps[fingerprint]
        recentFingerprintTimestamps[fingerprint] = now

        val cutoff = now - 20_000L
        val iterator = recentFingerprintTimestamps.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value < cutoff) {
                iterator.remove()
            }
        }

        return previous != null && (now - previous) < 20_000L
    }
}
