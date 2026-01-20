package com.materialchat.data.remote.api

/**
 * Represents events that occur during SSE/NDJSON streaming from AI providers.
 * This sealed class provides a unified event type for both OpenAI and Ollama streaming formats.
 */
sealed class StreamingEvent {

    /**
     * A chunk of content received during streaming.
     *
     * @param content The partial text content from this chunk.
     *                For OpenAI: from choices[0].delta.content
     *                For Ollama: from message.content
     * @param isFirst Whether this is the first content chunk (may include role info).
     */
    data class Content(
        val content: String,
        val isFirst: Boolean = false
    ) : StreamingEvent()

    /**
     * The streaming has completed successfully.
     *
     * @param finishReason The reason streaming ended (e.g., "stop", "length", "content_filter").
     *                     May be null for Ollama responses.
     * @param model The model that generated the response (if available).
     */
    data class Done(
        val finishReason: String? = null,
        val model: String? = null
    ) : StreamingEvent()

    /**
     * An error occurred during streaming.
     *
     * @param message Human-readable error message.
     * @param code Error code (HTTP status or provider-specific code).
     * @param isRecoverable Whether the error might be resolved by retrying.
     */
    data class Error(
        val message: String,
        val code: String? = null,
        val isRecoverable: Boolean = false
    ) : StreamingEvent()

    /**
     * Connection was established but no content yet.
     * Useful for showing "thinking" indicator.
     */
    data object Connected : StreamingEvent()

    /**
     * A keep-alive or heartbeat event (empty SSE event).
     * These can be ignored but indicate the connection is alive.
     */
    data object KeepAlive : StreamingEvent()

    companion object {
        /**
         * Common finish reasons for Done event.
         */
        object FinishReason {
            const val STOP = "stop"
            const val LENGTH = "length"
            const val CONTENT_FILTER = "content_filter"
            const val TOOL_CALLS = "tool_calls"
            const val FUNCTION_CALL = "function_call"
        }

        /**
         * Common error codes.
         */
        object ErrorCode {
            const val UNAUTHORIZED = "401"
            const val FORBIDDEN = "403"
            const val NOT_FOUND = "404"
            const val RATE_LIMITED = "429"
            const val SERVER_ERROR = "500"
            const val SERVICE_UNAVAILABLE = "503"
            const val CONNECTION_FAILED = "connection_failed"
            const val TIMEOUT = "timeout"
            const val PARSE_ERROR = "parse_error"
            const val CANCELLED = "cancelled"
        }

        /**
         * Create an error event from an HTTP response code.
         */
        fun fromHttpError(code: Int, message: String): Error {
            val isRecoverable = code == 429 || code >= 500
            return Error(
                message = message,
                code = code.toString(),
                isRecoverable = isRecoverable
            )
        }

        /**
         * Create an error event from an exception.
         */
        fun fromException(exception: Throwable): Error {
            val code = when (exception) {
                is java.net.SocketTimeoutException -> ErrorCode.TIMEOUT
                is java.net.UnknownHostException -> ErrorCode.CONNECTION_FAILED
                is java.net.ConnectException -> ErrorCode.CONNECTION_FAILED
                is java.io.IOException -> ErrorCode.CONNECTION_FAILED
                else -> null
            }
            val isRecoverable = code in listOf(
                ErrorCode.TIMEOUT,
                ErrorCode.CONNECTION_FAILED,
                ErrorCode.RATE_LIMITED,
                ErrorCode.SERVER_ERROR,
                ErrorCode.SERVICE_UNAVAILABLE
            )
            return Error(
                message = exception.message ?: "Unknown error",
                code = code,
                isRecoverable = isRecoverable
            )
        }
    }
}
