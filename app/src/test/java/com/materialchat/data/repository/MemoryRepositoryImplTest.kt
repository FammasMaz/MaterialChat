package com.materialchat.data.repository

import com.materialchat.data.local.database.dao.MemoryDao
import com.materialchat.data.mapper.toEntity
import com.materialchat.domain.model.Memory
import com.materialchat.domain.model.MemoryKind
import com.materialchat.domain.model.MemorySnippet
import com.materialchat.domain.model.MessageRole
import com.materialchat.domain.model.RecalledMemorySource
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MemoryRepositoryImplTest {

    private lateinit var memoryDao: MemoryDao
    private lateinit var repository: MemoryRepositoryImpl

    @Before
    fun setup() {
        memoryDao = mockk(relaxUnitFun = true)
        coEvery { memoryDao.getActiveSnippets(any()) } returns emptyList()
        coEvery { memoryDao.searchActiveSnippets(any(), any(), any(), any(), any()) } returns emptyList()
    }

    @Test
    fun `recall - general memory question returns memories without token overlap`() = runTest {
        repository = MemoryRepositoryImpl(memoryDao, StandardTestDispatcher(testScheduler))
        coEvery { memoryDao.getActiveMemories(any()) } returns listOf(
            memory(content = "User prefers compact Kotlin examples", kind = MemoryKind.USER_PREFERENCE).toEntity(),
            memory(content = "User lives in Berlin", kind = MemoryKind.PERSONAL_FACT).toEntity()
        )

        val recalled = repository.recall(query = "What do you remember about me?", limit = 3)

        assertEquals(2, recalled.size)
    }

    @Test
    fun `recall - passive distinctive token recalls high confidence preference`() = runTest {
        repository = MemoryRepositoryImpl(memoryDao, StandardTestDispatcher(testScheduler))
        coEvery { memoryDao.getActiveMemories(any()) } returns listOf(
            memory(
                content = "User prefers dark mode for UI work",
                kind = MemoryKind.USER_PREFERENCE,
                confidence = 0.84f
            ).toEntity()
        )

        val recalled = repository.recall(query = "Make the background dark", limit = 3)

        assertEquals("User prefers dark mode for UI work", recalled.single().memory.content)
    }

    @Test
    fun `recall - project intent returns project memory without token overlap`() = runTest {
        repository = MemoryRepositoryImpl(memoryDao, StandardTestDispatcher(testScheduler))
        coEvery { memoryDao.getActiveMemories(any()) } returns listOf(
            memory(
                content = "MaterialChat is built with Kotlin and Jetpack Compose",
                kind = MemoryKind.PROJECT_FACT,
                confidence = 0.82f
            ).toEntity()
        )

        val recalled = repository.recall(query = "What stack is my app using?", limit = 3)

        assertEquals("MaterialChat is built with Kotlin and Jetpack Compose", recalled.single().memory.content)
    }

    @Test
    fun `recall - personal possession question can recall paraphrased personal memory`() = runTest {
        repository = MemoryRepositoryImpl(memoryDao, StandardTestDispatcher(testScheduler))
        coEvery { memoryDao.getActiveMemories(any()) } returns listOf(
            memory(
                content = "User owns a Pixel 9 Pro",
                kind = MemoryKind.PERSONAL_FACT,
                confidence = 0.82f
            ).toEntity()
        )

        val recalled = repository.recall(query = "What phone do I have?", limit = 3)

        assertEquals("User owns a Pixel 9 Pro", recalled.single().memory.content)
    }

    @Test
    fun `recall - favorite question can recall paraphrased preference memory`() = runTest {
        repository = MemoryRepositoryImpl(memoryDao, StandardTestDispatcher(testScheduler))
        coEvery { memoryDao.getActiveMemories(any()) } returns listOf(
            memory(
                content = "User prefers blue accent colors",
                kind = MemoryKind.USER_PREFERENCE,
                confidence = 0.82f
            ).toEntity()
        )

        val recalled = repository.recall(query = "What is my favorite color?", limit = 3)

        assertEquals("User prefers blue accent colors", recalled.single().memory.content)
    }

    @Test
    fun `recall - snippet drawer helps explicit discussion recall`() = runTest {
        repository = MemoryRepositoryImpl(memoryDao, StandardTestDispatcher(testScheduler))
        coEvery { memoryDao.getActiveMemories(any()) } returns emptyList()
        coEvery { memoryDao.getActiveSnippets(any()) } returns listOf(
            snippet("We decided the release strategy should use patch releases for memory fixes").toEntity()
        )

        val recalled = repository.recall(query = "What did we decide about release strategy?", limit = 3)

        assertEquals(RecalledMemorySource.VERBATIM_SNIPPET, recalled.single().source)
        assertTrue(recalled.single().memory.content.contains("patch releases"))
    }

    @Test
    fun `recall - generic do you know query stays quiet`() = runTest {
        repository = MemoryRepositoryImpl(memoryDao, StandardTestDispatcher(testScheduler))
        coEvery { memoryDao.getActiveMemories(any()) } returns listOf(
            memory(
                content = "User lives in Berlin",
                kind = MemoryKind.PERSONAL_FACT,
                confidence = 0.84f
            ).toEntity()
        )

        val recalled = repository.recall(query = "Do you know how to fix this crash?", limit = 3)

        assertTrue(recalled.isEmpty())
    }

    @Test
    fun `recall - unrelated casual query stays quiet`() = runTest {
        repository = MemoryRepositoryImpl(memoryDao, StandardTestDispatcher(testScheduler))
        coEvery { memoryDao.getActiveMemories(any()) } returns listOf(
            memory(
                content = "User prefers dark mode for UI work",
                kind = MemoryKind.USER_PREFERENCE,
                confidence = 0.84f
            ).toEntity()
        )

        val recalled = repository.recall(query = "How do I fix this Room migration crash?", limit = 3)

        assertTrue(recalled.isEmpty())
    }

    private fun memory(
        content: String,
        kind: MemoryKind,
        confidence: Float = 0.78f
    ) = Memory(
        id = content.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-'),
        content = content,
        kind = kind,
        confidence = confidence,
        createdAt = 1_000L,
        updatedAt = 1_000L
    )

    private fun snippet(content: String) = MemorySnippet(
        id = "snippet-msg-1",
        conversationId = "conv-1",
        messageId = "msg-1",
        role = MessageRole.USER,
        content = content,
        createdAt = 1_000L,
        updatedAt = 1_000L
    )
}
