package com.materialchat.ui.screens.chat

import com.materialchat.domain.model.AiModel
import com.materialchat.domain.model.Attachment
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.ReasoningEffort
import com.materialchat.domain.model.StreamingState

/**
 * UI state for the Chat screen.
 *
 * This sealed interface represents all possible states of the chat interface.
 */
sealed interface ChatUiState {

    /**
     * Loading state while the conversation is being loaded.
     */
    data object Loading : ChatUiState

    /**
     * Success state with conversation data.
     *
     * @property conversationId The ID of the current conversation
     * @property conversationTitle The title of the conversation
     * @property conversationIcon The icon (emoji) of the conversation
     * @property providerName The name of the provider being used
     * @property modelName The name of the current model
     * @property messages The list of messages in the conversation
     * @property inputText The current text in the input field
     * @property pendingAttachments List of image attachments pending to be sent with the next message
     * @property streamingState The current state of streaming response
     * @property availableModels The list of available models from the provider
     * @property isLoadingModels Whether models are currently being loaded
 * @property showExportSheet Whether to show the export bottom sheet
 * @property isExporting Whether an export operation is in progress
 * @property hapticsEnabled Whether haptic feedback is enabled
 * @property reasoningEffort The reasoning effort setting for the next response
 */
    data class Success(
        val conversationId: String,
        val conversationTitle: String,
        val conversationIcon: String? = null,
        val providerName: String,
        val modelName: String,
        val messages: List<MessageUiItem>,
        val inputText: String = "",
        val pendingAttachments: List<Attachment> = emptyList(),
        val streamingState: StreamingState = StreamingState.Idle,
        val availableModels: List<AiModel> = emptyList(),
        val isLoadingModels: Boolean = false,
        val showExportSheet: Boolean = false,
        val isExporting: Boolean = false,
        val hapticsEnabled: Boolean = true,
        val reasoningEffort: ReasoningEffort = ReasoningEffort.HIGH
    ) : ChatUiState {
        /**
         * Whether a message is currently streaming.
         */
        val isStreaming: Boolean
            get() = streamingState is StreamingState.Starting ||
                    streamingState is StreamingState.Streaming

        /**
         * Whether there are pending attachments to be sent.
         */
        val hasAttachments: Boolean
            get() = pendingAttachments.isNotEmpty()

        /**
         * Whether the send button should be enabled.
         * Can send if there's text OR attachments, and not currently streaming.
         */
        val canSend: Boolean
            get() = (inputText.isNotBlank() || hasAttachments) && !isStreaming
    }

    /**
     * Error state when something goes wrong loading the conversation.
     *
     * @property message The error message to display
     */
    data class Error(
        val message: String
    ) : ChatUiState
}

/**
 * UI representation of a message in the chat.
 *
 * @property message The domain message model
 * @property isLastAssistantMessage Whether this is the last message from the assistant
 * @property showActions Whether to show action buttons for this message
 */
data class MessageUiItem(
    val message: Message,
    val isLastAssistantMessage: Boolean = false,
    val showActions: Boolean = false,
    val groupPosition: MessageGroupPosition = MessageGroupPosition.Single
)

/**
 * Indicates where a message sits within a consecutive group from the same sender.
 */
enum class MessageGroupPosition {
    Single,
    First,
    Middle,
    Last
}

/**
 * Events that can occur on the Chat screen.
 * These are one-time events that should be handled and consumed.
 */
sealed interface ChatEvent {
    /**
     * Navigate back to the conversations list.
     */
    data object NavigateBack : ChatEvent

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
    ) : ChatEvent

    /**
     * Message copied to clipboard.
     */
    data object MessageCopied : ChatEvent

    /**
     * Model successfully changed.
     *
     * @property modelName The new model name
     */
    data class ModelChanged(val modelName: String) : ChatEvent

    /**
     * Show export options for the conversation.
     */
    data object ShowExportOptions : ChatEvent

    /**
     * Hide export options bottom sheet.
     */
    data object HideExportOptions : ChatEvent

    /**
     * Share exported content via system share sheet.
     *
     * @property content The exported content string
     * @property filename The suggested filename for the export
     * @property mimeType The MIME type of the content
     */
    data class ShareContent(
        val content: String,
        val filename: String,
        val mimeType: String
    ) : ChatEvent

    /**
     * Scroll to the bottom of the message list.
     */
    data object ScrollToBottom : ChatEvent
}
