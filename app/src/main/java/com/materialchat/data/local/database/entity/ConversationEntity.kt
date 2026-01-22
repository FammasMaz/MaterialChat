package com.materialchat.data.local.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a conversation with an AI assistant.
 *
 * This entity stores conversation metadata including the associated provider,
 * model, and timestamps. Messages are stored in a separate entity with a
 * foreign key reference to this entity.
 */
@Entity(
    tableName = "conversations",
    foreignKeys = [
        ForeignKey(
            entity = ProviderEntity::class,
            parentColumns = ["id"],
            childColumns = ["provider_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["provider_id"]),
        Index(value = ["updated_at"])
    ]
)
data class ConversationEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "icon", defaultValue = "NULL")
    val icon: String? = null,

    @ColumnInfo(name = "provider_id")
    val providerId: String?,

    @ColumnInfo(name = "model_name")
    val modelName: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
