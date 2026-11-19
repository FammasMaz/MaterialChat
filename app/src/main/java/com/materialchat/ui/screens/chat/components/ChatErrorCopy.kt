package com.materialchat.ui.screens.chat.components

import com.materialchat.domain.model.ProviderHttpException
import com.materialchat.domain.model.StreamingState
import com.materialchat.ui.theme.CopyRules
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
 *
 * Failed chat *actions* (branch, edit, redo, export, bookmark, model load, fusion) go through
 * [action]: a typed HTTP status or a connectivity failure gets the same class line as a failed
 * reply, anything else gets the action's own one-phrase `Could not …` snackbar line. An
 * exception's text never reaches a snackbar or the error card from those paths (W7 chat part
 * 1, pinned by `ChatCopyRulingsTest`).
 */
object ChatErrorCopy {
    /** HTTP 401: the credential was refused. */
    const val UNAUTHORIZED = "The provider rejected the API key. Check it in settings and try again."

    /** HTTP 403: authenticated but not allowed (plan, region, model access). */
    const val FORBIDDEN = "The provider refused this request. Check your access to this model and try again."

    /** HTTP 429 / quota exhaustion: waiting is the right move. Shared class line (`CopyRules`, W8 theme). */
    const val RATE_LIMITED = CopyRules.RATE_LIMITED

    /** HTTP 5xx: the provider's side. */
    const val SERVER_ERROR = "The provider is having trouble. Try again in a moment."

    /** Any other 4xx (400 bad request, 404 unknown model, 413 too large, …). */
    const val REJECTED = "The provider could not handle this request. Check the model and try again."

    /** An [IOException] anywhere in the cause chain. Shared class line (`CopyRules`, W8 theme). */
    const val NO_CONNECTION = CopyRules.NO_CONNECTION

    /** Everything else, including in-stream provider errors on a 200. */
    const val REQUEST_FAILED = CopyRules.REQUEST_FAILED

    /** The retry action label, shared with the assistant's notice action. */
    const val TRY_AGAIN = CopyRules.TRY_AGAIN

    // --- failed actions: one-phrase snackbar lines, `Could not …`, no trailing period ------

    const val COULD_NOT_LOAD_MODELS = "Could not load models"
    const val COULD_NOT_CHANGE_MODEL = "Could not change the model"
    const val COULD_NOT_BRANCH = "Could not create the branch"
    const val COULD_NOT_RESEND_EDIT = "Could not send the edited message"
    const val COULD_NOT_EXPORT = "Could not export the conversation"
    /** The share-chooser failure line is the theme's (`CopyRules.COULD_NOT_SHARE`, W8 theme C5). */
    const val COULD_NOT_SHARE = CopyRules.COULD_NOT_SHARE
    const val COULD_NOT_REDO = "Could not redo with that model"
    const val COULD_NOT_UPDATE_BOOKMARK = "Could not update the bookmark"
    const val COULD_NOT_RUN_FUSION = "Could not complete the fusion"

    /**
     * The full-screen error card's supporting line when the conversation or its messages
     * cannot be read (notice rules: full stops, the last sentence names the action).
     */
    const val COULD_NOT_LOAD_CONVERSATION = "Could not load this conversation. Try again to reload it."

    /** [COULD_NOT_LOAD_MODELS], naming the provider when one is known. */
    fun couldNotLoadModels(providerName: String?): String =
        if (providerName.isNullOrBlank()) COULD_NOT_LOAD_MODELS else "Could not load models for $providerName"

    /**
     * The snackbar line for a failed action: a [ProviderHttpException] anywhere in the cause
     * chain classifies like a failed reply, a connectivity failure is [NO_CONNECTION], and
     * everything else is the action's own [fallback] line. The exception's text is never used.
     */
    fun action(error: Throwable?, fallback: String): String {
        val status = httpStatusOf(error)
        return when {
            status != null -> notice(status, error)
            isConnectivity(error) -> NO_CONNECTION
            else -> fallback
        }
    }

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

    private fun isConnectivity(error: Throwable?): Boolean = causeChain(error).any { it is IOException }

    private fun httpStatusOf(error: Throwable?): Int? =
        causeChain(error).firstNotNullOfOrNull { (it as? ProviderHttpException)?.httpStatus }

    private fun causeChain(error: Throwable?): Sequence<Throwable> =
        generateSequence(error) { it.cause }.take(6)
}
