package com.materialchat.data.backup

import kotlinx.serialization.Serializable

@Serializable
data class EncryptedBackupFile(
    val formatVersion: Int,
    val appPackage: String,
    val appVersionName: String,
    val appVersionCode: Int,
    val createdAtMillis: Long,
    val kdf: String,
    val kdfIterations: Int,
    val saltBase64: String,
    val cipher: String,
    val ivBase64: String,
    val payloadBase64: String
)

@Serializable
data class BackupPayload(
    val formatVersion: Int,
    val databaseVersion: Int,
    val appVersionName: String,
    val appVersionCode: Int,
    val createdAtMillis: Long,
    val providers: List<BackupProvider>,
    val personas: List<BackupPersona>,
    val conversations: List<BackupConversation>,
    val messages: List<BackupMessage>,
    val bookmarks: List<BackupBookmark>
)

@Serializable
data class BackupProvider(
    val id: String,
    val name: String,
    val type: String,
    val baseUrl: String,
    val defaultModel: String,
    val requiresApiKey: Boolean,
    val isActive: Boolean
)

@Serializable
data class BackupPersona(
    val id: String,
    val name: String,
    val emoji: String,
    val description: String,
    val systemPrompt: String,
    val expertiseTags: String?,
    val tone: String,
    val conversationStarters: String?,
    val colorSeed: Int?,
    val isBuiltin: Int,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class BackupConversation(
    val id: String,
    val title: String,
    val icon: String?,
    val providerId: String?,
    val modelName: String,
    val isArchived: Boolean,
    val archiveTime: Long?,
    val parentId: String?,
    val branchSourceMessageId: String?,
    val createdAt: Long,
    val personaId: String?,
    val updatedAt: Long
)

@Serializable
data class BackupMessage(
    val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val thinkingContent: String?,
    val imageAttachments: String?,
    val thinkingDurationMs: Long?,
    val totalDurationMs: Long?,
    val modelName: String?,
    val fusionMetadata: String?,
    val webSearchMetadata: String?,
    val createdAt: Long
)

@Serializable
data class BackupBookmark(
    val id: String,
    val messageId: String,
    val conversationId: String,
    val tags: String?,
    val note: String?,
    val category: String,
    val createdAt: Long
)

data class BackupSummary(
    val conversations: Int,
    val messages: Int,
    val bookmarks: Int,
    val customPersonas: Int,
    val providers: Int
) {
    fun label(): String {
        return "$conversations conversations, $messages messages, " +
            "$bookmarks bookmarks, $customPersonas personas, $providers providers"
    }
}

data class BackupExport(
    val bytes: ByteArray,
    val filename: String,
    val summary: BackupSummary
)

data class BackupPreview(
    val createdAtMillis: Long,
    val appVersionName: String,
    val summary: BackupSummary
)

data class BackupRestoreResult(
    val summary: BackupSummary
)
