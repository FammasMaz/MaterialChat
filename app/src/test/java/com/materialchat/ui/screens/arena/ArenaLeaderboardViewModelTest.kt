package com.materialchat.ui.screens.arena

import com.materialchat.domain.model.ModelRating
import com.materialchat.domain.usecase.GetArenaLeaderboardUseCase
import com.materialchat.ui.components.LoadError
import com.materialchat.ui.screens.arena.components.ArenaLeaderboardViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * A failing ratings store must reach [ArenaLeaderboardViewModel]'s error state instead of
 * showing "no battles yet", and a retry that succeeds must clear it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArenaLeaderboardViewModelTest {

    private val useCase = mockk<GetArenaLeaderboardUseCase>()

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a failing observation reaches the error state and a successful retry clears it`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val rating = ModelRating(modelName = "gpt", wins = 2, totalBattles = 2)
        var attempts = 0
        every { useCase.observeRatings() } answers {
            attempts++
            if (attempts == 1) flow { throw IOException("database closed") } else flowOf(listOf(rating))
        }

        val viewModel = ArenaLeaderboardViewModel(useCase)
        assertTrue("Initial state is loading, not empty", viewModel.uiState.value.isLoading)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        val failed = viewModel.uiState.value
        assertEquals(LoadError.Unavailable, failed.error)
        assertFalse(failed.isLoading)
        assertTrue(failed.ratings.isEmpty())

        viewModel.retry()
        runCurrent()

        val recovered = viewModel.uiState.value
        assertNull("Retry must clear the error", recovered.error)
        assertFalse(recovered.isLoading)
        assertEquals(listOf(rating), recovered.ratings)
        assertEquals(2, attempts)
    }

    @Test
    fun `a use case that throws on subscription reaches the error state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        every { useCase.observeRatings() } throws IllegalStateException("not initialised")

        val viewModel = ArenaLeaderboardViewModel(useCase)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        assertEquals(LoadError.Unavailable, viewModel.uiState.value.error)
    }
}
