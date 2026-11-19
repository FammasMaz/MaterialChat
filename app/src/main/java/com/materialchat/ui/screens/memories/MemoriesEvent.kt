package com.materialchat.ui.screens.memories

/**
 * One-shot notices from [MemoriesViewModel] that are not part of the list state: a delete that
 * failed after the list was already showing. Rendered by the screen's snackbar host.
 */
sealed interface MemoriesEvent {
    data class ShowSnackbar(val message: String) : MemoriesEvent
}
