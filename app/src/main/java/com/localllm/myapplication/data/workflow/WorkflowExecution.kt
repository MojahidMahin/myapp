package com.localllm.myapplication.data.workflow

/**
 * Represents a single execution of a workflow
 */
data class WorkflowExecution(
    val executionId: String,
    val workflowId: String,
    val triggeredBy: String,
    val executionTime: Long,
    val success: Boolean,
    val errorMessage: String? = null,
    val resultData: Map<String, Any> = emptyMap()
)