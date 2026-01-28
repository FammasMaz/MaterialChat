package com.materialchat.ui.screens.conversations

import android.text.format.DateUtils
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.domain.model.Conversation
import com.materialchat.domain.model.Provider
import com.materialchat.domain.repository.ConversationRepository
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
 * Supports branch grouping where branches are displayed under their parent conversations.
 */
@HiltViewModel
class ConversationsViewModel @Inject constructor(
    private val getConversationsUseCase: GetConversationsUseCase,
    private val createConversationUseCase: CreateConversationUseCase,
    private val manageProvidersUseCase: ManageProvidersUseCase,
    private val conversationRepository: ConversationRepository,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow<ConversationsUiState>(ConversationsUiState.Loading)
    val uiState: StateFlow<ConversationsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ConversationsEvent>()
    val events: SharedFlow<ConversationsEvent> = _events.asSharedFlow()

    // Track expanded conversation groups by parent ID
    private val _expandedGroupIds = MutableStateFlow<Set<String>>(emptySet())

    // Track multiple pending deletions: conversation ID -> delete job
    private val pendingDeletions = mutableMapOf<String, Job>()
    // Set of conversation IDs pending deletion (for UI filtering)
    private val pendingDeleteIds = mutableSetOf<String>()
    // Most recent deletion for undo functionality
    private var lastDeletedConversation: Conversation? = null

    init {
        observeConversations()
    }

    /**
     * Observes conversations and provider changes to update the UI state.
     * Groups root conversations with their branches.
     */
    private fun observeConversations() {
        viewModelScope.launch {
            combine(
                getConversationsUseCase.observeConversations(),
                manageProvidersUseCase.observeProviders(),
                appPreferences.hapticsEnabled,
                _expandedGroupIds
            ) { conversations, providers, hapticsEnabled, expandedIds ->
                val activeProvider = providers.find { it.isActive }
                ConversationsData(conversations, providers, activeProvider, hapticsEnabled, expandedIds)
            }
                .catch { e ->
                    _uiState.value = ConversationsUiState.Error(
                        message = e.message ?: "Failed to load conversations"
                    )
                }
                .collect { data ->
                    // Filter out all conversations pending deletion
                    val filteredConversations = if (pendingDeleteIds.isNotEmpty()) {
                        data.conversations.filter { it.id !in pendingDeleteIds }
                    } else {
                        data.conversations
                    }

                    // Separate root conversations and branches
                    val (branches, roots) = filteredConversations.partition { it.isBranch }

                    // Build a map of parent ID to branches
                    val branchMap = branches.groupBy { it.parentId!! }

                    // Create grouped structure
                    val groups = roots.map { root ->
                        val rootUiItem = root.toUiItem(data.providers)
                        val branchUiItems = branchMap[root.id]?.map { it.toUiItem(data.providers) } ?: emptyList()

                        ConversationGroupUiItem(
                            parent = rootUiItem,
                            branches = branchUiItems,
                            isExpanded = root.id in data.expandedIds
                        )
                    }

                    // Also create flat list for compatibility
                    val flatList = roots.map { it.toUiItem(data.providers) }

                    _uiState.value = if (groups.isEmpty()) {
                        ConversationsUiState.Empty(
                            hasActiveProvider = data.activeProvider != null,
                            hapticsEnabled = data.hapticsEnabled
                        )
                    } else {
                        ConversationsUiState.Success(
                            conversations = flatList,
                            conversationGroups = groups,
                            activeProvider = data.activeProvider,
                            deletedConversation = lastDeletedConversation,
                            hapticsEnabled = data.hapticsEnabled
                        )
                    }
                }
        }
    }

    /**
     * Internal data class for combining conversation flows.
     */
    private data class ConversationsData(
        val conversations: List<Conversation>,
        val providers: List<Provider>,
        val activeProvider: Provider?,
        val hapticsEnabled: Boolean,
        val expandedIds: Set<String>
    )

    /**
     * Toggles the expanded state of a conversation group.
     *
     * @param parentId The ID of the parent conversation
     */
    fun toggleGroupExpanded(parentId: String) {
        val currentExpanded = _expandedGroupIds.value
        _expandedGroupIds.value = if (parentId in currentExpanded) {
            currentExpanded - parentId
        } else {
            currentExpanded + parentId
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
     * Supports multiple concurrent deletions - each gets its own timer.
     * Only the most recent deletion can be undone via the snackbar.
     */
    fun deleteConversation(conversation: Conversation) {
        val conversationId = conversation.id

        // Cancel any existing deletion job for this specific conversation (in case of re-delete)
        pendingDeletions[conversationId]?.cancel()

        // Track this conversation as pending deletion
        pendingDeleteIds.add(conversationId)
        lastDeletedConversation = conversation

        // Update state to remove the conversation visually
        val currentState = _uiState.value
        if (currentState is ConversationsUiState.Success) {
            val updatedGroups = currentState.conversationGroups.filter {
                it.parent.conversation.id !in pendingDeleteIds
            }.map { group ->
                group.copy(
                    branches = group.branches.filter { it.conversation.id !in pendingDeleteIds }
                )
            }
            val updatedConversations = currentState.conversations.filter {
                it.conversation.id !in pendingDeleteIds
            }
            _uiState.value = if (updatedGroups.isEmpty()) {
                ConversationsUiState.Empty(
                    hasActiveProvider = currentState.activeProvider != null,
                    hapticsEnabled = currentState.hapticsEnabled
                )
            } else {
                currentState.copy(
                    conversations = updatedConversations,
                    conversationGroups = updatedGroups,
                    deletedConversation = conversation
                )
            }
        }

        // Show undo snackbar
        viewModelScope.launch {
            _events.emit(
                ConversationsEvent.ShowSnackbar(
                    message = "Conversation deleted",
                    actionLabel = "Undo",
                    onAction = { undoDelete(conversationId) }
                )
            )
        }

        // Schedule actual deletion with its own job
        val deleteJob = viewModelScope.launch {
            delay(UNDO_DELAY_MS)
            performDelete(conversationId)
        }
        pendingDeletions[conversationId] = deleteJob
    }

    /**
     * Undoes a pending conversation deletion.
     *
     * @param conversationId The ID of the conversation to restore
     */
    fun undoDelete(conversationId: String) {
        // Cancel the deletion job for this conversation
        pendingDeletions[conversationId]?.cancel()
        pendingDeletions.remove(conversationId)
        pendingDeleteIds.remove(conversationId)

        // Clear last deleted if it matches
        if (lastDeletedConversation?.id == conversationId) {
            lastDeletedConversation = null
        }

        // Trigger UI refresh to restore the conversation
        val currentState = _uiState.value
        if (currentState is ConversationsUiState.Success || currentState is ConversationsUiState.Empty) {
            // Force a re-collection by updating state - the Flow will re-emit
            viewModelScope.launch {
                _events.emit(
                    ConversationsEvent.ShowSnackbar(message = "Deletion cancelled")
                )
            }
        }
    }

    /**
     * Undoes the most recent pending deletion (legacy support).
     */
    fun undoDelete() {
        lastDeletedConversation?.let { undoDelete(it.id) }
    }

    /**
     * Performs the actual deletion of a conversation.
     *
     * @param conversationId The ID of the conversation to delete
     */
    private suspend fun performDelete(conversationId: String) {
        try {
            getConversationsUseCase.deleteConversation(conversationId)
        } catch (e: Exception) {
            _events.emit(
                ConversationsEvent.ShowSnackbar(
                    message = "Failed to delete conversation: ${e.message}"
                )
            )
        } finally {
            // Clean up tracking regardless of success/failure
            pendingDeletions.remove(conversationId)
            pendingDeleteIds.remove(conversationId)
            if (lastDeletedConversation?.id == conversationId) {
                lastDeletedConversation = null
            }
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
