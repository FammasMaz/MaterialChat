package com.materialchat.ui.screens.chat.components

import com.materialchat.domain.model.StreamingState
import java.io.IOException

/**
 * Copy for a failed reply in the chat, keyed on the typed error class rather than on the
 * provider's text. One or two sentences, sentence case, each ending with a period, and the
 * last sentence names what the user can do (the assistant's notice rules; the three shared
 * lines are the same strings as `AssistantCopy`, pinned by `ChatErrorCopyTest`).
 *
 * The class comes from [StreamingState.Error.httpStatus] (typed end-to-end since W4) and,
 * for connectivity, from an [IOException] in the cause chain. The provider's own message
 * never reaches the bubble; the snackbar shows it only when the failure has no class.
 */
object ChatErrorCopy {
    /** HTTP 401: the credential was refused. */
    const val UNAUTHORIZED = "The provider rejected the API key. Check it in settings and try again."

    /** HTTP 403: authenticated but not allowed (plan, region, model access). */
    const val FORBIDDEN = "The provider refused this request. Check your access to this model and try again."

    /** HTTP 429 / quota exhaustion: waiting is the right move. */
    const val RATE_LIMITED = "The model is busy right now. Wait a moment and try again."

    /** HTTP 5xx: the provider's side. */
    const val SERVER_ERROR = "The provider is having trouble. Try again in a moment."

    /** Any other 4xx (400 bad request, 404 unknown model, 413 too large, …). */
    const val REJECTED = "The provider could not handle this request. Check the model and try again."

    /** An [IOException] anywhere in the cause chain. */
    const val NO_CONNECTION = "No connection. Check your network and try again."

    /** Everything else, including in-stream provider errors on a 200. */
    const val REQUEST_FAILED = "Something went wrong. Try again."

    /** The retry action label, shared with the assistant's notice action. */
    const val TRY_AGAIN = "Try again"

    /** The line shown in the errored bubble for [error]. */
    fun notice(error: StreamingState.Error): String = notice(error.httpStatus, error.error)

    fun notice(httpStatus: Int?, error: Throwable?): String = when {
        httpStatus == 401 -> UNAUTHORIZED
        httpStatus == 403 -> FORBIDDEN
        httpStatus == 429 -> RATE_LIMITED
        httpStatus != null && httpStatus >= 500 -> SERVER_ERROR
        httpStatus != null && httpStatus >= 400 -> REJECTED
        isConnectivity(error) -> NO_CONNECTION
        else -> REQUEST_FAILED
    }

    /**
     * The snackbar line for [error]. A classified failure (HTTP status or connectivity) shows
     * the same notice as the bubble. An unclassified one keeps the provider's or the use
     * case's own text ("The provider returned an empty response", an in-stream error body),
     * because that text is the only diagnostic the user gets; blank falls back to the generic
     * line.
     */
    fun snackbar(error: StreamingState.Error): String {
        if (error.httpStatus != null || isConnectivity(error.error)) return notice(error)
        return error.error.message?.takeIf { it.isNotBlank() } ?: REQUEST_FAILED
    }

    private fun isConnectivity(error: Throwable?): Boolean {
        var cause = error
        var depth = 0
        while (cause != null && depth < 6) {
            if (cause is IOException) return true
            cause = cause.cause
            depth++
        }
        return false
    }
}
