package com.materialchat.ui.screens.workflows

/**
 * One-shot notices from [WorkflowsViewModel] that are not part of the list state: a delete that
 * failed after the list was already showing. Rendered by the screen's snackbar host.
 */
sealed interface WorkflowsEvent {
    data class ShowSnackbar(val message: String) : WorkflowsEvent
}
