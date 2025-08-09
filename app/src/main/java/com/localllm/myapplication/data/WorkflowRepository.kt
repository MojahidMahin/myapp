package com.localllm.myapplication.data

/**
 * Repository interface for workflow data access
 */
interface WorkflowRepository {
    suspend fun getAllWorkflows(): Result<List<Workflow>>
    suspend fun getWorkflowById(id: String): Result<Workflow?>
    suspend fun saveWorkflow(workflow: Workflow): Result<Unit>
    suspend fun deleteWorkflow(id: String): Result<Unit>
    suspend fun updateWorkflow(workflow: Workflow): Result<Unit>
}

/**
 * Basic in-memory implementation for testing/fallback
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
}