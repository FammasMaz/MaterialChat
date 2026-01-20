package com.materialchat.ui.screens.conversations

import android.text.format.DateUtils
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialchat.domain.model.Conversation
import com.materialchat.domain.model.Provider
import com.materialchat.domain.usecase.CreateConversationUseCase
import com.materialchat.domain.usecase.GetConversationsUseCase
import com.materialchat.domain.usecase.ManageProvidersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Conversations screen.
 *
 * Manages the list of conversations and handles user interactions.
 */
@HiltViewModel
class ConversationsViewModel @Inject constructor(
    private val getConversationsUseCase: GetConversationsUseCase,
    private val createConversationUseCase: CreateConversationUseCase,
    private val manageProvidersUseCase: ManageProvidersUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<ConversationsUiState>(ConversationsUiState.Loading)
    val uiState: StateFlow<ConversationsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ConversationsEvent>()
    val events: SharedFlow<ConversationsEvent> = _events.asSharedFlow()

    private var pendingDeleteJob: Job? = null
    private var conversationToDelete: Conversation? = null

    init {
        observeConversations()
    }

    /**
     * Observes conversations and provider changes to update the UI state.
     */
    private fun observeConversations() {
        viewModelScope.launch {
            combine(
                getConversationsUseCase.observeConversations(),
                manageProvidersUseCase.observeProviders()
            ) { conversations, providers ->
                val activeProvider = providers.find { it.isActive }
                Triple(conversations, providers, activeProvider)
            }
                .catch { e ->
                    _uiState.value = ConversationsUiState.Error(
                        message = e.message ?: "Failed to load conversations"
                    )
                }
                .collect { (conversations, providers, activeProvider) ->
                    _uiState.value = if (conversations.isEmpty()) {
                        ConversationsUiState.Empty(
                            hasActiveProvider = activeProvider != null
                        )
                    } else {
                        val uiItems = conversations.map { conversation ->
                            conversation.toUiItem(providers)
                        }
                        ConversationsUiState.Success(
                            conversations = uiItems,
                            activeProvider = activeProvider,
                            deletedConversation = conversationToDelete
                        )
                    }
                }
        }
    }

    /**
     * Creates a new conversation and navigates to it.
     */
    fun createNewConversation() {
        viewModelScope.launch {
            try {
                val conversationId = createConversationUseCase()
                _events.emit(ConversationsEvent.NavigateToChat(conversationId))
            } catch (e: IllegalStateException) {
                _events.emit(ConversationsEvent.ShowNoProviderError)
            } catch (e: Exception) {
                _events.emit(
                    ConversationsEvent.ShowSnackbar(
                        message = e.message ?: "Failed to create conversation"
                    )
                )
            }
        }
    }

    /**
     * Opens an existing conversation.
     */
    fun openConversation(conversationId: String) {
        viewModelScope.launch {
            _events.emit(ConversationsEvent.NavigateToChat(conversationId))
        }
    }

    /**
     * Navigates to the settings screen.
     */
    fun navigateToSettings() {
        viewModelScope.launch {
            _events.emit(ConversationsEvent.NavigateToSettings)
        }
    }

    /**
     * Initiates deletion of a conversation with undo capability.
     *
     * The deletion is delayed to allow the user to undo.
     */
    fun deleteConversation(conversation: Conversation) {
        // Cancel any pending delete
        pendingDeleteJob?.cancel()
        conversationToDelete = conversation

        // Update state to remove the conversation visually
        val currentState = _uiState.value
        if (currentState is ConversationsUiState.Success) {
            val updatedConversations = currentState.conversations.filter {
                it.conversation.id != conversation.id
            }
            _uiState.value = if (updatedConversations.isEmpty()) {
                ConversationsUiState.Empty(
                    hasActiveProvider = currentState.activeProvider != null
                )
            } else {
                currentState.copy(
                    conversations = updatedConversations,
                    deletedConversation = conversation
                )
            }
        }

        // Show undo snackbar and schedule actual deletion
        viewModelScope.launch {
            _events.emit(
                ConversationsEvent.ShowSnackbar(
                    message = "Conversation deleted",
                    actionLabel = "Undo",
                    onAction = { undoDelete() }
                )
            )
        }

        pendingDeleteJob = viewModelScope.launch {
            delay(UNDO_DELAY_MS)
            performDelete(conversation)
        }
    }

    /**
     * Undoes a pending conversation deletion.
     */
    fun undoDelete() {
        pendingDeleteJob?.cancel()
        pendingDeleteJob = null

        val deletedConversation = conversationToDelete ?: return
        conversationToDelete = null

        // Refresh the list to restore the conversation
        observeConversations()

        viewModelScope.launch {
            _events.emit(
                ConversationsEvent.ShowSnackbar(message = "Deletion cancelled")
            )
        }
    }

    /**
     * Performs the actual deletion of a conversation.
     */
    private suspend fun performDelete(conversation: Conversation) {
        try {
            getConversationsUseCase.deleteConversation(conversation.id)
            conversationToDelete = null
        } catch (e: Exception) {
            _events.emit(
                ConversationsEvent.ShowSnackbar(
                    message = "Failed to delete conversation: ${e.message}"
                )
            )
            // Refresh to restore the conversation
            observeConversations()
        }
    }

    /**
     * Retries loading conversations after an error.
     */
    fun retry() {
        _uiState.value = ConversationsUiState.Loading
        observeConversations()
    }

    /**
     * Converts a domain Conversation to a UI item with additional display data.
     */
    private fun Conversation.toUiItem(providers: List<Provider>): ConversationUiItem {
        val provider = providers.find { it.id == providerId }
        val relativeTime = formatRelativeTime(updatedAt)

        return ConversationUiItem(
            conversation = this,
            providerName = provider?.name ?: "Unknown Provider",
            relativeTime = relativeTime,
            messagePreview = null // Message preview could be added later if needed
        )
    }

    /**
     * Formats a timestamp to a human-readable relative time string.
     */
    private fun formatRelativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        return DateUtils.getRelativeTimeSpanString(
            timestamp,
            now,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
    }

    companion object {
        private const val UNDO_DELAY_MS = 5000L
    }
}
