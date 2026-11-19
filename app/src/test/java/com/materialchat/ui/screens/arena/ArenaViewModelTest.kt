package com.materialchat.ui.screens.arena

import com.materialchat.domain.model.AiModel
import com.materialchat.domain.model.InsightsTimeRange
import com.materialchat.domain.model.Provider
import com.materialchat.domain.model.ProviderType
import com.materialchat.domain.model.StreamingState
import com.materialchat.domain.usecase.GetArenaLeaderboardUseCase
import com.materialchat.domain.usecase.GetConversationInsightsUseCase
import com.materialchat.domain.usecase.ManageProvidersUseCase
import com.materialchat.domain.usecase.RunArenaBattleUseCase
import com.materialchat.domain.usecase.VoteArenaBattleUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Arena start-up transitions: no providers is an empty state, a failing provider store is a
 * fixed-copy error that [ArenaViewModel.retry] recovers from, and a failed model fetch is never
 * cached as "this provider has no models".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArenaViewModelTest {

    private val providers = mockk<ManageProvidersUseCase>()
    private val battle = mockk<RunArenaBattleUseCase>()
    private val vote = mockk<VoteArenaBattleUseCase>()
    private val leaderboard = mockk<GetArenaLeaderboardUseCase>()
    private val insights = mockk<GetConversationInsightsUseCase>()

    private val provider = Provider(
        id = "p1",
        name = "Local",
        type = ProviderType.OPENAI_COMPATIBLE,
        baseUrl = "http://localhost",
        defaultModel = "m",
        requiresApiKey = false
    )

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(dispatcher: TestDispatcher) = ArenaViewModel(
        manageProvidersUseCase = providers,
        runArenaBattleUseCase = battle,
        voteArenaBattleUseCase = vote,
        getArenaLeaderboardUseCase = leaderboard,
        getConversationInsightsUseCase = insights,
        ioDispatcher = dispatcher
    )

    @Test
    fun `no configured providers is the empty state, not an error`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        coEvery { providers.getProviders() } returns emptyList()

        val viewModel = viewModel(dispatcher)
        runCurrent()

        assertEquals(ArenaUiState.NoProviders, viewModel.uiState.value)
    }

    @Test
    fun `a throwing provider store is a fixed-copy error and retry recovers`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        var attempts = 0
        coEvery { providers.getProviders() } answers {
            attempts++
            if (attempts == 1) throw IOException("database closed") else listOf(provider)
        }
        coEvery { providers.getActiveProvider() } returns null
        coEvery { insights(InsightsTimeRange.ALL_TIME) } throws IllegalStateException("no dao")
        coEvery { providers.fetchModels("p1") } returns Result.success(listOf(AiModel(id = "m", providerId = "p1")))

        val viewModel = viewModel(dispatcher)
        runCurrent()

        val failed = viewModel.uiState.value
        assertEquals(ArenaUiState.Error(ArenaViewModel.PROVIDERS_UNAVAILABLE), failed)
        assertTrue("database closed" !in (failed as ArenaUiState.Error).message)

        viewModel.retry()
        assertEquals(ArenaUiState.Loading, viewModel.uiState.value)
        runCurrent()

        val ready = viewModel.uiState.value
        assertTrue(ready is ArenaUiState.Ready)
        ready as ArenaUiState.Ready
        assertEquals("p1", ready.pickerProviderId)
        assertEquals(listOf("m"), ready.pickerModels.map { it.id })
        assertFalse(ready.isLoadingModels)
        assertEquals(2, attempts)
    }

    @Test
    fun `a failed model fetch is not cached and re-selecting the provider fetches again`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        coEvery { providers.getProviders() } returns listOf(provider)
        coEvery { providers.getActiveProvider() } returns provider
        coEvery { insights(InsightsTimeRange.ALL_TIME) } throws IllegalStateException("no dao")
        var fetches = 0
        coEvery { providers.fetchModels("p1") } answers {
            fetches++
            if (fetches == 1) Result.failure(IOException("timeout"))
            else Result.success(listOf(AiModel(id = "m", providerId = "p1")))
        }

        val viewModel = viewModel(dispatcher)
        val events = mutableListOf<ArenaEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }
        runCurrent()

        val afterFailure = viewModel.uiState.value as ArenaUiState.Ready
        assertFalse("a failed fetch must not be cached", afterFailure.availableModels.containsKey("p1"))
        assertFalse(afterFailure.isLoadingModels)
        assertEquals(listOf(ArenaEvent.ShowSnackbar(ArenaViewModel.MODELS_UNAVAILABLE)), events)

        viewModel.setPickerProvider("p1")
        runCurrent()

        val recovered = viewModel.uiState.value as ArenaUiState.Ready
        assertEquals(listOf("m"), recovered.pickerModels.map { it.id })
        assertEquals(2, fetches)
    }

    @Test
    fun `a contender that failed without a partial answer shows fixed copy`() {
        val failed = ContenderUi(
            slot = 0,
            providerId = "p1",
            modelName = "m",
            streamState = StreamingState.Error(error = IOException("socket reset"))
        )
        assertEquals(ContenderUi.FAILED_ANSWER, failed.content)
        assertTrue("socket reset" !in failed.content)

        val partial = failed.copy(
            streamState = StreamingState.Error(error = IOException("socket reset"), partialContent = "Half an answer")
        )
        assertEquals("Half an answer", partial.content)
    }
}
