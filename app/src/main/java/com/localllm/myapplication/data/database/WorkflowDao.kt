package com.localllm.myapplication.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkflowUserDao {
    @Query("SELECT * FROM workflow_users")
    suspend fun getAllUsers(): List<WorkflowUserEntity>
    
    @Query("SELECT * FROM workflow_users WHERE id = :id")
    suspend fun getUserById(id: String): WorkflowUserEntity?
    
    @Query("SELECT * FROM workflow_users WHERE email = :email COLLATE NOCASE")
    suspend fun getUserByEmail(email: String): WorkflowUserEntity?
    
    @Query("SELECT * FROM workflow_users WHERE telegramUserId = :telegramId")
    suspend fun getUserByTelegramId(telegramId: Long): WorkflowUserEntity?
    
    @Query("SELECT * FROM workflow_users WHERE email LIKE '%' || :query || '%' COLLATE NOCASE OR displayName LIKE '%' || :query || '%' COLLATE NOCASE OR telegramUsername LIKE '%' || :query || '%' COLLATE NOCASE")
    suspend fun searchUsers(query: String): List<WorkflowUserEntity>
    
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(user: WorkflowUserEntity)
    
    @Update
    suspend fun updateUser(user: WorkflowUserEntity)
    
    @Delete
    suspend fun deleteUser(user: WorkflowUserEntity)
    
    @Query("DELETE FROM workflow_users WHERE id = :userId")
    suspend fun deleteUserById(userId: String)
}

@Dao
interface WorkflowDao {
    @Query("SELECT * FROM workflows")
    suspend fun getAllWorkflows(): List<WorkflowEntity>
    
    @Query("SELECT * FROM workflows WHERE id = :id")
    suspend fun getWorkflowById(id: String): WorkflowEntity?
    
    @Query("SELECT * FROM workflows WHERE createdBy = :userId")
    suspend fun getWorkflowsByUser(userId: String): List<WorkflowEntity>
    
    @Query("SELECT * FROM workflows WHERE sharedWith LIKE '%\"' || :userId || '\"%' AND createdBy != :userId")
    suspend fun getSharedWorkflows(userId: String): List<WorkflowEntity>
    
    @Query("SELECT * FROM workflows WHERE isPublic = 1")
    suspend fun getPublicWorkflows(): List<WorkflowEntity>
    
    @Query("SELECT * FROM workflows WHERE workflowType = :type")
    suspend fun getWorkflowsByType(type: String): List<WorkflowEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkflow(workflow: WorkflowEntity)
    
    @Update
    suspend fun updateWorkflow(workflow: WorkflowEntity)
    
    @Delete
    suspend fun deleteWorkflow(workflow: WorkflowEntity)
    
    @Query("DELETE FROM workflows WHERE id = :workflowId")
    suspend fun deleteWorkflowById(workflowId: String)
    
    @Query("SELECT COUNT(*) FROM workflows WHERE (createdBy = :userId OR sharedWith LIKE '%\"' || :userId || '\"%' OR isPublic = 1) AND id = :workflowId")
    suspend fun checkUserAccess(userId: String, workflowId: String): Int
}

@Dao
interface WorkflowExecutionDao {
    @Query("SELECT * FROM workflow_executions WHERE executionId = :executionId")
    suspend fun getExecutionById(executionId: String): WorkflowExecutionEntity?
    
    @Query("SELECT * FROM workflow_executions WHERE workflowId = :workflowId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getExecutionHistory(workflowId: String, limit: Int): List<WorkflowExecutionEntity>
    
    @Query("SELECT * FROM workflow_executions ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getAllExecutions(limit: Int): List<WorkflowExecutionEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExecution(execution: WorkflowExecutionEntity)
    
    @Query("DELETE FROM workflow_executions WHERE timestamp < :cutoffTime")
    suspend fun deleteOldExecutions(cutoffTime: Long)
    
    @Query("SELECT COUNT(*) FROM workflow_executions WHERE workflowId = :workflowId AND success = 1")
    suspend fun getSuccessfulExecutionCount(workflowId: String): Int
    
    @Query("SELECT COUNT(*) FROM workflow_executions WHERE workflowId = :workflowId AND success = 0")
    suspend fun getFailedExecutionCount(workflowId: String): Int
}