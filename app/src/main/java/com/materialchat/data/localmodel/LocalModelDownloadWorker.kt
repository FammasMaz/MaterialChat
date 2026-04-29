package com.materialchat.data.localmodel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.materialchat.MainActivity
import com.materialchat.R
import com.materialchat.data.local.preferences.EncryptedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class LocalModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val modelId = inputData.getString(KEY_MODEL_ID)
            ?: return@withContext Result.failure(errorData("Missing model id"))
        val descriptor = LocalModelCatalog.descriptor(modelId)
            ?: return@withContext Result.failure(errorData("Unknown on-device model"))
        val url = descriptor.downloadUrl
            ?: return@withContext Result.failure(errorData("No download URL for ${descriptor.displayName}"))
        val target = LocalModelCatalog.modelFile(applicationContext, descriptor)

        if (target.exists()) {
            setProgress(progressData(modelId, target.length(), target.length()))
            return@withContext Result.success()
        }

        try {
            ensureChannel()
            setForeground(foregroundInfo(descriptor.displayName, 0L, descriptor.approximateSizeBytes))
            downloadFile(modelId, descriptor.displayName, url, target, descriptor.approximateSizeBytes)
            Result.success()
        } catch (e: Exception) {
            File(target.parentFile, "${target.name}.download").delete()
            Result.failure(errorData(e.message ?: "Model download failed"))
        }
    }

    private suspend fun downloadFile(
        modelId: String,
        displayName: String,
        url: String,
        target: File,
        approximateSizeBytes: Long?
    ) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.download")
        if (tmp.exists()) tmp.delete()

        val requestBuilder = Request.Builder().url(url).get()
        if (url.startsWith("https://huggingface.co")) {
            EncryptedPreferences(applicationContext)
                .getApiKey(HUGGING_FACE_TOKEN_ID)
                ?.let { token -> requestBuilder.addHeader("Authorization", "Bearer $token") }
        }

        val response = httpClient.newCall(requestBuilder.build()).execute()
        response.use {
            if (!it.isSuccessful) {
                val hint = if (it.code == 401 || it.code == 403) {
                    " Open the Gemma page, accept the license, then save a Hugging Face read token in MaterialChat."
                } else ""
                throw IOException("HTTP ${it.code} while downloading $displayName.$hint")
            }
            val body = it.body ?: throw IOException("Empty model download response")
            val total = body.contentLength().takeIf { length -> length > 0L } ?: approximateSizeBytes
            body.byteStream().use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    var lastEmit = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded - lastEmit >= PROGRESS_EMIT_BYTES) {
                            lastEmit = downloaded
                            setProgress(progressData(modelId, downloaded, total))
                            setForeground(foregroundInfo(displayName, downloaded, total))
                        }
                    }
                    output.flush()
                    setProgress(progressData(modelId, downloaded, total))
                }
            }
        }

        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    private fun foregroundInfo(displayName: String, downloaded: Long, total: Long?): ForegroundInfo {
        val notification = buildNotification(displayName, downloaded, total)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(displayName: String, downloaded: Long, total: Long?): Notification {
        val progress = total?.takeIf { it > 0L }?.let { totalBytes ->
            ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
        }
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Downloading on-device model")
            .setContentText(displayName)
            .setContentIntent(contentIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .apply {
                if (progress == null) setProgress(0, 0, true) else setProgress(100, progress, false)
            }
            .build()
    }

    private fun contentIntent(): PendingIntent {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Model downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows progress for on-device model downloads"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val KEY_MODEL_ID = "model_id"
        const val KEY_DOWNLOADED_BYTES = "downloaded_bytes"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_ERROR = "error"
        const val WORK_TAG = "local_model_download"

        private const val CHANNEL_ID = "model_downloads"
        private const val NOTIFICATION_ID = 24_017
        private const val PROGRESS_EMIT_BYTES = 1024L * 1024L
        private const val HUGGING_FACE_TOKEN_ID = "huggingface_access_token"

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        fun uniqueWorkName(modelId: String): String {
            return "local_model_download_" + modelId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        }

        fun inputData(modelId: String): Data = Data.Builder()
            .putString(KEY_MODEL_ID, modelId)
            .build()

        fun progressData(modelId: String, downloadedBytes: Long, totalBytes: Long?): Data {
            return Data.Builder()
                .putString(KEY_MODEL_ID, modelId)
                .putLong(KEY_DOWNLOADED_BYTES, downloadedBytes)
                .apply { totalBytes?.let { putLong(KEY_TOTAL_BYTES, it) } }
                .build()
        }

        fun errorData(message: String): Data = Data.Builder()
            .putString(KEY_ERROR, message)
            .build()
    }
}
