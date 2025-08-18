package com.localllm.myapplication.data

/**
 * Repository interface for multi-user workflow data access
 */
interface WorkflowRepository {
    // Basic workflow operations
    suspend fun getAllWorkflows(): Result<List<Workflow>>
    suspend fun getWorkflowById(id: String): Result<Workflow?>
    suspend fun saveWorkflow(workflow: Workflow): Result<Unit>
    suspend fun deleteWorkflow(id: String): Result<Unit>
    suspend fun updateWorkflow(workflow: Workflow): Result<Unit>
    
    // Multi-user specific operations
    suspend fun getWorkflowsByUser(userId: String): Result<List<Workflow>>
    suspend fun getSharedWorkflows(userId: String): Result<List<Workflow>>
    suspend fun getPublicWorkflows(): Result<List<Workflow>>
    suspend fun shareWorkflow(workflowId: String, targetUserId: String): Result<Unit>
    suspend fun unshareWorkflow(workflowId: String, targetUserId: String): Result<Unit>
    suspend fun getWorkflowsByType(type: WorkflowType): Result<List<Workflow>>
    
    // Permission checking
    suspend fun hasPermission(userId: String, workflowId: String, permission: Permission): Result<Boolean>
    suspend fun getUsersWithAccess(workflowId: String): Result<List<String>>
}

/**
 * User repository interface
 */
interface UserRepository {
    suspend fun createUser(user: WorkflowUser): Result<Unit>
    suspend fun getUserById(id: String): Result<WorkflowUser?>
    suspend fun getUserByEmail(email: String): Result<WorkflowUser?>
    suspend fun getUserByTelegramId(telegramId: Long): Result<WorkflowUser?>
    suspend fun updateUser(user: WorkflowUser): Result<Unit>
    suspend fun deleteUser(id: String): Result<Unit>
    suspend fun searchUsers(query: String): Result<List<WorkflowUser>>
    suspend fun getAllUsers(): Result<List<WorkflowUser>>
}

/**
 * Workflow execution history repository
 */
interface WorkflowExecutionRepository {
    suspend fun saveExecution(execution: WorkflowExecutionResult): Result<Unit>
    suspend fun getExecutionHistory(workflowId: String, limit: Int = 50): Result<List<WorkflowExecutionResult>>
    suspend fun getUserExecutionHistory(userId: String, limit: Int = 50): Result<List<WorkflowExecutionResult>>
    suspend fun getExecutionById(executionId: String): Result<WorkflowExecutionResult?>
    suspend fun deleteOldExecutions(olderThanDays: Int): Result<Unit>
    suspend fun getAllExecutions(limit: Int = 50): Result<List<WorkflowExecutionResult>>
}

/**
 * Enhanced in-memory implementation for multi-user workflows
 */
class InMemoryWorkflowRepository : WorkflowRepository {
    private val workflows = mutableMapOf<String, Workflow>()
    
    override suspend fun getAllWorkflows(): Result<List<Workflow>> {
        return Result.success(workflows.values.toList())
    }
    
    override suspend fun getWorkflowById(id: String): Result<Workflow?> {
        return Result.success(workflows[id])
    }
    
    override suspend fun saveWorkflow(workflow: Workflow): Result<Unit> {
        workflows[workflow.id] = workflow
        return Result.success(Unit)
    }
    
    override suspend fun deleteWorkflow(id: String): Result<Unit> {
        workflows.remove(id)
        return Result.success(Unit)
    }
    
    override suspend fun updateWorkflow(workflow: Workflow): Result<Unit> {
        workflows[workflow.id] = workflow
        return Result.success(Unit)
    }
    
    override suspend fun getWorkflowsByUser(userId: String): Result<List<Workflow>> {
        val userWorkflows = workflows.values.filter { workflow ->
            workflow.createdBy == userId
        }
        return Result.success(userWorkflows)
    }
    
    override suspend fun getSharedWorkflows(userId: String): Result<List<Workflow>> {
        val sharedWorkflows = workflows.values.filterIsInstance<MultiUserWorkflow>().filter { workflow ->
            workflow.sharedWith.contains(userId) && workflow.createdBy != userId
        }
        return Result.success(sharedWorkflows)
    }
    
    override suspend fun getPublicWorkflows(): Result<List<Workflow>> {
        val publicWorkflows = workflows.values.filterIsInstance<MultiUserWorkflow>().filter { it.isPublic }
        return Result.success(publicWorkflows)
    }
    
    override suspend fun shareWorkflow(workflowId: String, targetUserId: String): Result<Unit> {
        val workflow = workflows[workflowId] as? MultiUserWorkflow
            ?: return Result.failure(Exception("Workflow not found or not shareable"))
        
        val updatedWorkflow = workflow.copy(
            sharedWith = workflow.sharedWith + targetUserId,
            updatedAt = System.currentTimeMillis()
        )
        workflows[workflowId] = updatedWorkflow
        return Result.success(Unit)
    }
    
    override suspend fun unshareWorkflow(workflowId: String, targetUserId: String): Result<Unit> {
        val workflow = workflows[workflowId] as? MultiUserWorkflow
            ?: return Result.failure(Exception("Workflow not found"))
        
        val updatedWorkflow = workflow.copy(
            sharedWith = workflow.sharedWith - targetUserId,
            updatedAt = System.currentTimeMillis()
        )
        workflows[workflowId] = updatedWorkflow
        return Result.success(Unit)
    }
    
