package com.materialchat.data.repository

import com.materialchat.data.local.database.dao.WorkflowDao
import com.materialchat.data.mapper.toDomain
import com.materialchat.data.mapper.toEntity
import com.materialchat.data.mapper.toStepDomainList
import com.materialchat.domain.model.Workflow
import com.materialchat.domain.model.WorkflowStep
import com.materialchat.domain.repository.WorkflowRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkflowRepositoryImpl @Inject constructor(
    private val workflowDao: WorkflowDao
) : WorkflowRepository {

    override fun observeAllWorkflows(): Flow<List<Workflow>> {
        return workflowDao.observeAllWorkflows().map { entities ->
            entities.map { entity ->
                val steps = workflowDao.getStepsForWorkflow(entity.id).toStepDomainList()
                entity.toDomain(steps)
            }
        }
    }

    override suspend fun getWorkflow(workflowId: String): Workflow? {
        val entity = workflowDao.getWorkflowById(workflowId) ?: return null
        val steps = workflowDao.getStepsForWorkflow(workflowId).toStepDomainList()
        return entity.toDomain(steps)
    }

    override suspend fun getStepsForWorkflow(workflowId: String): List<WorkflowStep> {
        return workflowDao.getStepsForWorkflow(workflowId).toStepDomainList()
    }

    override suspend fun saveWorkflow(workflow: Workflow) {
        workflowDao.insertWorkflow(workflow.toEntity())
        if (workflow.steps.isNotEmpty()) {
            saveSteps(workflow.id, workflow.steps)
        }
    }

    override suspend fun saveSteps(workflowId: String, steps: List<WorkflowStep>) {
        workflowDao.deleteStepsForWorkflow(workflowId)
        val entities = steps.mapIndexed { index, step ->
            step.copy(stepOrder = index).toEntity(workflowId)
        }
        workflowDao.insertSteps(entities)
    }

    override suspend fun deleteWorkflow(workflowId: String) {
        workflowDao.deleteWorkflow(workflowId)
    }

    override suspend fun hasWorkflows(): Boolean {
        return workflowDao.getWorkflowCount() > 0
    }
}
