package com.materialchat.data.local.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a single step in a workflow.
 */
@Entity(
    tableName = "workflow_steps",
    foreignKeys = [
        ForeignKey(
            entity = WorkflowEntity::class,
            parentColumns = ["id"],
            childColumns = ["workflow_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["workflow_id"])
    ]
)
data class WorkflowStepEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "workflow_id")
    val workflowId: String,

    @ColumnInfo(name = "step_order")
    val stepOrder: Int,

    @ColumnInfo(name = "prompt_template")
    val promptTemplate: String,

    @ColumnInfo(name = "model_name", defaultValue = "NULL")
    val modelName: String? = null,

    @ColumnInfo(name = "provider_id", defaultValue = "NULL")
    val providerId: String? = null,

    @ColumnInfo(name = "system_prompt", defaultValue = "NULL")
    val systemPrompt: String? = null
)
