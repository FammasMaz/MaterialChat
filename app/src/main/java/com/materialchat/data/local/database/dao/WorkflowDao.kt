package com.materialchat.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.materialchat.data.local.database.entity.WorkflowEntity
import com.materialchat.data.local.database.entity.WorkflowStepEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for workflow operations.
 */
@Dao
interface WorkflowDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkflow(workflow: WorkflowEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSteps(steps: List<WorkflowStepEntity>)

    @Query("SELECT * FROM workflows ORDER BY updated_at DESC")
    fun observeAllWorkflows(): Flow<List<WorkflowEntity>>

    @Query("SELECT * FROM workflows WHERE id = :workflowId")
    suspend fun getWorkflowById(workflowId: String): WorkflowEntity?

    @Query("SELECT * FROM workflow_steps WHERE workflow_id = :workflowId ORDER BY step_order ASC")
    suspend fun getStepsForWorkflow(workflowId: String): List<WorkflowStepEntity>

    @Query("DELETE FROM workflows WHERE id = :workflowId")
    suspend fun deleteWorkflow(workflowId: String)

    @Query("DELETE FROM workflow_steps WHERE workflow_id = :workflowId")
    suspend fun deleteStepsForWorkflow(workflowId: String)

    @Query("SELECT COUNT(*) FROM workflows")
    suspend fun getWorkflowCount(): Int
}
