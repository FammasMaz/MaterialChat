package com.materialchat.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Why a list screen (memories, workflows, generated images, leaderboard) could not load.
 * Typed rather than a message string so the screen decides the copy and the view-model test
 * can assert the exact state; rendered through [LoadErrorContent].
 */
sealed interface LoadError {
    /** The repository threw while the list was being observed; nothing can be shown. */
    data object Unavailable : LoadError
}

/** Action verb for every error surface on home, settings and the drawer screens. */
const val ERROR_RETRY_LABEL = ErrorContentCopy.TRY_AGAIN

/** Secondary verb on error surfaces that were pushed and can be left. */
const val ERROR_BACK_LABEL = "Go back"

/**
 * The one full-screen error surface for home, settings and every drawer screen: a thin alias
 * over the shared [ErrorContent] card (error tile, bold headline, supporting line) that fixes
 * the action order — [onRetry] ([ERROR_RETRY_LABEL]) first as the primary button, then
 * [onBack] ([ERROR_BACK_LABEL]) as a low-emphasis text button. The copy is fixed per screen;
 * exception text is never passed through. Fills the space it is given and centres the card.
 */
@Composable
fun LoadErrorContent(
    title: String,
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null
) {
    ErrorContent(
        title = title,
        message = message,
        onRetry = onRetry,
        modifier = modifier.fillMaxSize(),
        secondaryLabel = onBack?.let { ERROR_BACK_LABEL },
        onSecondary = onBack
    )
}
