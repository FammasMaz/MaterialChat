package com.materialchat.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.materialchat.data.local.database.MaterialChatDatabase
import com.materialchat.data.local.database.entity.ConversationEntity
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A crash or process death mid-stream leaves messages marked as streaming.
 * Opening the chat used to be the only thing that cleared them, so any chat the
 * user did not revisit kept a permanent typing indicator and a "streaming"
 * badge. Cleared once per process, before new streams exist.
 */
@RunWith(AndroidJUnit4::class)
class InterruptedStreamCleanupTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: MaterialChatDatabase
    private lateinit var repository: ConversationRepositoryImpl

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, MaterialChatDatabase::class.java).build()
        repository = ConversationRepositoryImpl(
            database.conversationDao(),
            database.messageDao(),
            context
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun clearingInterruptedStreamsResetsEveryConversation() = runBlocking {
        createConversation("a")
        createConversation("b")
        val streamingA = addMessage("a", streaming = true)
        val streamingB = addMessage("b", streaming = true)

        repository.clearInterruptedStreamingFlags()

        assertFalse(database.messageDao().getMessageById(streamingA)!!.isStreaming)
        assertFalse(database.messageDao().getMessageById(streamingB)!!.isStreaming)
        assertEquals(
            emptySet<String>(),
            database.messageDao().observeStreamingConversationIds().first().toSet()
        )
    }

    @Test
    fun clearingInterruptedStreamsKeepsFinishedMessagesUntouched() = runBlocking {
        createConversation("a")
        val done = addMessage("a", streaming = false)
        val streaming = addMessage("a", streaming = true)

        repository.clearInterruptedStreamingFlags()

        assertFalse(database.messageDao().getMessageById(done)!!.isStreaming)
        assertFalse(database.messageDao().getMessageById(streaming)!!.isStreaming)
    }

    private suspend fun createConversation(id: String) {
        database.conversationDao().insert(
            ConversationEntity(
                id = id,
                title = id,
                providerId = null,
                modelName = "model",
                createdAt = 1L,
                updatedAt = 1L
            )
        )
    }

    private suspend fun addMessage(conversationId: String, streaming: Boolean): String {
        val message = Message(
            conversationId = conversationId,
            role = MessageRole.ASSISTANT,
            content = if (streaming) "partial" else "done",
            isStreaming = streaming
        )
        repository.addMessage(message)
        return message.id
    }
}
