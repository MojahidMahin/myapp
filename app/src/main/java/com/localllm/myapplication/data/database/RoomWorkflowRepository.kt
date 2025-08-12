package com.localllm.myapplication.data.database

import android.content.Context
import com.localllm.myapplication.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Room-based implementation of WorkflowRepository
 */
class RoomWorkflowRepository(context: Context) : WorkflowRepository {
    private val database = WorkflowDatabase.getDatabase(context)
    private val workflowDao = database.workflowDao()
    
    override suspend fun getAllWorkflows(): Result<List<Workflow>> = withContext(Dispatchers.IO) {
        try {
            val entities = workflowDao.getAllWorkflows()
            val workflows = entities.map { it.toMultiUserWorkflow() }
            Result.success(workflows)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getWorkflowById(id: String): Result<Workflow?> = withContext(Dispatchers.IO) {
        try {
            val entity = workflowDao.getWorkflowById(id)
            val workflow = entity?.toMultiUserWorkflow()
            Result.success(workflow)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun saveWorkflow(workflow: Workflow): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val multiUserWorkflow = workflow as MultiUserWorkflow
            val entity = multiUserWorkflow.toEntity()
            workflowDao.insertWorkflow(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun deleteWorkflow(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            workflowDao.deleteWorkflowById(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun updateWorkflow(workflow: Workflow): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val multiUserWorkflow = workflow as MultiUserWorkflow
            val entity = multiUserWorkflow.toEntity()
            workflowDao.updateWorkflow(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getWorkflowsByUser(userId: String): Result<List<Workflow>> = withContext(Dispatchers.IO) {
        try {
            val entities = workflowDao.getWorkflowsByUser(userId)
            val workflows = entities.map { it.toMultiUserWorkflow() }
            Result.success(workflows)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getSharedWorkflows(userId: String): Result<List<Workflow>> = withContext(Dispatchers.IO) {
        try {
            val entities = workflowDao.getSharedWorkflows(userId)
            val workflows = entities.map { it.toMultiUserWorkflow() }
            Result.success(workflows)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getPublicWorkflows(): Result<List<Workflow>> = withContext(Dispatchers.IO) {
        try {
            val entities = workflowDao.getPublicWorkflows()
            val workflows = entities.map { it.toMultiUserWorkflow() }
            Result.success(workflows)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun shareWorkflow(workflowId: String, targetUserId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val entity = workflowDao.getWorkflowById(workflowId)
                ?: return@withContext Result.failure(Exception("Workflow not found"))
            
            val workflow = entity.toMultiUserWorkflow()
            val updatedWorkflow = workflow.copy(
                sharedWith = workflow.sharedWith + targetUserId,
                updatedAt = System.currentTimeMillis()
            )
            
            workflowDao.updateWorkflow(updatedWorkflow.toEntity())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun unshareWorkflow(workflowId: String, targetUserId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val entity = workflowDao.getWorkflowById(workflowId)
                ?: return@withContext Result.failure(Exception("Workflow not found"))
            
            val workflow = entity.toMultiUserWorkflow()
            val updatedWorkflow = workflow.copy(
                sharedWith = workflow.sharedWith - targetUserId,
                updatedAt = System.currentTimeMillis()
            )
            
            workflowDao.updateWorkflow(updatedWorkflow.toEntity())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getWorkflowsByType(type: WorkflowType): Result<List<Workflow>> = withContext(Dispatchers.IO) {
        try {
            val entities = workflowDao.getWorkflowsByType(type.name)
            val workflows = entities.map { it.toMultiUserWorkflow() }
            Result.success(workflows)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun hasPermission(userId: String, workflowId: String, permission: Permission): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val entity = workflowDao.getWorkflowById(workflowId)
                ?: return@withContext Result.success(false)
            
            val workflow = entity.toMultiUserWorkflow()
            
            val hasPermission = when {
                workflow.createdBy == userId -> true  // Creator has all permissions
                workflow.isPublic && permission == Permission.VIEW_WORKFLOW -> true
                workflow.sharedWith.contains(userId) -> {
                    when (permission) {
                        Permission.VIEW_WORKFLOW, Permission.EXECUTE_WORKFLOW -> true
                        Permission.EDIT_WORKFLOW -> workflow.permissions.canModify.contains(userId)
                        Permission.DELETE_WORKFLOW -> workflow.createdBy == userId
                        else -> false
                    }
                }
                else -> false
            }
            
            Result.success(hasPermission)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getUsersWithAccess(workflowId: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val entity = workflowDao.getWorkflowById(workflowId)
                ?: return@withContext Result.failure(Exception("Workflow not found"))
            
            val workflow = entity.toMultiUserWorkflow()
            val usersWithAccess = mutableListOf<String>().apply {
                add(workflow.createdBy)  // Creator always has access
                addAll(workflow.sharedWith)
            }.distinct()
            
            Result.success(usersWithAccess)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Room-based implementation of UserRepository
 */
class RoomUserRepository(context: Context) : UserRepository {
    private val database = WorkflowDatabase.getDatabase(context)
    private val userDao = database.workflowUserDao()
    
    override suspend fun createUser(user: WorkflowUser): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val entity = user.toEntity()
            userDao.insertUser(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getUserById(id: String): Result<WorkflowUser?> = withContext(Dispatchers.IO) {
        try {
            val entity = userDao.getUserById(id)
            val user = entity?.toWorkflowUser()
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getUserByEmail(email: String): Result<WorkflowUser?> = withContext(Dispatchers.IO) {
        try {
            val entity = userDao.getUserByEmail(email)
            val user = entity?.toWorkflowUser()
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getUserByTelegramId(telegramId: Long): Result<WorkflowUser?> = withContext(Dispatchers.IO) {
        try {
            val entity = userDao.getUserByTelegramId(telegramId)
            val user = entity?.toWorkflowUser()
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun updateUser(user: WorkflowUser): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val entity = user.toEntity()
            userDao.updateUser(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun deleteUser(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            userDao.deleteUserById(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun searchUsers(query: String): Result<List<WorkflowUser>> = withContext(Dispatchers.IO) {
        try {
            val entities = userDao.searchUsers(query)
            val users = entities.map { it.toWorkflowUser() }
            Result.success(users)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getAllUsers(): Result<List<WorkflowUser>> = withContext(Dispatchers.IO) {
        try {
            val entities = userDao.getAllUsers()
            val users = entities.map { it.toWorkflowUser() }
            Result.success(users)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Room-based implementation of WorkflowExecutionRepository
 */
class RoomWorkflowExecutionRepository(context: Context) : WorkflowExecutionRepository {
    private val database = WorkflowDatabase.getDatabase(context)
    private val executionDao = database.workflowExecutionDao()
    
    override suspend fun saveExecution(execution: WorkflowExecutionResult): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val entity = execution.toEntity()
            executionDao.insertExecution(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getExecutionHistory(workflowId: String, limit: Int): Result<List<WorkflowExecutionResult>> = withContext(Dispatchers.IO) {
        try {
            val entities = executionDao.getExecutionHistory(workflowId, limit)
            val executions = entities.map { it.toWorkflowExecutionResult() }
            Result.success(executions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getUserExecutionHistory(userId: String, limit: Int): Result<List<WorkflowExecutionResult>> = withContext(Dispatchers.IO) {
        try {
            // Note: This would require additional tracking of userId in executions
            // For now, return all executions
            val entities = executionDao.getAllExecutions(limit)
            val executions = entities.map { it.toWorkflowExecutionResult() }
            Result.success(executions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getExecutionById(executionId: String): Result<WorkflowExecutionResult?> = withContext(Dispatchers.IO) {
        try {
            val entity = executionDao.getExecutionById(executionId)
            val execution = entity?.toWorkflowExecutionResult()
            Result.success(execution)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun deleteOldExecutions(olderThanDays: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val cutoffTime = System.currentTimeMillis() - (olderThanDays * 24 * 60 * 60 * 1000L)
            executionDao.deleteOldExecutions(cutoffTime)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getAllExecutions(limit: Int): Result<List<WorkflowExecutionResult>> = withContext(Dispatchers.IO) {
        try {
            val entities = executionDao.getAllExecutions(limit)
            val executions = entities.map { it.toWorkflowExecutionResult() }
            Result.success(executions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}