package com.materialchat.assistant.voice

import com.materialchat.data.local.preferences.AssistantVoiceSettings
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
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
                try {
                    currentCoroutineContext().ensureActive()
                    decode(response)
                } finally {
                    response.fill(0)
                }
            }
            try {
                currentCoroutineContext().ensureActive()
                emit(TtsEvent.Started)
                withTimeout(70_000) { player.play(pcm, SAMPLE_RATE) }
                emit(TtsEvent.Completed)
            } finally {
                pcm.fill(0)
            }
        } catch (_: TimeoutCancellationException) {
            // A synthesis deadline is a speech failure, not cancellation of the voice turn.
            currentCoroutineContext().ensureActive()
            emit(TtsEvent.Error("Gemini speech timed out. Try Android speech or retry."))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: GeminiTtsException) {
            emit(TtsEvent.Error(failure.message!!))
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
        val chunks = mutableListOf<ByteArray>()
        try {
            require(response.size <= MAX_RESPONSE_BYTES)
            val root = Json.parseToJsonElement(response.decodeToString(throwOnInvalidSequence = true)).jsonObject
            val candidates = root["candidates"]!!.jsonArray
            require(candidates.size == 1)
            val candidate = candidates.single().jsonObject
            require(candidate["finishReason"]?.jsonPrimitive?.content == "STOP")
            val parts = candidate["content"]!!.jsonObject["parts"]!!.jsonArray
            var total = 0
            for (element in parts) {
                val part = element.jsonObject
                val inline = part["inlineData"]
                if (inline == null) {
                    require(part["text"]?.jsonPrimitive?.isString == true ||
                        part["thought"]?.jsonPrimitive?.booleanOrNull == true)
                    continue
                }
                val audio = inline.jsonObject
                val mime = audio["mimeType"]!!.jsonPrimitive.content.lowercase().replace(" ", "")
                require(mime == "audio/l16;codec=pcm;rate=24000" || mime == "audio/l16;rate=24000")
                val encoded = audio["data"]!!.jsonPrimitive.content
                require(encoded.length <= ((MAX_PCM_BYTES - total + 2) / 3) * 4)
                val pcm = Base64.getDecoder().decode(encoded)
                chunks.add(pcm)
                require(pcm.isNotEmpty() && pcm.size % 2 == 0 && pcm.size <= MAX_PCM_BYTES - total)
                total += pcm.size
            }
            require(total > 0)
            return ByteArray(total).also { result ->
                var offset = 0
                for (chunk in chunks) {
                    chunk.copyInto(result, offset)
                    offset += chunk.size
                }
            }
        } catch (_: Exception) {
            throw GeminiTtsException(GeminiTtsFailure.INVALID_AUDIO)
        } finally {
            chunks.forEach { it.fill(0) }
        }
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
class OkHttpGeminiTtsTransport internal constructor(private val calls: Call.Factory) : GeminiTtsTransport {
    constructor() : this(OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .callTimeout(60, TimeUnit.SECONDS).build()
    )

    override suspend fun generate(model: String, key: String, requestJson: String): ByteArray {
        require(model in GeminiTtsBackend.SUPPORTED_MODELS)
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", key)
            .post(requestJson.toRequestBody("application/json".toMediaType())).build()
        val call = calls.newCall(request)
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(GeminiTtsException(GeminiTtsFailure.TRANSPORT))
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val bytes = response.use {
                            if (!it.isSuccessful) throw GeminiTtsException(GeminiTtsFailure.fromHttp(it.code))
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
                        continuation.resume(bytes) { _, value, _ -> value.fill(0) }
                    } catch (failure: GeminiTtsException) {
                        continuation.resumeWithException(failure)
                    } catch (_: IOException) {
                        continuation.resumeWithException(GeminiTtsException(GeminiTtsFailure.TRANSPORT))
                    } catch (_: Exception) {
                        continuation.resumeWithException(GeminiTtsException(GeminiTtsFailure.INVALID_AUDIO))
                    }
                }
            })
        }
    }
}

/** Fixed diagnostics only: never retain a provider body, URL, credential or underlying cause. */
enum class GeminiTtsFailure(val diagnostic: String) {
    AUTHENTICATION("Gemini speech authentication failed. Check the speech API key."),
    PERMISSION("Gemini speech permission denied. Check project and API access."),
    QUOTA("Gemini speech quota or rate limit reached. Retry later."),
    MODEL_UNAVAILABLE("Gemini speech model unavailable. Check the selected model."),
    TRANSPORT("Gemini speech service or connection failed. Retry later."),
    INVALID_AUDIO("Gemini speech returned invalid or unsupported audio.");

    companion object {
        internal fun fromHttp(code: Int): GeminiTtsFailure = when (code) {
            401 -> AUTHENTICATION
            403 -> PERMISSION
            429 -> QUOTA
            404 -> MODEL_UNAVAILABLE
            else -> TRANSPORT
        }
    }
}

class GeminiTtsException(val kind: GeminiTtsFailure) : IOException(kind.diagnostic)
