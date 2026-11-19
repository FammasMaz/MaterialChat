package com.materialchat.ui.screens.personas

import com.materialchat.domain.model.Persona
import com.materialchat.domain.usecase.ManagePersonasUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The Persona Studio error state must be recoverable from the shared error card's "Try again"
 * action: [PersonaStudioViewModel.retry] returns to loading and re-observes the store.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonaStudioViewModelTest {

    private val useCase = mockk<ManagePersonasUseCase>()

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a failing observation reaches the error state and a successful retry recovers`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val persona = Persona(name = "Tutor", systemPrompt = "Explain patiently")
        var attempts = 0
        every { useCase.observeAllPersonas() } answers {
            attempts++
            if (attempts == 1) flow { throw IOException("database closed") } else flowOf(listOf(persona))
        }

        val viewModel = PersonaStudioViewModel(useCase)
        runCurrent()
        assertTrue(viewModel.uiState.value is PersonaStudioUiState.Error)

        viewModel.retry()
        assertEquals("Retry must show loading first", PersonaStudioUiState.Loading, viewModel.uiState.value)
        runCurrent()

        val recovered = viewModel.uiState.value
        assertTrue(recovered is PersonaStudioUiState.Success)
        assertEquals(listOf(persona), (recovered as PersonaStudioUiState.Success).personas)
        assertEquals(2, attempts)
    }
}
