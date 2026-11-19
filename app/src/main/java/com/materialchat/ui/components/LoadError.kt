package com.materialchat.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.materialchat.ui.screens.conversations.EmptyStateAccent

/**
 * Why a list screen (memories, workflows, generated images, leaderboard) could not load.
 * Typed rather than a message string so the screen decides the copy and the view-model test
 * can assert the exact state; rendered through [LoadErrorContent].
 */
sealed interface LoadError {
    /** The repository threw while the list was being observed; nothing can be shown. */
    data object Unavailable : LoadError
}

/**
 * The shared error surface for a screen whose list failed to load: the same empty-state
 * cluster as the home list (icon, sentence-case headline, one supporting line) with a single
 * retry action. [onRetry] must put the screen back into its loading state.
 */
@Composable
fun LoadErrorContent(
    title: String,
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    ExpressiveEmptyState(
        icon = Icons.Outlined.ErrorOutline,
        title = title,
        message = message,
        modifier = modifier,
        actionText = "Try again",
        onAction = onRetry,
        accent = EmptyStateAccent.Secondary
    )
}
