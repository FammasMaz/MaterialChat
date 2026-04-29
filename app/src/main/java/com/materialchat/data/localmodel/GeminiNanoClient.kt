package com.materialchat.data.localmodel

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.materialchat.domain.model.LocalModelAvailability
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Thin wrapper around ML Kit Prompt API / Android AICore Gemini Nano.
 */
class GeminiNanoClient @Inject constructor() {
    suspend fun availability(): LocalModelAvailability {
        val model = Generation.getClient()
        return try {
            when (model.checkStatus()) {
                FeatureStatus.AVAILABLE -> LocalModelAvailability.AVAILABLE
                FeatureStatus.DOWNLOADABLE -> LocalModelAvailability.DOWNLOADABLE
                FeatureStatus.DOWNLOADING -> LocalModelAvailability.DOWNLOADING
                FeatureStatus.UNAVAILABLE -> LocalModelAvailability.UNAVAILABLE
                else -> LocalModelAvailability.UNAVAILABLE
            }
        } finally {
            closeQuietly(model)
        }
    }

    fun download(): Flow<Pair<Long, Long?>> = flow {
        val model = Generation.getClient()
        try {
            model.download().collect { status ->
                when (status) {
                    is DownloadStatus.DownloadStarted -> emit(0L to status.bytesToDownload)
                    is DownloadStatus.DownloadProgress -> emit(status.totalBytesDownloaded to null)
                    is DownloadStatus.DownloadCompleted -> emit(1L to 1L)
                    is DownloadStatus.DownloadFailed -> throw status.e
                }
            }
        } finally {
            closeQuietly(model)
        }
    }

    fun stream(prompt: String, maxOutputTokens: Int = 512): Flow<String> = flow {
        val model = Generation.getClient()
        try {
            ensureAvailable(model)
            val request = generateContentRequest(TextPart(prompt)) {
                temperature = 0.7f
                topK = 40
                this.maxOutputTokens = maxOutputTokens
            }
            model.generateContentStream(request).collect { chunk ->
                val text = chunk.candidates.firstOrNull()?.text.orEmpty()
                if (text.isNotEmpty()) emit(text)
            }
        } finally {
            closeQuietly(model)
        }
    }

    suspend fun generate(prompt: String, maxOutputTokens: Int = 128): String {
        val model = Generation.getClient()
        return try {
            ensureAvailable(model)
            val request = generateContentRequest(TextPart(prompt)) {
                temperature = 0.2f
                topK = 10
                this.maxOutputTokens = maxOutputTokens
            }
            model.generateContent(request).candidates.firstOrNull()?.text.orEmpty().trim()
        } finally {
            closeQuietly(model)
        }
    }

    private suspend fun ensureAvailable(model: GenerativeModel) {
        when (model.checkStatus()) {
            FeatureStatus.AVAILABLE -> model.warmup()
            FeatureStatus.DOWNLOADABLE -> {
                model.download().collect { status ->
                    if (status is DownloadStatus.DownloadFailed) throw status.e
                }
                model.warmup()
            }
            FeatureStatus.DOWNLOADING -> throw IllegalStateException("Gemini Nano is still downloading. Try again in a moment.")
            else -> throw IllegalStateException("Gemini Nano is unavailable on this device.")
        }
    }

    private fun closeQuietly(model: GenerativeModel) {
        runCatching { model.close() }
    }
}