    override suspend fun getWorkflowsByType(type: WorkflowType): Result<List<Workflow>> {
        val filteredWorkflows = workflows.values.filter { it.workflowType == type }
        return Result.success(filteredWorkflows)
    }
    
    override suspend fun hasPermission(userId: String, workflowId: String, permission: Permission): Result<Boolean> {
        val workflow = workflows[workflowId] as? MultiUserWorkflow
            ?: return Result.success(false)
        
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
        
        return Result.success(hasPermission)
    }
    
    override suspend fun getUsersWithAccess(workflowId: String): Result<List<String>> {
        val workflow = workflows[workflowId] as? MultiUserWorkflow
            ?: return Result.failure(Exception("Workflow not found"))
        
        val usersWithAccess = mutableListOf<String>().apply {
            add(workflow.createdBy)  // Creator always has access
            addAll(workflow.sharedWith)
        }.distinct()
        
        return Result.success(usersWithAccess)
    }
}

/**
 * In-memory user repository implementation
 */
class InMemoryUserRepository : UserRepository {
    private val users = mutableMapOf<String, WorkflowUser>()
    
    override suspend fun createUser(user: WorkflowUser): Result<Unit> {
        if (users.containsKey(user.id) || users.values.any { it.email == user.email }) {
            return Result.failure(Exception("User already exists"))
        }
        users[user.id] = user
        return Result.success(Unit)
    }
    
    override suspend fun getUserById(id: String): Result<WorkflowUser?> {
        return Result.success(users[id])
    }
    
    override suspend fun getUserByEmail(email: String): Result<WorkflowUser?> {
        val user = users.values.find { it.email.equals(email, ignoreCase = true) }
        return Result.success(user)
    }
    
    override suspend fun getUserByTelegramId(telegramId: Long): Result<WorkflowUser?> {
        val user = users.values.find { it.telegramUserId == telegramId }
        return Result.success(user)
    }
    
    override suspend fun updateUser(user: WorkflowUser): Result<Unit> {
        if (!users.containsKey(user.id)) {
            return Result.failure(Exception("User not found"))
        }
        users[user.id] = user
        return Result.success(Unit)
    }
    
    override suspend fun deleteUser(id: String): Result<Unit> {
        users.remove(id)
        return Result.success(Unit)
    }
    
    override suspend fun searchUsers(query: String): Result<List<WorkflowUser>> {
        val searchResults = users.values.filter { user ->
            user.email.contains(query, ignoreCase = true) ||
            user.displayName.contains(query, ignoreCase = true) ||
            user.telegramUsername?.contains(query, ignoreCase = true) == true
        }
        return Result.success(searchResults)
    }
    
    override suspend fun getAllUsers(): Result<List<WorkflowUser>> {
        return Result.success(users.values.toList())
    }
}

/**
 * In-memory workflow execution repository implementation
 */
class InMemoryWorkflowExecutionRepository : WorkflowExecutionRepository {
    private val executions = mutableMapOf<String, WorkflowExecutionResult>()
    
    override suspend fun saveExecution(execution: WorkflowExecutionResult): Result<Unit> {
        executions[execution.executionId] = execution
        return Result.success(Unit)
    }
    
    override suspend fun getExecutionHistory(workflowId: String, limit: Int): Result<List<WorkflowExecutionResult>> {
        val history = executions.values
            .filter { it.workflowId == workflowId }
            .sortedByDescending { it.timestamp }
            .take(limit)
        return Result.success(history)
    }
    
    override suspend fun getUserExecutionHistory(userId: String, limit: Int): Result<List<WorkflowExecutionResult>> {
        // Note: We'd need to track userId in WorkflowExecutionResult for this to work properly
        // For now, return empty list
        return Result.success(emptyList())
    }
    
    override suspend fun getExecutionById(executionId: String): Result<WorkflowExecutionResult?> {
        return Result.success(executions[executionId])
    }
    
    override suspend fun deleteOldExecutions(olderThanDays: Int): Result<Unit> {
        val cutoffTime = System.currentTimeMillis() - (olderThanDays * 24 * 60 * 60 * 1000L)
        val toRemove = executions.values.filter { it.timestamp < cutoffTime }.map { it.executionId }
        toRemove.forEach { executions.remove(it) }
        return Result.success(Unit)
    }
    
    override suspend fun getAllExecutions(limit: Int): Result<List<WorkflowExecutionResult>> {
        val allExecutions = executions.values
            .sortedByDescending { it.timestamp }
            .take(limit)
        return Result.success(allExecutions)
    }
}

/**
 * Extension functions to initialize sample data
 */
fun InMemoryWorkflowRepository.initializeSampleWorkflows() {
    // No default workflows - users create their own
}

fun InMemoryUserRepository.initializeSampleUsers() {
    val sampleUsers = listOf(
        WorkflowUser(
            id = "user_1",
            email = "user@example.com",
            displayName = "Demo User",
            telegramUserId = null,  // No fake Telegram ID
            telegramUsername = null,
            telegramConnected = false,  // Will be set to true when real bot connects
            createdAt = System.currentTimeMillis()
        ),
        WorkflowUser(
            id = "user_2", 
            email = "manager@example.com",
            displayName = "Manager",
            telegramUserId = 987654321L,
            telegramUsername = "manager_user",
            createdAt = System.currentTimeMillis()
        )
    )
    
    sampleUsers.forEach { user ->
        kotlinx.coroutines.runBlocking {
            createUser(user)
        }
    }
}