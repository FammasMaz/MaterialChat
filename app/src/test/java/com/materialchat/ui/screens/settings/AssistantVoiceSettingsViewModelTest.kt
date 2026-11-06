package com.materialchat.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModelStore
import com.materialchat.assistant.voice.*
import com.materialchat.data.local.preferences.*
import com.materialchat.domain.repository.ProviderRepository
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantVoiceSettingsViewModelTest {
    private val store = ViewModelStore()
    private val preferences = mockk<AppPreferences>(relaxed = true)
    private val credentials = mockk<EncryptedPreferences>(relaxed = true)
    private val providers = mockk<ProviderRepository>(relaxed = true)

    private fun TestScope.create(): AssistantVoiceSettingsViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = mockk<Context>()
        every { context.applicationContext } returns context
        every { preferences.assistantVoiceSettings } returns flowOf(AssistantVoiceSettings())
        every { providers.observeProviders() } returns flowOf(emptyList())
        mockkConstructor(TextToSpeechManager::class)
        every { anyConstructed<TextToSpeechManager>().initialize() } returns flowOf(TtsInitResult.Success)
        every { anyConstructed<TextToSpeechManager>().configure(any()) } returns flowOf(TtsInitResult.Success)
        every { anyConstructed<TextToSpeechManager>().enqueue(any()) } returns flow { awaitCancellation() }
        every { anyConstructed<TextToSpeechManager>().release() } just Runs
        every { anyConstructed<TextToSpeechManager>().stop() } just Runs
        return AssistantVoiceSettingsViewModel(preferences, credentials, providers, context).also {
            store.put("settings", it)
            runCurrent()
        }
    }

    @After fun cleanup() {
        store.clear()
        unmockkAll()
        Dispatchers.resetMain()
    }

    @Test fun `picker initializes lazily and preview uses draft without saving`() = runTest {
        val vm = create()
        verify(exactly = 0) { anyConstructed<TextToSpeechManager>().initialize() }
        vm.openVoicePicker(); runCurrent()
        val draft = AssistantVoiceSettings(androidVoiceId = "offline", languageTag = "fr-FR", speechRate = 1.5f)
        vm.preview(draft); runCurrent()
        assertTrue(vm.previewing.value)
        verify { anyConstructed<TextToSpeechManager>().configure(draft) }
        coVerify(exactly = 0) { preferences.setAssistantVoiceSettings(any()) }
        vm.closeVoicePicker(); runCurrent()
        assertFalse(vm.previewing.value)
        verify { anyConstructed<TextToSpeechManager>().release() }
    }

    @Test fun `configuration failure is sanitized and never speaks`() = runTest {
        val vm = create()
        every { anyConstructed<TextToSpeechManager>().configure(any()) } returns
            flowOf(TtsInitResult.Error("private engine diagnostic"))
        vm.preview(AssistantVoiceSettings()); runCurrent()
        assertFalse(vm.previewing.value)
        assertFalse(vm.previewMessage.value.orEmpty().contains("private"))
        assertNotNull(vm.previewMessage.value)
        verify(exactly = 0) { anyConstructed<TextToSpeechManager>().enqueue(any()) }
    }

    @Test fun `clear cancels preview and releases its engine`() = runTest {
        val vm = create()
        vm.preview(AssistantVoiceSettings()); runCurrent()
        store.clear(); runCurrent()
        assertFalse(vm.previewing.value)
        verify { anyConstructed<TextToSpeechManager>().stop() }
        verify { anyConstructed<TextToSpeechManager>().release() }
    }
}
