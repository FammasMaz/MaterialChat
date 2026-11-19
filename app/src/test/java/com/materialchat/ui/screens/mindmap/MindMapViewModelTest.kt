package com.materialchat.ui.screens.mindmap

import androidx.lifecycle.SavedStateHandle
import com.materialchat.domain.model.ConversationTreeNode
import com.materialchat.domain.usecase.GetConversationTreeUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * A tree that cannot be built reaches [MindMapUiState.Error] with fixed copy (never the
 * exception text), and [MindMapViewModel.retry] goes back through loading to success.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MindMapViewModelTest {

    private val useCase = mockk<GetConversationTreeUseCase>()

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a throwing tree load shows fixed copy and a successful retry reaches success`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val tree = ConversationTreeNode(id = "c1", title = "Root", modelName = "gpt")
        var attempts = 0
        coEvery { useCase("c1") } answers {
            attempts++
            if (attempts == 1) throw IOException("database closed") else tree
        }

        val viewModel = MindMapViewModel(SavedStateHandle(mapOf("conversationId" to "c1")), useCase)
        assertTrue(viewModel.uiState.value is MindMapUiState.Loading)
        runCurrent()

        val failed = viewModel.uiState.value
        assertTrue(failed is MindMapUiState.Error)
        assertEquals(MindMapViewModel.TREE_UNAVAILABLE, (failed as MindMapUiState.Error).message)
        assertTrue("exception text must not reach the screen", "database closed" !in failed.message)

        viewModel.retry()
        assertTrue("retry shows loading first", viewModel.uiState.value is MindMapUiState.Loading)
        runCurrent()

        val recovered = viewModel.uiState.value
        assertTrue(recovered is MindMapUiState.Success)
        assertEquals(tree, (recovered as MindMapUiState.Success).tree)
        assertEquals("c1", recovered.selectedNodeId)
        assertEquals(2, attempts)
    }

    @Test
    fun `a null tree is the same fixed error`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        coEvery { useCase("c1") } returns null

        val viewModel = MindMapViewModel(SavedStateHandle(mapOf("conversationId" to "c1")), useCase)
        runCurrent()

        assertEquals(MindMapUiState.Error(MindMapViewModel.TREE_UNAVAILABLE), viewModel.uiState.value)
    }

    @Test
    fun `selecting a node only applies in the success state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        coEvery { useCase("c1") } returns null

        val viewModel = MindMapViewModel(SavedStateHandle(mapOf("conversationId" to "c1")), useCase)
        runCurrent()
        viewModel.selectNode("c2")

        assertTrue(viewModel.uiState.value is MindMapUiState.Error)
    }
}
