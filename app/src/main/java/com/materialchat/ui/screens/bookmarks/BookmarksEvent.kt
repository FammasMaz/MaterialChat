package com.materialchat.ui.screens.bookmarks

/**
 * One-shot notices from [BookmarksViewModel] that are not part of the list state: a delete or
 * update that failed after the list was already showing. Rendered by the screen's snackbar host.
 */
sealed interface BookmarksEvent {
    data class ShowSnackbar(val message: String) : BookmarksEvent
}
