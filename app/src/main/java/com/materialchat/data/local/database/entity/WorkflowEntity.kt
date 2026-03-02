package com.materialchat.data.local.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a multi-step prompt workflow.
 */
@Entity(tableName = "workflows")
data class WorkflowEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "description", defaultValue = "")
    val description: String = "",

    @ColumnInfo(name = "icon", defaultValue = "\uD83D\uDD17")
    val icon: String = "\uD83D\uDD17",

    @ColumnInfo(name = "is_template", defaultValue = "0")
    val isTemplate: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
