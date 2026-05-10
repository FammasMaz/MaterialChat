package com.materialchat.data.backup

import android.util.Base64
import androidx.room.withTransaction
import com.materialchat.BuildConfig
import com.materialchat.data.local.database.MaterialChatDatabase
import com.materialchat.data.local.database.dao.BookmarkDao
import com.materialchat.data.local.database.dao.ConversationDao
import com.materialchat.data.local.database.dao.MessageDao
import com.materialchat.data.local.database.dao.MemoryDao
import com.materialchat.data.local.database.dao.PersonaDao
import com.materialchat.data.local.database.dao.ProviderDao
import com.materialchat.data.local.database.entity.BookmarkEntity
import com.materialchat.data.local.database.entity.ConversationEntity
import com.materialchat.data.local.database.entity.MessageEntity
import com.materialchat.data.local.database.entity.MemoryEntity
import com.materialchat.data.local.database.entity.PersonaEntity
import com.materialchat.data.local.database.entity.ProviderEntity
import com.materialchat.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(
    private val database: MaterialChatDatabase,
    private val providerDao: ProviderDao,
    private val personaDao: PersonaDao,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val bookmarkDao: BookmarkDao,
    private val memoryDao: MemoryDao,
    private val json: Json,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun createEncryptedBackup(passphrase: String): BackupExport = withContext(ioDispatcher) {
        validatePassphrase(passphrase)
        val payload = buildPayload()
        val payloadJson = json.encodeToString(payload)
        val encrypted = encryptPayload(payloadJson, passphrase, payload.createdAtMillis)
        val backupBytes = json.encodeToString(encrypted).toByteArray(Charsets.UTF_8)

        BackupExport(
            bytes = backupBytes,
            filename = defaultBackupFilename(payload.createdAtMillis),
            summary = payload.summary()
        )
    }

    suspend fun previewEncryptedBackup(
        backupBytes: ByteArray,
        passphrase: String
    ): BackupPreview = withContext(ioDispatcher) {
        validatePassphrase(passphrase)
        val envelope = decodeEnvelope(backupBytes)
        val payload = decryptPayload(envelope, passphrase)
        BackupPreview(
            createdAtMillis = payload.createdAtMillis,
            appVersionName = payload.appVersionName,
            summary = payload.summary()
        )
    }

    suspend fun restoreEncryptedBackup(
        backupBytes: ByteArray,
        passphrase: String
    ): BackupRestoreResult = withContext(ioDispatcher) {
        validatePassphrase(passphrase)
        val payload = decryptPayload(decodeEnvelope(backupBytes), passphrase)
        restorePayload(payload)
        BackupRestoreResult(payload.summary())
    }

    private suspend fun buildPayload(): BackupPayload {
        val conversations = conversationDao.getAllConversationsForBackup()
        val conversationIds = conversations.map { it.id }
        val messages = if (conversationIds.isEmpty()) {
            emptyList()
        } else {
            messageDao.getMessagesForConversations(conversationIds)
        }
        val messageIds = messages.map { it.id }.toSet()
        val bookmarks = if (conversationIds.isEmpty()) {
            emptyList()
        } else {
            bookmarkDao.getBookmarksForConversations(conversationIds)
                .filter { it.messageId in messageIds }
        }

        return BackupPayload(
            formatVersion = BACKUP_FORMAT_VERSION,
            databaseVersion = DATABASE_VERSION,
            appVersionName = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE,
            createdAtMillis = System.currentTimeMillis(),
            providers = providerDao.getAllProvidersOnce().map { it.toBackup() },
            personas = personaDao.getAllPersonasOnce()
                .filter { it.isBuiltin == 0 }
                .map { it.toBackup() },
            conversations = conversations.map { it.toBackup() },
            messages = messages.map { it.toBackup() },
            bookmarks = bookmarks.map { it.toBackup() },
            memories = memoryDao.getAllMemoriesForBackup().map { it.toBackup() }
        )
    }

    private suspend fun restorePayload(payload: BackupPayload) {
        check(payload.formatVersion == BACKUP_FORMAT_VERSION) {
            "Unsupported backup format: ${payload.formatVersion}"
        }

        val providers = payload.providers.mapNotNull { it.toEntityOrNull() }
        val personas = payload.personas.mapNotNull { it.toEntityOrNull() }
        val conversations = restoreReadyConversations(payload.conversations, providers)
        val messages = restoreReadyMessages(payload.messages, conversations)
        val bookmarks = restoreReadyBookmarks(payload.bookmarks, conversations, messages)
        val memories = restoreReadyMemories(payload.memories)

        database.withTransaction {
            if (providers.any { it.isActive }) providerDao.deactivateAllProviders()
            providers.forEach { upsertProvider(it) }
            personaDao.insertAll(personas)
            sortedForForeignKeys(conversations).forEach { upsertConversation(it) }
            conversations.forEach { messageDao.deleteAllMessagesInConversation(it.id) }
            messageDao.insertAll(messages)
            bookmarkDao.insertAll(bookmarks)
            memoryDao.insertAll(memories)
        }
    }

    private suspend fun upsertProvider(provider: ProviderEntity) {
        if (providerDao.getProviderById(provider.id) == null) {
            providerDao.insert(provider)
        } else {
            providerDao.update(provider)
        }
    }

    private suspend fun upsertConversation(conversation: ConversationEntity) {
        if (conversationDao.conversationExists(conversation.id)) {
            conversationDao.update(conversation)
        } else {
            conversationDao.insert(conversation)
        }
    }

    private fun restoreReadyConversations(
        backups: List<BackupConversation>,
        providers: List<ProviderEntity>
    ): List<ConversationEntity> {
        val providerIds = providers.map { it.id }.toSet()
        return backups.filter { it.id.isNotBlank() }.map {
            it.toEntity(providerIds).copy(isEphemeral = false)
        }
    }

    private fun restoreReadyMessages(
        backups: List<BackupMessage>,
        conversations: List<ConversationEntity>
    ): List<MessageEntity> {
        val conversationIds = conversations.map { it.id }.toSet()
        return backups
            .filter { it.id.isNotBlank() && it.conversationId in conversationIds }
            .map { it.toEntity().copy(isStreaming = false) }
    }

    private fun restoreReadyBookmarks(
        backups: List<BackupBookmark>,
        conversations: List<ConversationEntity>,
        messages: List<MessageEntity>
    ): List<BookmarkEntity> {
        val conversationIds = conversations.map { it.id }.toSet()
        val messageIds = messages.map { it.id }.toSet()
        return backups
            .filter { it.id.isNotBlank() }
            .filter { it.conversationId in conversationIds && it.messageId in messageIds }
            .map { it.toEntity() }
    }

    private fun restoreReadyMemories(backups: List<BackupMemory>): List<MemoryEntity> {
        return backups
            .filter { it.id.isNotBlank() && it.content.isNotBlank() && it.normalizedContent.isNotBlank() }
            .map { it.toEntity() }
    }

    private fun sortedForForeignKeys(conversations: List<ConversationEntity>): List<ConversationEntity> {
        val allIds = conversations.map { it.id }.toSet()
        val pending = conversations.map { conversation ->
            if (conversation.parentId in allIds) conversation else conversation.copy(parentId = null)
        }.toMutableList()
        val inserted = mutableSetOf<String>()
        val sorted = mutableListOf<ConversationEntity>()

        while (pending.isNotEmpty()) {
            val ready = pending.filter { it.parentId == null || it.parentId in inserted }
            if (ready.isEmpty()) {
                sorted += pending.map { it.copy(parentId = null) }
                break
            }
            sorted += ready
            inserted += ready.map { it.id }
            pending.removeAll(ready.toSet())
        }

        return sorted
    }

    private fun encryptPayload(
        payloadJson: String,
        passphrase: String,
        createdAtMillis: Long
    ): EncryptedBackupFile {
        val salt = randomBytes(SALT_BYTES)
        val iv = randomBytes(IV_BYTES)
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(ASSOCIATED_DATA)
        val encryptedPayload = cipher.doFinal(payloadJson.toByteArray(Charsets.UTF_8))

        return EncryptedBackupFile(
            formatVersion = BACKUP_FORMAT_VERSION,
            appPackage = BuildConfig.APPLICATION_ID,
            appVersionName = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE,
            createdAtMillis = createdAtMillis,
            kdf = KDF,
            kdfIterations = KDF_ITERATIONS,
            saltBase64 = encodeBase64(salt),
            cipher = CIPHER,
            ivBase64 = encodeBase64(iv),
            payloadBase64 = encodeBase64(encryptedPayload)
        )
    }

    private fun decryptPayload(envelope: EncryptedBackupFile, passphrase: String): BackupPayload {
        check(envelope.formatVersion == BACKUP_FORMAT_VERSION) {
            "Unsupported backup format: ${envelope.formatVersion}"
        }
        return try {
            val salt = decodeBase64(envelope.saltBase64)
            val iv = decodeBase64(envelope.ivBase64)
            val encryptedPayload = decodeBase64(envelope.payloadBase64)
            val cipher = Cipher.getInstance(envelope.cipher)
            val key = deriveKey(passphrase, salt, envelope.kdfIterations)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(ASSOCIATED_DATA)
            val payloadJson = cipher.doFinal(encryptedPayload).toString(Charsets.UTF_8)
            json.decodeFromString(payloadJson)
        } catch (e: AEADBadTagException) {
            throw IOException("Wrong backup password or corrupted backup", e)
        } catch (e: IllegalArgumentException) {
            throw IOException("Invalid backup file", e)
        }
    }

    private fun decodeEnvelope(backupBytes: ByteArray): EncryptedBackupFile {
        return try {
            json.decodeFromString(backupBytes.toString(Charsets.UTF_8))
        } catch (e: SerializationException) {
            throw IOException("This is not a valid MaterialChat backup file", e)
        } catch (e: IllegalArgumentException) {
            throw IOException("This is not a valid MaterialChat backup file", e)
        }
    }

    private fun deriveKey(
        passphrase: String,
        salt: ByteArray,
        iterations: Int = KDF_ITERATIONS
    ): SecretKeySpec {
        val password = passphrase.toCharArray()
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        return try {
            val keyBytes = SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
            SecretKeySpec(keyBytes, "AES")
        } finally {
            spec.clearPassword()
            password.fill('\u0000')
        }
    }

    private fun validatePassphrase(passphrase: String) {
        require(passphrase.length >= MIN_PASSWORD_LENGTH) {
            "Backup password must be at least $MIN_PASSWORD_LENGTH characters."
        }
    }

    private fun BackupPayload.summary(): BackupSummary {
        return BackupSummary(
            conversations = conversations.size,
            messages = messages.size,
            bookmarks = bookmarks.size,
            customPersonas = personas.size,
            providers = providers.size,
            memories = memories.size
        )
    }

    private fun defaultBackupFilename(createdAtMillis: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US)
        return "materialchat-backup-${formatter.format(Date(createdAtMillis))}.mchatbak"
    }

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also { secureRandom.nextBytes(it) }

    private fun encodeBase64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decodeBase64(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)

    private fun ProviderEntity.toBackup() = BackupProvider(
        id, name, type, baseUrl, defaultModel, requiresApiKey, isActive
    )

    private fun BackupProvider.toEntityOrNull(): ProviderEntity? {
        if (id.isBlank() || name.isBlank() || type.isBlank()) return null
        return ProviderEntity(id, name, type, baseUrl, defaultModel, requiresApiKey, isActive)
    }

    private fun PersonaEntity.toBackup() = BackupPersona(
        id, name, emoji, description, systemPrompt, expertiseTags, tone,
        conversationStarters, colorSeed, isBuiltin, createdAt, updatedAt
    )

    private fun BackupPersona.toEntityOrNull(): PersonaEntity? {
        if (id.isBlank() || name.isBlank()) return null
        return PersonaEntity(
            id, name, emoji, description, systemPrompt, expertiseTags, tone,
            conversationStarters, colorSeed, isBuiltin, createdAt, updatedAt
        )
    }

    private fun ConversationEntity.toBackup() = BackupConversation(
        id, title, icon, providerId, modelName, isArchived, archiveTime,
        parentId, branchSourceMessageId, createdAt, personaId, updatedAt
    )

    private fun BackupConversation.toEntity(providerIds: Set<String>): ConversationEntity {
        return ConversationEntity(
            id = id,
            title = title,
            icon = icon,
            providerId = providerId?.takeIf { it in providerIds },
            modelName = modelName,
            isEphemeral = false,
            isArchived = isArchived,
            archiveTime = archiveTime,
            parentId = parentId,
            branchSourceMessageId = branchSourceMessageId,
            createdAt = createdAt,
            personaId = personaId,
            updatedAt = updatedAt
        )
    }

    private fun MessageEntity.toBackup() = BackupMessage(
        id = id,
        conversationId = conversationId,
        role = role,
        content = content,
        thinkingContent = thinkingContent,
        imageAttachments = imageAttachments,
        thinkingDurationMs = thinkingDurationMs,
        totalDurationMs = totalDurationMs,
        modelName = modelName,
        fusionMetadata = fusionMetadata,
        webSearchMetadata = webSearchMetadata,
        memoryMetadata = memoryMetadata,
        createdAt = createdAt
    )

    private fun BackupMessage.toEntity(): MessageEntity {
        return MessageEntity(
            id = id,
            conversationId = conversationId,
            role = role,
            content = content,
            thinkingContent = thinkingContent,
            imageAttachments = imageAttachments,
            isStreaming = false,
            thinkingDurationMs = thinkingDurationMs,
            totalDurationMs = totalDurationMs,
            modelName = modelName,
            fusionMetadata = fusionMetadata,
            webSearchMetadata = webSearchMetadata,
            memoryMetadata = memoryMetadata,
            createdAt = createdAt
        )
    }

    private fun BookmarkEntity.toBackup() = BackupBookmark(
        id, messageId, conversationId, tags, note, category, createdAt
    )

    private fun BackupBookmark.toEntity() = BookmarkEntity(
        id, messageId, conversationId, tags, note, category, createdAt
    )

    private fun MemoryEntity.toBackup() = BackupMemory(
        id = id,
        content = content,
        normalizedContent = normalizedContent,
        kind = kind,
        confidence = confidence,
        sourceConversationId = sourceConversationId,
        sourceMessageId = sourceMessageId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastRecalledAt = lastRecalledAt,
        recallCount = recallCount,
        isArchived = isArchived
    )

    private fun BackupMemory.toEntity() = MemoryEntity(
        id = id,
        content = content,
        normalizedContent = normalizedContent,
        kind = kind,
        confidence = confidence,
        sourceConversationId = sourceConversationId,
        sourceMessageId = sourceMessageId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastRecalledAt = lastRecalledAt,
        recallCount = recallCount,
        isArchived = isArchived
    )

    private companion object {
        const val BACKUP_FORMAT_VERSION = 1
        const val DATABASE_VERSION = 18
        const val MIN_PASSWORD_LENGTH = 8
        const val KDF = "PBKDF2WithHmacSHA256"
        const val KDF_ITERATIONS = 180_000
        const val CIPHER = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
        const val SALT_BYTES = 16
        const val IV_BYTES = 12
        val ASSOCIATED_DATA = "MaterialChat encrypted backup v1".toByteArray(Charsets.UTF_8)
        val secureRandom = SecureRandom()
    }
}
