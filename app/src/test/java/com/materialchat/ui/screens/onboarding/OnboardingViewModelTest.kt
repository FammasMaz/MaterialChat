package com.materialchat.ui.screens.onboarding

import com.materialchat.data.auth.NativeAuthManager
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.domain.model.ProviderType
import com.materialchat.domain.usecase.ManageProvidersUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * Onboarding sign-in transitions: a failed OAuth flow lands on the fixed-copy error card (never
 * the exception text) with the sign-in button re-enabled, and skipping completes onboarding.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val auth = mockk<NativeAuthManager>()
    private val providers = mockk<ManageProvidersUseCase>()
    private val preferences = mockk<AppPreferences>()

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a failed sign-in shows fixed copy and re-enables the button`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        coEvery { auth.authenticate(ProviderType.CODEX_NATIVE, any()) } throws IOException("HTTP 401 invalid_grant")

        val viewModel = OnboardingViewModel(auth, providers, preferences)
        viewModel.signInWithCodex()
        assertTrue(viewModel.uiState.value.isSigningIn)
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isSigningIn)
        assertEquals(OnboardingViewModel.SIGN_IN_FAILED, state.error)
        assertTrue("exception text must not reach the card", "401" !in state.error.orEmpty())
        assertNull(state.status)
    }

    @Test
    fun `skipping completes onboarding and resets the state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        coEvery { preferences.setOnboardingComplete(true) } returns Unit

        val viewModel = OnboardingViewModel(auth, providers, preferences)
        val events = mutableListOf<OnboardingEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }
        runCurrent()

        viewModel.skipForNow()
        runCurrent()

        assertEquals(listOf<OnboardingEvent>(OnboardingEvent.Completed), events)
        assertEquals(OnboardingUiState(), viewModel.uiState.value)
    }
}
