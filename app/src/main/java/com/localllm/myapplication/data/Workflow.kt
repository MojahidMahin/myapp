package com.localllm.myapplication.data

/**
 * Basic workflow interface
 */
interface Workflow {
    val id: String
    val name: String
    val description: String
    val isEnabled: Boolean
    val createdAt: Long
    val updatedAt: Long
}

/**
 * Basic workflow implementation
 */
data class WorkflowImpl(
    override val id: String,
    override val name: String,
    override val description: String = "",
    override val isEnabled: Boolean = true,
    override val createdAt: Long = System.currentTimeMillis(),
    override val updatedAt: Long = System.currentTimeMillis(),
    val triggers: List<WorkflowTrigger> = emptyList(),
    val actions: List<WorkflowAction> = emptyList(),
    val runInBackground: Boolean = false
) : Workflow

/**
 * Workflow trigger types
 */
sealed class WorkflowTrigger {
    data class ImageDetected(val watchPath: String) : WorkflowTrigger()
    data class FileChanged(val watchPath: String, val watchType: FileWatchType) : WorkflowTrigger()
    data class ScheduledTrigger(val cronExpression: String) : WorkflowTrigger()
    data class ManualTrigger(val triggerName: String) : WorkflowTrigger()
}

/**
 * File watch types
 */
enum class FileWatchType {
    CREATED, MODIFIED, DELETED, ALL
}

/**
 * Workflow action types
 */
sealed class WorkflowAction {
    data class NotificationAction(val message: String, val title: String = "Workflow") : WorkflowAction()
    data class ImageAnalysisAction(val analysisType: String, val outputPath: String? = null) : WorkflowAction()
    data class EmailAction(val to: String, val subject: String, val body: String) : WorkflowAction()
    data class LogAction(val message: String, val level: String = "INFO") : WorkflowAction()
}