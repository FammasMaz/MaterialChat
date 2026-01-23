package com.materialchat.data.remote.sse

import com.materialchat.data.remote.api.StreamingEvent
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
     */
    private fun parseOpenAiChunk(chunk: OpenAiStreamChunk): StreamingEvent {
        val choices = chunk.choices
        if (choices.isEmpty()) {
            // No choices usually means initial connection or keep-alive
            return StreamingEvent.KeepAlive
        }

        val choice = choices.first()
        val delta = choice.delta
        val content = delta?.content
        val finishReason = choice.finishReason

        // First, check if there's content to emit (even if finish_reason is also present)
        // Some APIs (LiteLLM, etc.) send content and finish_reason in the same chunk
        if (!content.isNullOrEmpty()) {
            // If there's also a finish_reason, this is the final content chunk
            // The caller will get Done on the next iteration when finish_reason is set
            // but for APIs that combine them, we need to emit content first
            return if (finishReason != null) {
                // Combined final chunk: emit content with isFinal flag
                // The stream will end after this since finish_reason is set
                StreamingEvent.Content(
                    content = content,
                    isFirst = false
                )
            } else {
                // Normal content chunk
                StreamingEvent.Content(
                    content = content,
                    isFirst = false
                )
            }
        }

        // No content - check for completion signal
        if (finishReason != null) {
            return StreamingEvent.Done(
                finishReason = finishReason,
                model = chunk.model
            )
        }

        // Role-only delta (first message) or empty delta
        val role = delta?.role
        if (role != null) {
            // First chunk, return connected event
            return StreamingEvent.Connected
        }

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
