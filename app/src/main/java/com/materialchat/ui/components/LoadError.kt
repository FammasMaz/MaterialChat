package com.materialchat.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.materialchat.ui.screens.conversations.EmptyStateAccent
import com.materialchat.ui.screens.conversations.EmptyStateAction
import com.materialchat.ui.screens.conversations.HomeEmptyState

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
const val ERROR_RETRY_LABEL = "Try again"

/** Secondary verb on error surfaces that were pushed and can be left. */
const val ERROR_BACK_LABEL = "Go back"

/**
 * The one full-screen error surface for home, settings and every drawer screen: the same
 * empty-state cluster as the home list (icon, sentence-case headline, one supporting line)
 * with a fixed action order — [onRetry] ("Try again") first as the primary button, then
 * [onBack] ("Go back") as a low-emphasis text button. Either may be omitted. The copy is
 * fixed per screen; exception text is never passed through.
 */
@Composable
fun LoadErrorContent(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null
) {
    HomeEmptyState(
        headline = title,
        supportingText = message,
        icon = Icons.Outlined.ErrorOutline,
        accent = EmptyStateAccent.Secondary,
        action = onRetry?.let { EmptyStateAction(ERROR_RETRY_LABEL, it) },
        secondaryAction = onBack?.let { EmptyStateAction(ERROR_BACK_LABEL, it) },
        modifier = modifier
    )
}
