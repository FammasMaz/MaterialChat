package com.materialchat.data.remote.sse

import com.materialchat.data.remote.api.StreamingEvent
import com.materialchat.data.remote.dto.AntigravityStreamChunk
import com.materialchat.data.remote.dto.OllamaChatResponse
import com.materialchat.data.remote.dto.OpenAiStreamChunk
import kotlinx.serialization.json.Json

/**
 * Parser for Server-Sent Events (SSE) and NDJSON streaming formats.
 *
 * Supports:
 * - OpenAI SSE format: Lines prefixed with "data: " followed by JSON
 * - Ollama NDJSON format: Each line is a complete JSON object
 *
 * Thread-safe and stateless - each parse call is independent.
 */
class SseEventParser(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
) {

    /**
     * Parses an OpenAI SSE event line.
     *
     * OpenAI SSE format:
     * - Lines starting with "data: " contain JSON payload
     * - "data: [DONE]" signals end of stream
     * - Empty lines or lines starting with ":" are ignored (keep-alive/comments)
     *
     * @param line The raw SSE line from the stream
     * @return StreamingEvent representing the parsed event, or null if the line should be ignored
     */
    fun parseOpenAiEvent(line: String): StreamingEvent? {
        val trimmedLine = line.trim()

        // Empty lines or comment lines (starting with :) are keep-alive signals
        if (trimmedLine.isEmpty() || trimmedLine.startsWith(":")) {
            return StreamingEvent.KeepAlive
        }

        // Must start with "data: " prefix
        if (!trimmedLine.startsWith(DATA_PREFIX)) {
            // Unknown line format, skip it
            return null
        }

        // Extract the data payload
        val data = trimmedLine.removePrefix(DATA_PREFIX).trim()

        // Check for stream termination
        if (data == DONE_MARKER) {
            return StreamingEvent.Done()
        }

        // Empty data after prefix is a keep-alive
        if (data.isEmpty()) {
            return StreamingEvent.KeepAlive
        }

        // Parse JSON payload
        return try {
            val chunk = json.decodeFromString<OpenAiStreamChunk>(data)
            parseOpenAiChunk(chunk)
        } catch (e: Exception) {
            // Try to parse as error response
            tryParseOpenAiError(data) ?: StreamingEvent.Error(
                message = "Failed to parse SSE event: ${e.message}",
                code = StreamingEvent.Companion.ErrorCode.PARSE_ERROR,
                isRecoverable = false
            )
        }
    }

    /**
     * Parses an Ollama NDJSON event line.
     *
     * Ollama NDJSON format:
     * - Each line is a complete JSON object
     * - The "done" field indicates stream completion
     * - Content is in message.content field
     *
     * @param line The raw NDJSON line from the stream
     * @return StreamingEvent representing the parsed event, or null if the line should be ignored
     */
    fun parseOllamaEvent(line: String): StreamingEvent? {
        val trimmedLine = line.trim()

        // Empty lines should be ignored
        if (trimmedLine.isEmpty()) {
            return StreamingEvent.KeepAlive
        }

        // Parse JSON
        return try {
            val response = json.decodeFromString<OllamaChatResponse>(trimmedLine)
            parseOllamaResponse(response)
        } catch (e: Exception) {
            // Try to parse as error response
            tryParseOllamaError(trimmedLine) ?: StreamingEvent.Error(
                message = "Failed to parse NDJSON event: ${e.message}",
                code = StreamingEvent.Companion.ErrorCode.PARSE_ERROR,
                isRecoverable = false
            )
        }
    }

    /**
     * Parses an OpenAI stream chunk into a StreamingEvent.
     * Handles APIs that may include content and finish_reason in the same chunk.
     * Also handles APIs that put content in message field instead of delta.
     *
     * Note: Some APIs (like LiteLLM) send finish_reason on EVERY chunk, not just the last.
     * We ignore finish_reason here and rely on the [DONE] marker for stream termination.
     */
    private fun parseOpenAiChunk(chunk: OpenAiStreamChunk): StreamingEvent {
        val choices = chunk.choices
        if (choices.isEmpty()) {
            // No choices usually means initial connection or keep-alive
            return StreamingEvent.KeepAlive
        }

        val choice = choices.first()
        val delta = choice.delta
        val message = choice.message

        // Check for content in delta first (standard streaming format)
        val deltaContent = delta?.content?.takeIf { it.isNotEmpty() }

        // Fallback: check message.content (some APIs put content here instead of delta)
        val messageContent = message?.content?.takeIf { it.isNotEmpty() }

        // Use whichever has content
        val content = deltaContent ?: messageContent

        // Extract thinking/reasoning from delta or message
        // Different providers use different field names
        val deltaThinking = delta?.thinking
            ?: delta?.reasoning
            ?: delta?.reasoningContent
        val messageThinking = message?.thinking
            ?: message?.reasoning
            ?: message?.reasoningContent
        val thinking = deltaThinking?.takeIf { it.isNotEmpty() }
            ?: messageThinking?.takeIf { it.isNotEmpty() }

        // If there's content or thinking, emit it
        if (!content.isNullOrEmpty() || !thinking.isNullOrEmpty()) {
            return StreamingEvent.Content(
                content = content ?: "",
                thinking = thinking,
                isFirst = false
            )
        }

        // Role-only delta (first message) or empty delta
        val role = delta?.role
        if (role != null) {
            // First chunk with role info
            return StreamingEvent.Connected
        }

        // Empty chunk - could be keep-alive or final chunk before [DONE]
        // Don't return Done here - let the [DONE] marker handle termination
        return StreamingEvent.KeepAlive
    }

    /**
     * Parses an Ollama response into a StreamingEvent.
     * Handles both content and thinking (reasoning) fields.
     */
    private fun parseOllamaResponse(response: OllamaChatResponse): StreamingEvent {
        // Check if stream is done
        if (response.done) {
            return StreamingEvent.Done(
                finishReason = StreamingEvent.Companion.FinishReason.STOP,
                model = response.model
            )
        }

        // Extract content and thinking from message
        val content = response.message?.content
        val thinking = response.message?.thinking

        // No content or thinking in this chunk
        if ((content == null || content.isEmpty()) && (thinking == null || thinking.isEmpty())) {
            return StreamingEvent.KeepAlive
        }

        // Return content chunk with optional thinking
        return StreamingEvent.Content(
            content = content ?: "",
            thinking = thinking,
            isFirst = false
        )
    }

    /**
     * Attempts to parse an OpenAI error response.
     */
    private fun tryParseOpenAiError(data: String): StreamingEvent.Error? {
        return try {
            // Try to parse as error object
            val errorWrapper = json.decodeFromString<OpenAiErrorWrapper>(data)
            errorWrapper.error?.let { error ->
                StreamingEvent.Error(
                    message = error.message ?: "Unknown OpenAI error",
                    code = error.code ?: error.type,
                    isRecoverable = false
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Attempts to parse an Ollama error response.
     */
    private fun tryParseOllamaError(data: String): StreamingEvent.Error? {
        return try {
            val errorResponse = json.decodeFromString<OllamaErrorWrapper>(data)
            errorResponse.error?.let { errorMessage ->
                StreamingEvent.Error(
                    message = errorMessage,
                    code = null,
                    isRecoverable = false
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parses multiple SSE lines (OpenAI format) and returns all events.
     * Useful for parsing buffered content.
     *
     * @param content Multi-line SSE content
     * @return List of non-null StreamingEvents
     */
    fun parseOpenAiEvents(content: String): List<StreamingEvent> {
        return content.lineSequence()
            .mapNotNull { parseOpenAiEvent(it) }
            .filter { it != StreamingEvent.KeepAlive }
            .toList()
    }

    /**
     * Parses multiple NDJSON lines (Ollama format) and returns all events.
     * Useful for parsing buffered content.
     *
     * @param content Multi-line NDJSON content
     * @return List of non-null StreamingEvents
     */
    fun parseOllamaEvents(content: String): List<StreamingEvent> {
        return content.lineSequence()
            .mapNotNull { parseOllamaEvent(it) }
            .filter { it != StreamingEvent.KeepAlive }
            .toList()
    }

    /**
     * Parses an Antigravity SSE event line (Gemini-style format).
     *
     * Antigravity SSE format:
     * - Lines starting with "data: " contain JSON payload
     * - JSON has `candidates` array with `content.parts`
     * - Parts may have `text` for content or `thought: true` for thinking
     *
     * @param line The raw SSE line from the stream
     * @return StreamingEvent representing the parsed event, or null if the line should be ignored
     */
    fun parseAntigravityEvent(line: String): StreamingEvent? {
        val trimmedLine = line.trim()

        // Empty lines or comment lines (starting with :) are keep-alive signals
        if (trimmedLine.isEmpty() || trimmedLine.startsWith(":")) {
            return StreamingEvent.KeepAlive
        }

        // Must start with "data: " prefix
        if (!trimmedLine.startsWith(DATA_PREFIX)) {
            // Unknown line format, skip it
            return null
        }

        // Extract the data payload
        val data = trimmedLine.removePrefix(DATA_PREFIX).trim()

        // Check for stream termination (Antigravity may use [DONE] or empty data)
        if (data == DONE_MARKER || data.isEmpty()) {
            return if (data == DONE_MARKER) {
                StreamingEvent.Done()
            } else {
                StreamingEvent.KeepAlive
            }
        }

        // Parse JSON payload
        return try {
            val chunk = json.decodeFromString<AntigravityStreamChunk>(data)
            parseAntigravityChunk(chunk)
        } catch (e: Exception) {
            // Try to parse as error response
            tryParseAntigravityError(data) ?: StreamingEvent.Error(
                message = "Failed to parse Antigravity SSE event: ${e.message}",
                code = StreamingEvent.Companion.ErrorCode.PARSE_ERROR,
                isRecoverable = false
            )
        }
    }

    /**
     * Parses an Antigravity stream chunk into a StreamingEvent.
     */
    private fun parseAntigravityChunk(chunk: AntigravityStreamChunk): StreamingEvent {
        val candidates = chunk.candidates
        if (candidates.isNullOrEmpty()) {
            // No candidates usually means initial connection or keep-alive
            return StreamingEvent.KeepAlive
        }

        val candidate = candidates.first()

        // Check for finish reason
        val finishReason = candidate.finishReason
        if (finishReason != null && finishReason != "STOP" && candidate.content == null) {
            // Blocked or error finish without content
            return StreamingEvent.Done(
                finishReason = when (finishReason) {
                    "STOP" -> StreamingEvent.Companion.FinishReason.STOP
                    "MAX_TOKENS" -> StreamingEvent.Companion.FinishReason.LENGTH
                    "SAFETY" -> StreamingEvent.Companion.FinishReason.CONTENT_FILTER
                    else -> StreamingEvent.Companion.FinishReason.STOP
                }
            )
        }

        val content = candidate.content ?: return StreamingEvent.KeepAlive
        val parts = content.parts

        // Extract text and thinking from parts
        val textParts = StringBuilder()
        val thinkingParts = StringBuilder()

        for (part in parts) {
            val text = part.text ?: continue
            if (part.thought == true) {
                thinkingParts.append(text)
            } else {
                textParts.append(text)
            }
        }

        val textContent = textParts.toString()
        val thinkingContent = thinkingParts.toString().takeIf { it.isNotEmpty() }

        // If there's content or thinking, emit it
        if (textContent.isNotEmpty() || thinkingContent != null) {
            return StreamingEvent.Content(
                content = textContent,
                thinking = thinkingContent,
                isFirst = false
            )
        }

        // Check if this is the first chunk with role
        if (content.role == "model" && parts.isEmpty()) {
            return StreamingEvent.Connected
        }

        // Check for finish reason with content (final chunk)
        if (finishReason == "STOP" && textContent.isEmpty() && thinkingContent == null) {
            return StreamingEvent.Done(finishReason = StreamingEvent.Companion.FinishReason.STOP)
        }

        return StreamingEvent.KeepAlive
    }

    /**
     * Attempts to parse an Antigravity error response.
     */
    private fun tryParseAntigravityError(data: String): StreamingEvent.Error? {
        return try {
            val errorWrapper = json.decodeFromString<AntigravityErrorWrapper>(data)
            errorWrapper.error?.let { error ->
                StreamingEvent.Error(
                    message = error.message ?: "Unknown Antigravity error",
                    code = error.status ?: error.code?.toString(),
                    isRecoverable = error.code in 500..599
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parses multiple SSE lines (Antigravity format) and returns all events.
     * Useful for parsing buffered content.
     *
     * @param content Multi-line SSE content
     * @return List of non-null StreamingEvents
     */
    fun parseAntigravityEvents(content: String): List<StreamingEvent> {
        return content.lineSequence()
            .mapNotNull { parseAntigravityEvent(it) }
            .filter { it != StreamingEvent.KeepAlive }
            .toList()
    }

    companion object {
        private const val DATA_PREFIX = "data:"
        private const val DONE_MARKER = "[DONE]"

        /**
         * Singleton instance with default configuration.
         */
        val default: SseEventParser by lazy { SseEventParser() }
    }
}

/**
 * Internal wrapper for parsing OpenAI error responses.
 */
@kotlinx.serialization.Serializable
private data class OpenAiErrorWrapper(
    val error: OpenAiErrorDetail? = null
)

@kotlinx.serialization.Serializable
private data class OpenAiErrorDetail(
    val message: String? = null,
    val type: String? = null,
    val param: String? = null,
    val code: String? = null
)

/**
 * Internal wrapper for parsing Ollama error responses.
 */
@kotlinx.serialization.Serializable
private data class OllamaErrorWrapper(
    val error: String? = null
)

/**
 * Internal wrapper for parsing Antigravity error responses.
 */
@kotlinx.serialization.Serializable
private data class AntigravityErrorWrapper(
    val error: AntigravityErrorDetail? = null
)

@kotlinx.serialization.Serializable
private data class AntigravityErrorDetail(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null
)
