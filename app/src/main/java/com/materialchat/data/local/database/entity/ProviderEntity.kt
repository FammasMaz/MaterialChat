package com.materialchat.data.local.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing an AI provider configuration.
 *
 * This entity stores provider information including connection details
 * and configuration. API keys and OAuth tokens are stored separately
 * in encrypted storage.
 */
@Entity(tableName = "providers")
data class ProviderEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "auth_type", defaultValue = "API_KEY")
    val authType: String = "API_KEY",

    @ColumnInfo(name = "base_url")
    val baseUrl: String,

    @ColumnInfo(name = "default_model")
    val defaultModel: String,

    @ColumnInfo(name = "requires_api_key")
    val requiresApiKey: Boolean,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean,

    @ColumnInfo(name = "system_prompt", defaultValue = "NULL")
    val systemPrompt: String? = null,

    @ColumnInfo(name = "headers_json", defaultValue = "NULL")
    val headersJson: String? = null,

    @ColumnInfo(name = "options_json", defaultValue = "NULL")
    val optionsJson: String? = null,

    @ColumnInfo(name = "supports_streaming", defaultValue = "1")
    val supportsStreaming: Boolean = true,

    @ColumnInfo(name = "supports_images", defaultValue = "0")
    val supportsImages: Boolean = false
)
