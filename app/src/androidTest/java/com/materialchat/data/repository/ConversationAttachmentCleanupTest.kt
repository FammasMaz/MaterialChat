package com.materialchat.data.repository

import android.net.Uri
import android.system.Os
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.materialchat.data.local.database.MaterialChatDatabase
import com.materialchat.data.local.database.entity.ConversationEntity
import com.materialchat.domain.model.Attachment
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import com.materialchat.domain.usecase.BranchConversationUseCase
import com.materialchat.util.AttachmentFiles
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Real Room foreign keys, production repository/branching, and real private files. */
@RunWith(AndroidJUnit4::class)
class ConversationAttachmentCleanupTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: MaterialChatDatabase
    private lateinit var repository: ConversationRepositoryImpl
    private val files = mutableListOf<File>()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, MaterialChatDatabase::class.java).build()
        repository = ConversationRepositoryImpl(database.conversationDao(), database.messageDao(), context)
    }

    @After
    fun tearDown() {
        database.close()
        files.forEach { it.delete() }
    }

    @Test
    fun failedTimestampUpdateRollsBackTheMessageAndKeepsItsImage() = runBlocking {
        createConversation("root")
        val image = imageFile(AttachmentFiles.PICKED_DIR)
        val message = Message(
            id = "rejected-message",
            conversationId = "root",
            role = MessageRole.USER,
            content = "Keep this draft",
            attachments = listOf(Attachment(uri = Uri.fromFile(image).toString(), mimeType = "image/jpeg"))
        )
        rejectTimestampUpdates()

        val failure = runCatching { repository.addMessage(message) }.exceptionOrNull()

        assertNotNull("The real SQLite trigger must reject the timestamp update", failure)
        assertNull("A refused save must not leave a committed attachment reference", database.messageDao().getMessageById(message.id))
        assertEquals(1L, requireNotNull(database.conversationDao().getConversationById("root")).updatedAt)
        assertTrue("The unsaved image still belongs to the composer", image.exists())
    }

    @Test
    fun failedReplacementRestoresThePreviouslySavedMessage() = runBlocking {
        createConversation("root")
        val image = imageFile(AttachmentFiles.PICKED_DIR)
        val original = addImageMessage("root", image)
        val timestamp = requireNotNull(database.conversationDao().getConversationById("root")).updatedAt
        rejectTimestampUpdates()

        val failure = runCatching {
            repository.addMessage(original.copy(content = "Replacement that must roll back", attachments = emptyList()))
        }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals(listOf(original), repository.getMessages("root"))
        assertEquals(timestamp, requireNotNull(database.conversationDao().getConversationById("root")).updatedAt)
        assertTrue(image.exists())
    }

    @Test
    fun successfulSaveCommitsTheMessageAndConversationTimestampTogether() = runBlocking {
        createConversation("root")
        val before = System.currentTimeMillis()

        val message = addImageMessage("root", imageFile(AttachmentFiles.PICKED_DIR))

        val after = System.currentTimeMillis()
        val updatedAt = requireNotNull(database.conversationDao().getConversationById("root")).updatedAt
        assertEquals(listOf(message), repository.getMessages("root"))
        assertTrue("The committed timestamp must belong to this save", updatedAt in before..after)
    }

    private fun rejectTimestampUpdates() {
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER reject_timestamp BEFORE UPDATE OF updated_at ON conversations
            BEGIN
                SELECT RAISE(ABORT, 'timestamp update rejected by regression test');
            END
            """.trimIndent()
        )
    }

    @Test
    fun deletingBranchedMessageKeepsSharedFileUntilLastReferenceIsDeleted() = runBlocking {
        createConversation("root")
        val image = imageFile(AttachmentFiles.PICKED_DIR)
        val message = addImageMessage("root", image)
        val branchId = BranchConversationUseCase(repository)("root", message.id)
        val copiedMessage = repository.getMessages(branchId).single()
        assertEquals(message.attachments, copiedMessage.attachments)

        repository.deleteMessage(copiedMessage.id)
        assertTrue("Root message still needs the shared file", image.exists())
        assertNotNull(database.messageDao().getMessageById(message.id))

        repository.deleteMessage(message.id)
        assertFalse("Last reference removed: backing file must be deleted", image.exists())
    }

    @Test
    fun deletingBranchKeepsRootImagesButDeletesBranchOnlyImages() = runBlocking {
        createConversation("root")
        val shared = imageFile(AttachmentFiles.GENERATED_DIR)
        val source = addImageMessage("root", shared)
        val branchId = BranchConversationUseCase(repository)("root", source.id)
        val branchOnly = imageFile(AttachmentFiles.PICKED_DIR)
        addImageMessage(branchId, branchOnly)

        repository.deleteConversation(branchId)
        assertNull(repository.getConversation(branchId))
        assertTrue(shared.exists())
        assertFalse(branchOnly.exists())

        repository.deleteConversation("root")
        assertFalse(shared.exists())
    }

    @Test
    fun deletingRootCleansCascadedDescendantsButNotAnotherConversation() = runBlocking {
        createConversation("root")
        createConversation("branch", parentId = "root")
        createConversation("grandchild", parentId = "branch")
        createConversation("unrelated")
        val shared = imageFile(AttachmentFiles.PICKED_DIR)
        val exclusive = imageFile(AttachmentFiles.GENERATED_DIR)
        addImageMessage("root", shared)
        addImageMessage("grandchild", exclusive)
        addImageMessage("unrelated", shared)

        repository.deleteConversation("root")
        assertNull(repository.getConversation("branch"))
        assertNull(repository.getConversation("grandchild"))
        assertTrue(repository.getMessages("grandchild").isEmpty())
        assertFalse(exclusive.exists())
        assertTrue("An unrelated conversation still references this image", shared.exists())

        repository.deleteLastMessage("unrelated")
        assertFalse(shared.exists())
    }

    @Test
    fun startupEphemeralCleanupPreservesPersistentReferences() = runBlocking {
        createConversation("temporary", ephemeral = true)
        createConversation("temporary-branch", parentId = "temporary", ephemeral = true)
        createConversation("saved")
        val shared = imageFile(AttachmentFiles.PICKED_DIR)
        val temporaryOnly = imageFile(AttachmentFiles.GENERATED_DIR)
        addImageMessage("temporary", shared)
        addImageMessage("saved", shared)
        addImageMessage("temporary-branch", temporaryOnly)

        repository.deleteEphemeralConversations()
        assertNull(repository.getConversation("temporary"))
        assertNull(repository.getConversation("temporary-branch"))
        assertTrue(shared.exists())
        assertFalse(temporaryOnly.exists())
    }

    @Test
    fun plainPathAndFileUriIdentifyTheSameSharedFile() = runBlocking {
        createConversation("one")
        createConversation("two")
        val image = imageFile(AttachmentFiles.PICKED_DIR)
        val first = addImageMessage("one", image)
        val second = addImageMessage("two", image, uri = image.canonicalPath)

        repository.deleteMessage(first.id)
        assertTrue(image.exists())
        repository.deleteMessage(second.id)
        assertFalse(image.exists())
    }

    @Test
    fun corruptRemainingMetadataFailsClosedInsteadOfDeletingImages() = runBlocking {
        createConversation("one")
        val image = imageFile(AttachmentFiles.PICKED_DIR)
        val message = addImageMessage("one", image)
        database.messageDao().insert(
            requireNotNull(database.messageDao().getMessageById(message.id)).copy(
                id = "corrupt-reference",
                imageAttachments = "not valid attachment JSON"
            )
        )

        repository.deleteMessage(message.id)
        assertTrue("Unknown references must prevent destructive cleanup", image.exists())
    }

    @Test
    fun externalFilesAndSymlinksEscapingOwnedDirectoriesAreNeverDeleted() = runBlocking {
        createConversation("one")
        val external = File(context.cacheDir, "external-${UUID.randomUUID()}.jpg").apply {
            writeText("not owned")
            files += this
        }
        val link = File(context.filesDir, "${AttachmentFiles.PICKED_DIR}/link-${UUID.randomUUID()}.jpg")
        requireNotNull(link.parentFile).mkdirs()
        Os.symlink(external.path, link.path)
        files += link
        val externalMessage = addImageMessage("one", external)
        val linkMessage = addImageMessage("one", link)

        assertFalse(AttachmentFiles.isOwned(context, external.path))
        assertFalse(AttachmentFiles.isOwned(context, link.path))
        repository.deleteMessage(externalMessage.id)
        repository.deleteMessage(linkMessage.id)
        assertTrue(external.exists())
        assertTrue(link.exists())
    }

    private suspend fun createConversation(id: String, parentId: String? = null, ephemeral: Boolean = false) {
        database.conversationDao().insert(
            ConversationEntity(
                id = id,
                title = id,
                providerId = null,
                modelName = "test-model",
                parentId = parentId,
                isEphemeral = ephemeral,
                createdAt = 1L,
                updatedAt = 1L
            )
        )
    }

    private fun imageFile(directory: String): File =
        File(context.filesDir, "$directory/test-${UUID.randomUUID()}.jpg").apply {
            requireNotNull(parentFile).mkdirs()
            writeText("image bytes")
            files += this
        }

    private suspend fun addImageMessage(conversationId: String, file: File, uri: String = Uri.fromFile(file).toString()): Message {
        val message = Message(
            conversationId = conversationId,
            role = MessageRole.USER,
            content = "Look at this image",
            attachments = listOf(Attachment(uri = uri, mimeType = "image/jpeg"))
        )
        repository.addMessage(message)
        return message
    }
}
