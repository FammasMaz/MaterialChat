package com.materialchat.ui.screens.conversations

import com.materialchat.domain.model.Conversation
import com.materialchat.domain.model.Provider

/**
 * UI state for the Conversations screen.
 *
 * This sealed interface represents all possible states of the conversations list.
 */
sealed interface ConversationsUiState {

    /**
     * Loading state while conversations are being fetched.
     */
    data object Loading : ConversationsUiState

    /**
     * Empty state when there are no conversations.
     *
     * @property hasActiveProvider Whether an active provider is configured
     * @property hapticsEnabled Whether haptic feedback is enabled
     */
    data class Empty(
        val hasActiveProvider: Boolean,
        val hapticsEnabled: Boolean = true
    ) : ConversationsUiState

    /**
     * Success state with a list of conversations.
     *
     * @property conversations The list of conversations to display
     * @property activeProvider The currently active provider, if any
     * @property deletedConversation Temporarily holds a deleted conversation for undo functionality
     * @property hapticsEnabled Whether haptic feedback is enabled
     */
    data class Success(
        val conversations: List<ConversationUiItem>,
        val activeProvider: Provider? = null,
        val deletedConversation: Conversation? = null,
        val hapticsEnabled: Boolean = true
    ) : ConversationsUiState

    /**
     * Error state when something goes wrong.
     *
     * @property message The error message to display
     */
    data class Error(
        val message: String
    ) : ConversationsUiState
}

/**
 * UI representation of a conversation item in the list.
 *
 * @property conversation The domain conversation model
 * @property providerName The name of the provider used (or "Unknown" if provider was deleted)
 * @property relativeTime Human-readable relative time string (e.g., "2 hours ago")
 * @property messagePreview Preview of the last message, if any
 */
data class ConversationUiItem(
    val conversation: Conversation,
    val providerName: String,
    val relativeTime: String,
    val messagePreview: String? = null
)

/**
 * Events that can occur on the Conversations screen.
 * These are one-time events that should be handled and consumed.
 */
sealed interface ConversationsEvent {
    /**
     * Navigate to a chat screen.
     *
     * @property conversationId The ID of the conversation to open
     */
    data class NavigateToChat(val conversationId: String) : ConversationsEvent

    /**
     * Navigate to settings screen.
     */
    data object NavigateToSettings : ConversationsEvent

    /**
     * Show a snackbar message.
     *
     * @property message The message to display
     * @property actionLabel Optional action button label
     * @property onAction Optional action to perform when button is clicked
     */
    data class ShowSnackbar(
        val message: String,
        val actionLabel: String? = null,
        val onAction: (() -> Unit)? = null
    ) : ConversationsEvent

    /**
     * Show error creating conversation due to missing provider.
     */
    data object ShowNoProviderError : ConversationsEvent
}
