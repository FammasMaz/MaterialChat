package com.materialchat.data.repository

import com.materialchat.data.local.database.dao.ConversationDao
import com.materialchat.data.local.database.dao.MessageDao
import com.materialchat.data.mapper.toDomain
import com.materialchat.data.mapper.toEntity
import com.materialchat.data.mapper.toConversationDomainList
import com.materialchat.data.mapper.toMessageDomainList
import com.materialchat.domain.model.Conversation
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import com.materialchat.domain.repository.ConversationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [ConversationRepository] that uses Room for persistence.
 */
@Singleton
class ConversationRepositoryImpl @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) : ConversationRepository {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    // ========== Conversation Operations ==========

    override fun observeConversations(): Flow<List<Conversation>> {
        return conversationDao.getAllConversations().map { entities ->
            entities.toConversationDomainList()
        }
    }

    override suspend fun getConversation(conversationId: String): Conversation? {
        return conversationDao.getConversationById(conversationId)?.toDomain()
    }

    override suspend fun createConversation(conversation: Conversation): String {
        conversationDao.insert(conversation.toEntity())
        return conversation.id
    }

    override suspend fun updateConversation(conversation: Conversation) {
        conversationDao.update(conversation.toEntity())
    }

    override suspend fun deleteConversation(conversationId: String) {
        // Messages are cascade deleted via Room foreign key constraint
        conversationDao.deleteById(conversationId)
    }

    override suspend fun updateConversationTitle(conversationId: String, title: String) {
        conversationDao.updateTitle(
            conversationId = conversationId,
            title = title,
            updatedAt = System.currentTimeMillis()
        )
    }

    override suspend fun updateConversationModel(conversationId: String, modelName: String) {
        conversationDao.updateModel(
            conversationId = conversationId,
            modelName = modelName,
            updatedAt = System.currentTimeMillis()
        )
    }

    // ========== Message Operations ==========

    override fun observeMessages(conversationId: String): Flow<List<Message>> {
        return messageDao.getMessagesForConversation(conversationId).map { entities ->
            entities.toMessageDomainList()
        }
    }

    override suspend fun getMessages(conversationId: String): List<Message> {
        return messageDao.getMessagesForConversationOnce(conversationId).toMessageDomainList()
    }

    override suspend fun addMessage(message: Message): String {
        messageDao.insert(message.toEntity())

        // Update the conversation's updatedAt timestamp
        conversationDao.updateTimestamp(
            conversationId = message.conversationId,
            updatedAt = System.currentTimeMillis()
        )

        return message.id
    }

    override suspend fun updateMessage(message: Message) {
        messageDao.update(message.toEntity())
    }

    override suspend fun updateMessageContent(messageId: String, content: String) {
        messageDao.updateContent(messageId, content)
    }

    override suspend fun updateMessageContentWithThinking(
        messageId: String,
        content: String,
        thinkingContent: String?
    ) {
        messageDao.updateContentWithThinking(messageId, content, thinkingContent)
    }

    override suspend fun setMessageStreaming(messageId: String, isStreaming: Boolean) {
        messageDao.updateStreamingStatus(messageId, isStreaming)
    }

    override suspend fun deleteMessage(messageId: String) {
        messageDao.deleteById(messageId)
    }

    override suspend fun deleteLastMessage(conversationId: String) {
        messageDao.deleteLastMessages(conversationId, count = 1)
    }

    // ========== Export Operations ==========

    override suspend fun exportToJson(conversationId: String): String {
        val conversation = conversationDao.getConversationById(conversationId)?.toDomain()
            ?: return "{}"

        val messages = messageDao.getMessagesForConversationOnce(conversationId)
            .toMessageDomainList()
            .filter { it.role != MessageRole.SYSTEM } // Exclude system messages from export

        val exportData = ConversationExportJson(
            id = conversation.id,
            title = conversation.title,
            modelName = conversation.modelName,
            createdAt = conversation.createdAt,
            updatedAt = conversation.updatedAt,
            messages = messages.map { message ->
                MessageExportJson(
                    id = message.id,
                    role = message.role.name.lowercase(),
                    content = message.content,
                    createdAt = message.createdAt
                )
            }
        )

        return json.encodeToString(exportData)
    }

    override suspend fun exportToMarkdown(conversationId: String): String {
        val conversation = conversationDao.getConversationById(conversationId)?.toDomain()
            ?: return ""

        val messages = messageDao.getMessagesForConversationOnce(conversationId)
            .toMessageDomainList()
            .filter { it.role != MessageRole.SYSTEM } // Exclude system messages from export

        return buildString {
            // Header
            appendLine("# ${conversation.title}")
            appendLine()
            appendLine("**Model:** ${conversation.modelName}")
            appendLine("**Created:** ${formatTimestamp(conversation.createdAt)}")
            appendLine()
            appendLine("---")
            appendLine()

            // Messages
            messages.forEach { message ->
                when (message.role) {
                    MessageRole.USER -> {
                        appendLine("## 🧑 User")
                        appendLine()
                        appendLine(message.content)
                        appendLine()
                    }
                    MessageRole.ASSISTANT -> {
                        appendLine("## 🤖 Assistant")
                        appendLine()
                        appendLine(message.content)
                        appendLine()
                    }
                    MessageRole.SYSTEM -> {
                        // System messages excluded from export
                    }
                }
            }
        }
    }

    /**
     * Formats a timestamp to a human-readable date string.
     */
    private fun formatTimestamp(timestamp: Long): String {
        val date = java.util.Date(timestamp)
        val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return format.format(date)
    }
}

/**
 * JSON export data structure for a conversation.
 */
@Serializable
private data class ConversationExportJson(
    val id: String,
    val title: String,
    val modelName: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<MessageExportJson>
)

/**
 * JSON export data structure for a message.
 */
@Serializable
private data class MessageExportJson(
    val id: String,
    val role: String,
    val content: String,
    val createdAt: Long
)
