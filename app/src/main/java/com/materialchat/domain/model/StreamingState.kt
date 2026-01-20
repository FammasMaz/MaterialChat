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
     * @property messageId The ID of the message being streamed
     */
    data class Streaming(
        val content: String,
        val messageId: String
    ) : StreamingState()

    /**
     * Streaming completed successfully.
     *
     * @property finalContent The complete content of the response
     * @property messageId The ID of the completed message
     */
    data class Completed(
        val finalContent: String,
        val messageId: String
    ) : StreamingState()

    /**
     * Streaming failed with an error.
     *
     * @property error The error that occurred
     * @property partialContent Any content that was received before the error
     * @property messageId The ID of the message that failed, if available
     */
    data class Error(
        val error: Throwable,
        val partialContent: String? = null,
        val messageId: String? = null
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
