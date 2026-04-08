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
        const val EXTRA_SESSION_KEY: String = "extra_session_key"
        const val REPLY_REMOTE_INPUT_KEY: String = "reply_remote_input_key"

        private const val CHAT_CHANNEL_ID = "chat_responses"
        private const val CHAT_CHANNEL_NAME = "Chat Responses"
        private const val CHAT_CHANNEL_DESCRIPTION = "Notifications when AI responses complete in the background"
        private const val CHAT_NOTIFICATION_BASE_ID = 9_000
    }

    private val notificationManagerCompat by lazy { NotificationManagerCompat.from(context) }
    private val recentFingerprintTimestamps = LinkedHashMap<String, Long>()

    @Volatile
    private var channelsCreated: Boolean = false

    /**
     * Shows a notification when a regular chat AI response completes in the background.
     * Only shows when the app is not in the foreground.
     */
    fun notifyChatResponseComplete(conversationId: String, preview: String) {
        if (!canPostNotifications()) return
        if (isAppInForeground()) return

        ensureChannels()

        val contentPreview = preview
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(220)
            .ifBlank { "Your AI response is ready." }

        val fingerprint = "chat|$conversationId|$contentPreview"
        if (isDuplicateNotification(fingerprint)) return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val requestCode = conversationId.hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHAT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Response ready")
            .setContentText(contentPreview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentPreview))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()

        val stableId = CHAT_NOTIFICATION_BASE_ID + abs(requestCode % 10_000)
        notificationManagerCompat.notify(stableId, notification)
    }

    private fun ensureChannels() {
        if (channelsCreated) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val chatChannel = NotificationChannel(
                CHAT_CHANNEL_ID,
                CHAT_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHAT_CHANNEL_DESCRIPTION
            }
            manager.createNotificationChannel(chatChannel)
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
