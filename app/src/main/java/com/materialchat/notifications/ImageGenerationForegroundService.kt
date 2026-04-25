package com.materialchat.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.materialchat.R

class ImageGenerationForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "MaterialChat is working"
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: "Keeping the request active in the background."
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(title, text),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        return START_STICKY
    }

    private fun buildNotification(title: String, text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
            .apply {
                if (Build.VERSION.SDK_INT >= 36) {
                    flags = flags or Notification.FLAG_PROMOTED_ONGOING
                }
            }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Active AI work",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps long chat and image requests running while MaterialChat is backgrounded"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "active_ai_work"
        private const val NOTIFICATION_ID = 12_401
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_TEXT = "extra_text"

        fun startImage(context: Context, modelName: String) {
            start(
                context = context,
                title = "Creating image",
                text = "Using $modelName. This may take a few minutes."
            )
        }

        fun startChat(context: Context, modelName: String) {
            start(
                context = context,
                title = "Generating response",
                text = "Streaming with $modelName."
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ImageGenerationForegroundService::class.java))
        }

        private fun start(context: Context, title: String, text: String) {
            runCatching {
                val intent = Intent(context, ImageGenerationForegroundService::class.java).apply {
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_TEXT, text)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }
    }
}
