package com.materialchat.domain.model

/**
 * Represents the state of a streaming response from an AI provider.
 */
sealed class StreamingState {
    /**
     * Initial idle state before streaming starts.
     */
    data object Idle : StreamingState()

    /**
     * Streaming has started, waiting for first token.
     */
    data object Starting : StreamingState()

    /**
     * Actively receiving streamed content.
     *
     * @property content The accumulated content received so far
     * @property thinkingContent The accumulated thinking/reasoning content (for models that support it)
     * @property messageId The ID of the message being streamed
     */
    data class Streaming(
        val content: String,
        val thinkingContent: String? = null,
        val messageId: String
    ) : StreamingState()

    /**
     * Streaming completed successfully.
     *
     * @property finalContent The complete content of the response
     * @property finalThinkingContent The complete thinking/reasoning content (for models that support it)
     * @property messageId The ID of the completed message
     */
    data class Completed(
        val finalContent: String,
        val finalThinkingContent: String? = null,
        val messageId: String
    ) : StreamingState()

    /**
     * Streaming failed with an error.
     *
     * @property error The error that occurred
     * @property partialContent Any content that was received before the error
     * @property messageId The ID of the message that failed, if available
     * @property httpStatus The provider's HTTP status when the failure was an HTTP response
     *   (401, 403, 429, 5xx, …), null for connectivity, parse and in-stream errors. Defaults
     *   to the status carried by a [ProviderHttpException] so a re-wrap that only copies
     *   [error] keeps it.
     */
    data class Error(
        val error: Throwable,
        val partialContent: String? = null,
        val messageId: String? = null,
        val httpStatus: Int? = (error as? ProviderHttpException)?.httpStatus
    ) : StreamingState()

    /**
     * Streaming was cancelled by the user.
     *
     * @property partialContent Any content that was received before cancellation
     * @property messageId The ID of the cancelled message, if available
     */
    data class Cancelled(
        val partialContent: String? = null,
        val messageId: String? = null
    ) : StreamingState()
}

/**
 * A provider answered the request with a non-2xx HTTP status. [message] is the parsed error
 * body exactly as the API client produced it; [httpStatus] is the typed status so consumers
 * never have to recognise "429" from the text. Deliberately not an [java.io.IOException]: an
 * HTTP refusal is not a connectivity failure.
 */
class ProviderHttpException(
    val httpStatus: Int,
    message: String
) : Exception(message)
