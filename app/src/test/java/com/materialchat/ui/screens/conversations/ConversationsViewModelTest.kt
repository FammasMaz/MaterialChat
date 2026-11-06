package com.materialchat.ui.screens.conversations

import android.text.format.DateUtils
import app.cash.turbine.test
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.domain.model.Conversation
import com.materialchat.domain.model.Provider
import com.materialchat.domain.repository.ConversationRepository
import com.materialchat.domain.usecase.CreateConversationUseCase
import com.materialchat.domain.usecase.GenerateConversationTitleUseCase
import com.materialchat.domain.usecase.GetConversationsUseCase
import com.materialchat.domain.usecase.ManageProvidersUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Regression coverage for the delete/undo contract on the conversation list.
 *
 * A conversation is hidden while its deletion is pending so the snackbar can
 * offer Undo. Undo must put the row back on screen immediately; previously the
 * id was dropped from the pending set without re-emitting state, so the row
 * stayed invisible until an unrelated database or preference change arrived.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConversationsViewModelTest {

    private val conversations = MutableStateFlow<List<Conversation>>(emptyList())
    private val archived = MutableStateFlow<List<Conversation>>(emptyList())
    private val streamingIds = MutableStateFlow<Set<String>>(emptySet())
    private val providers = MutableStateFlow<List<Provider>>(emptyList())

    private val repository = mockk<ConversationRepository>(relaxed = true)
    private val getConversations = mockk<GetConversationsUseCase>(relaxed = true)
    private val createConversation = mockk<CreateConversationUseCase>(relaxed = true)
    private val manageProviders = mockk<ManageProvidersUseCase>(relaxed = true)
    private val preferences = mockk<AppPreferences>(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(DateUtils::class)
        every {
            DateUtils.getRelativeTimeSpanString(any(), any(), any(), any())
        } returns "now"
        every { getConversations.observeConversations() } returns conversations
        every { repository.observeArchivedConversations() } returns archived
        every { repository.observeStreamingConversationIds() } returns streamingIds
        every { manageProviders.observeProviders() } returns providers
        every { preferences.hapticsEnabled } returns flowOf(true)
        every { preferences.listHapticsEnabled } returns flowOf(true)
        coEvery { getConversations.deleteConversation(any()) } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(DateUtils::class)
    }

    @Test
    fun `pending deletion hides the conversation and undo restores it`() = runTest {
        val viewModel = createViewModel()
        val conversation = conversation("keep-me")
        conversations.value = listOf(conversation)
        runCurrent()
        assertEquals(listOf(conversation.id), visible(viewModel))

        viewModel.deleteConversation(conversation)
        runCurrent()
        assertEquals(emptyList<String>(), visible(viewModel))

        viewModel.undoDelete(conversation.id)
        runCurrent()

        assertEquals(listOf(conversation.id), visible(viewModel))
        val state = viewModel.uiState.value as ConversationsUiState.Success
        assertNull("Undo must not leave the deleted row in state", state.deletedConversation)
    }

    @Test
    fun `undo prevents the delayed delete from removing the conversation`() = runTest {
        val viewModel = createViewModel()
        val conversation = conversation("survivor")
        conversations.value = listOf(conversation)
        runCurrent()

        viewModel.deleteConversation(conversation)
        runCurrent()
        viewModel.undoDelete(conversation.id)
        advanceUntilIdle()

        coVerify(exactly = 0) { getConversations.deleteConversation(conversation.id) }
        assertEquals(listOf(conversation.id), visible(viewModel))
    }

    @Test
    fun `deletion without undo commits after the delay`() = runTest {
        val viewModel = createViewModel()
        val conversation = conversation("gone")
        conversations.value = listOf(conversation)
        runCurrent()

        viewModel.deleteConversation(conversation)
        advanceTimeBy(UNDO_DELAY_MS + 1)
        advanceUntilIdle()

        coVerify(exactly = 1) { getConversations.deleteConversation(conversation.id) }
        // The repository mock does not drop the row, so only the pending-hide
        // tracking is asserted here; visibility is covered by the undo tests.
        assertTrue(viewModel.uiState.value is ConversationsUiState.Success)
    }

    @Test
    fun `undo snackbar action restores the conversation`() = runTest {
        val viewModel = createViewModel()
        val conversation = conversation("snackbar")
        conversations.value = listOf(conversation)
        runCurrent()

        viewModel.events.test {
            viewModel.deleteConversation(conversation)
            runCurrent()
            val shown = awaitItem() as ConversationsEvent.ShowSnackbar
            shown.onAction?.invoke()
            runCurrent()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(listOf(conversation.id), visible(viewModel))
    }

    @Test
    fun `immediate main dispatcher loads on construction without retry`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        conversations.value = listOf(conversation("startup"))
        val viewModel = ConversationsViewModel(
            getConversationsUseCase = getConversations,
            createConversationUseCase = createConversation,
            manageProvidersUseCase = manageProviders,
            conversationRepository = repository,
            appPreferences = preferences,
            generateConversationTitleUseCase = mockk(relaxed = true)
        )
        runCurrent()
        assertEquals(listOf("startup"), visible(viewModel))
    }

    /** Built inside the test body so the ViewModel shares the test scheduler. */
    private fun TestScope.createViewModel(): ConversationsViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        return ConversationsViewModel(
            getConversationsUseCase = getConversations,
            createConversationUseCase = createConversation,
            manageProvidersUseCase = manageProviders,
            conversationRepository = repository,
            appPreferences = preferences,
            generateConversationTitleUseCase = mockk<GenerateConversationTitleUseCase>(relaxed = true)
        )
    }

    private fun visible(viewModel: ConversationsViewModel): List<String> =
        when (val state = viewModel.uiState.value) {
            is ConversationsUiState.Success -> state.conversations.map { it.conversation.id }
            is ConversationsUiState.Empty -> emptyList()
            else -> error("Unexpected state $state")
        }

    private fun conversation(id: String) = Conversation(
        id = id,
        title = id,
        providerId = "provider",
        modelName = "model",
        createdAt = 1L,
        updatedAt = 1L
    )

    private companion object {
        const val UNDO_DELAY_MS = 5_000L
    }
}
