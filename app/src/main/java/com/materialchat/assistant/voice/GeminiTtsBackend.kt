package com.materialchat.assistant.voice

import com.materialchat.data.local.preferences.AssistantVoiceSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One bounded utterance per collection. Cancel collection to stop its HTTP call and player.
 * The caller decrypts its encrypted credential immediately before calling; nothing is persisted.
 */
class GeminiTtsBackend(
    private val transport: GeminiTtsTransport = OkHttpGeminiTtsTransport(),
    private val player: GeminiPcmPlayer
) {
    fun speak(text: String, settings: AssistantVoiceSettings, key: String): Flow<TtsEvent> = flow {
        try {
            require(text.isNotBlank() && text.length <= MAX_TEXT_CHARS)
            require(key.isNotBlank() && key.length <= 512 && key.none { it.isISOControl() })
            require(settings.geminiModel in SUPPORTED_MODELS)
            require(settings.geminiVoice.matches(Regex("[A-Za-z][A-Za-z0-9_-]{0,63}")))
            val pcm = withTimeout(60_000) {
                val response = transport.generate(settings.geminiModel, key, request(text, settings.geminiVoice))
                withContext(Dispatchers.Default) { decode(response) }
            }
            try {
                currentCoroutineContext().ensureActive()
                emit(TtsEvent.Started)
                withTimeout(70_000) { player.play(pcm, SAMPLE_RATE) }
                emit(TtsEvent.Completed)
            } finally {
                pcm.fill(0)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Never expose server bodies, request URLs, credentials or exception messages.
            emit(TtsEvent.Error("Gemini speech failed. Check voice settings, quota and connectivity."))
        }
    }

    private fun request(text: String, voice: String): String = buildJsonObject {
        putJsonArray("contents") {
            addJsonObject { putJsonArray("parts") { addJsonObject { put("text", text) } } }
        }
        putJsonObject("generationConfig") {
            putJsonArray("responseModalities") { add("AUDIO") }
            putJsonObject("speechConfig") {
                putJsonObject("voiceConfig") {
                    putJsonObject("prebuiltVoiceConfig") { put("voiceName", voice) }
                }
            }
        }
    }.toString()

    internal fun decode(response: ByteArray): ByteArray {
        require(response.size <= MAX_RESPONSE_BYTES)
        val root = Json.parseToJsonElement(response.decodeToString(throwOnInvalidSequence = true)).jsonObject
        val candidates = root["candidates"]!!.jsonArray
        require(candidates.size == 1)
        val candidate = candidates.single().jsonObject
        require(candidate["finishReason"]?.jsonPrimitive?.content == "STOP")
        val parts = candidate["content"]!!.jsonObject["parts"]!!.jsonArray
        require(parts.size == 1)
        val audio = parts.single().jsonObject["inlineData"]!!.jsonObject
        val mime = audio["mimeType"]!!.jsonPrimitive.content.lowercase().replace(" ", "")
        require(mime == "audio/l16;codec=pcm;rate=24000" || mime == "audio/l16;rate=24000")
        val encoded = audio["data"]!!.jsonPrimitive.content
        require(encoded.length <= ((MAX_PCM_BYTES + 2) / 3) * 4)
        val pcm = Base64.getDecoder().decode(encoded)
        if (pcm.isEmpty() || pcm.size > MAX_PCM_BYTES || pcm.size % 2 != 0) {
            pcm.fill(0)
            error("Invalid audio")
        }
        return pcm
    }

    companion object {
        const val MAX_TEXT_CHARS = 4_000
        const val SAMPLE_RATE = 24_000
        const val MAX_PCM_BYTES = SAMPLE_RATE * 2 * 60
        const val MAX_RESPONSE_BYTES = 4_000_000
        // Only the verified generateContent raw-PCM schema, not newer Interactions/WAV models.
        val SUPPORTED_MODELS = setOf("gemini-3.1-flash-tts-preview", "gemini-2.5-pro-preview-tts", "gemini-2.5-flash-preview-tts")
    }
}

/** Implementations must stop playback and release resources before returning on cancellation.
 * PCM is signed little-endian, mono, 16-bit; the buffer is borrowed until play returns.
 */
fun interface GeminiPcmPlayer {
    suspend fun play(pcm: ByteArray, sampleRate: Int)
}

/** Per-call ownership: implementations must cancel pending I/O when this suspension is cancelled. */
fun interface GeminiTtsTransport {
    suspend fun generate(model: String, key: String, requestJson: String): ByteArray
}

/** Dedicated client deliberately has no application logging interceptors, cache, or redirects. */
class OkHttpGeminiTtsTransport : GeminiTtsTransport {
    private val client = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .callTimeout(60, TimeUnit.SECONDS).build()

    override suspend fun generate(model: String, key: String, requestJson: String): ByteArray {
        require(model in GeminiTtsBackend.SUPPORTED_MODELS)
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", key)
            .post(requestJson.toRequestBody("application/json".toMediaType())).build()
        val call = client.newCall(request)
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(IOException("Speech request failed"))
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val bytes = response.use {
                            require(it.isSuccessful)
                            val body = requireNotNull(it.body)
                            require(body.contentType()?.subtype == "json")
                            require(body.contentLength() <= GeminiTtsBackend.MAX_RESPONSE_BYTES)
                            val source = body.source()
                            val buffer = okio.Buffer()
                            while (true) {
                                val remaining = GeminiTtsBackend.MAX_RESPONSE_BYTES + 1L - buffer.size
                                require(remaining > 0)
                                val read = source.read(buffer, minOf(8192L, remaining))
                                if (read == -1L) break
                            }
                            require(buffer.size <= GeminiTtsBackend.MAX_RESPONSE_BYTES)
                            buffer.readByteArray()
                        }
                        continuation.resume(bytes)
                    } catch (_: Exception) {
                        continuation.resumeWithException(IOException("Invalid speech response"))
                    }
                }
            })
        }
    }
}
