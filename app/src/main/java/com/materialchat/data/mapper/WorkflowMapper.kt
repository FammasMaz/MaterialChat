package com.materialchat.data.mapper

import com.materialchat.data.local.database.entity.WorkflowEntity
import com.materialchat.data.local.database.entity.WorkflowStepEntity
import com.materialchat.domain.model.Workflow
import com.materialchat.domain.model.WorkflowStep

fun WorkflowEntity.toDomain(steps: List<WorkflowStep> = emptyList()): Workflow = Workflow(
    id = id,
    name = name,
    description = description,
    icon = icon,
    steps = steps,
    isTemplate = isTemplate,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Workflow.toEntity(): WorkflowEntity = WorkflowEntity(
    id = id,
    name = name,
    description = description,
    icon = icon,
    isTemplate = isTemplate,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun WorkflowStepEntity.toDomain(): WorkflowStep = WorkflowStep(
    id = id,
    workflowId = workflowId,
    stepOrder = stepOrder,
    promptTemplate = promptTemplate,
    modelName = modelName,
    providerId = providerId,
    systemPrompt = systemPrompt
)

fun WorkflowStep.toEntity(workflowId: String): WorkflowStepEntity = WorkflowStepEntity(
    id = id,
    workflowId = workflowId,
    stepOrder = stepOrder,
    promptTemplate = promptTemplate,
    modelName = modelName,
    providerId = providerId,
    systemPrompt = systemPrompt
)

fun List<WorkflowStepEntity>.toStepDomainList(): List<WorkflowStep> = map { it.toDomain() }
