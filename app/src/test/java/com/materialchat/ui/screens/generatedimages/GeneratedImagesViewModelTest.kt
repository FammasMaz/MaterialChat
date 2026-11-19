package com.materialchat.ui.screens.generatedimages

import com.materialchat.domain.model.GeneratedImage
import com.materialchat.domain.repository.ConversationRepository
import com.materialchat.ui.components.LoadError
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
 * A failing image library must reach [GeneratedImagesUiState.error] instead of showing the
 * "no images yet" empty state, and a retry that succeeds must clear it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GeneratedImagesViewModelTest {

    private val repository = mockk<ConversationRepository>()

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a failing observation reaches the error state and a successful retry clears it`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val image = image("img-1")
        var attempts = 0
        every { repository.observeGeneratedImages() } answers {
            attempts++
            if (attempts == 1) flow { throw IOException("database closed") } else flowOf(listOf(image))
        }

        val viewModel = GeneratedImagesViewModel(repository)
        assertTrue("Initial state is loading, not empty", viewModel.uiState.value.isLoading)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        val failed = viewModel.uiState.value
        assertEquals(LoadError.Unavailable, failed.error)
        assertFalse(failed.isLoading)
        assertTrue(failed.images.isEmpty())

        viewModel.retry()
        runCurrent()

        val recovered = viewModel.uiState.value
        assertNull("Retry must clear the error", recovered.error)
        assertFalse(recovered.isLoading)
        assertEquals(listOf(image), recovered.images)
        assertEquals(2, attempts)
    }

    @Test
    fun `a repository that throws on subscription reaches the error state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        every { repository.observeGeneratedImages() } throws IllegalStateException("not initialised")

        val viewModel = GeneratedImagesViewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        assertEquals(LoadError.Unavailable, viewModel.uiState.value.error)
    }

    private fun image(id: String) = GeneratedImage(
        id = id,
        uri = "content://images/$id",
        mimeType = "image/png",
        conversationId = "conv",
        messageId = "msg",
        conversationTitle = "Chat",
        conversationIcon = null,
        prompt = "a teapot",
        modelName = "model",
        createdAt = 1L
    )
}
