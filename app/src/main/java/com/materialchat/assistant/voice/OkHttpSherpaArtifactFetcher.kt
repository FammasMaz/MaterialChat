package com.materialchat.assistant.voice

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Downloads a model artifact over HTTPS, following the GitHub release redirect, streaming straight to disk.
 * Cancelling the coroutine cancels the call, which breaks the body read. A body larger than the
 * catalog size is rejected so a changed upstream asset can never be installed.
 */
class OkHttpSherpaArtifactFetcher internal constructor(private val calls: Call.Factory) : SherpaArtifactFetcher {
    constructor() : this(
        OkHttpClient.Builder()
            .followRedirects(true).followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    )

    override suspend fun fetch(url: String, expectedBytes: Long, target: File, onProgress: (Long) -> Unit) {
        require(url.startsWith("https://")) { "Model downloads must use HTTPS" }
        val call = calls.newCall(Request.Builder().url(url).header("Accept", "application/octet-stream").build())
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use {
                            if (!it.isSuccessful) throw SherpaInstallException("Download failed (HTTP ${it.code}). Try again later.")
                            val body = it.body ?: throw SherpaInstallException("Download returned no data. Try again.")
                            val length = body.contentLength()
                            if (length >= 0 && length != expectedBytes) throw SherpaInstallException(SIZE_CHANGED)
                            body.byteStream().use { input ->
                                FileOutputStream(target).use { output ->
                                    val buffer = ByteArray(1 shl 16)
                                    var total = 0L
                                    while (continuation.isActive) {
                                        val read = input.read(buffer)
                                        if (read < 0) break
                                        total += read
                                        if (total > expectedBytes) throw SherpaInstallException(SIZE_CHANGED)
                                        output.write(buffer, 0, read)
                                        onProgress(total)
                                    }
                                }
                            }
                        }
                        if (continuation.isActive) continuation.resume(Unit)
                    } catch (failure: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(failure)
                    }
                }
            })
        }
    }

    private companion object {
        const val SIZE_CHANGED = "The model file changed upstream and no longer matches this app version. Update the app."
    }
}
