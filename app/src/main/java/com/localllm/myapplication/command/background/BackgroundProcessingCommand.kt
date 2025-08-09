package com.localllm.myapplication.command.background

import android.util.Log
import com.localllm.myapplication.command.Command
import com.localllm.myapplication.data.*
import com.localllm.myapplication.scheduler.BackgroundTaskScheduler

/**
 * Base interface for background processing commands
 * Following Command Pattern and Interface Segregation Principle
 */
interface BackgroundProcessingCommand : Command {
    suspend fun executeInBackground(): Result<Unit>
}

/**
 * Command to schedule a background chat task
 * Following Single Responsibility Principle - only handles chat task scheduling
 */
class ScheduleChatTaskCommand(
    private val scheduler: BackgroundTaskScheduler,
    private val prompt: String,
    private val images: List<String> = emptyList(),
    private val priority: Priority = Priority.NORMAL,
    private val sessionId: String? = null
) : BackgroundProcessingCommand {
    
    companion object {
        private const val TAG = "ScheduleChatTaskCommand"
    }
    
    override fun execute() {
        throw UnsupportedOperationException("Use executeInBackground() instead")
    }
    
    override suspend fun executeInBackground(): Result<Unit> {
        return try {
            Log.d(TAG, "Scheduling chat task for background processing")
            
            val result = scheduler.scheduleChatTask(
                prompt = prompt,
                images = images,
                priority = priority,
                sessionId = sessionId
            )
            
            result.fold(
                onSuccess = { taskId ->
                    Log.d(TAG, "Chat task scheduled successfully: $taskId")
                    Result.success(Unit)
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to schedule chat task", error)
                    Result.failure(error)
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error executing ScheduleChatTaskCommand", e)
            Result.failure(e)
        }
    }
}

/**
 * Command to schedule a background image analysis task
 * Following Single Responsibility Principle - only handles image analysis scheduling
 */
class ScheduleImageAnalysisCommand(
    private val scheduler: BackgroundTaskScheduler,
    private val prompt: String,
    private val images: List<String>,
    private val priority: Priority = Priority.HIGH
) : BackgroundProcessingCommand {
    
    companion object {
        private const val TAG = "ScheduleImageAnalysisCommand"
    }
    
    override fun execute() {
        throw UnsupportedOperationException("Use executeInBackground() instead")
    }
    
    override suspend fun executeInBackground(): Result<Unit> {
        return try {
            Log.d(TAG, "Scheduling image analysis task for background processing")
            
            val result = scheduler.scheduleImageAnalysisTask(
                prompt = prompt,
                images = images,
                priority = priority
            )
            
            result.fold(
                onSuccess = { taskId ->
                    Log.d(TAG, "Image analysis task scheduled successfully: $taskId")
                    Result.success(Unit)
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to schedule image analysis task", error)
                    Result.failure(error)
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error executing ScheduleImageAnalysisCommand", e)
            Result.failure(e)
        }
    }
}

/**
 * Command to schedule a task for a specific time
 * Following Single Responsibility Principle - only handles timed task scheduling
 */
class ScheduleTimedTaskCommand(
    private val scheduler: BackgroundTaskScheduler,
    private val prompt: String,
    private val scheduledTime: Long,
    private val priority: Priority = Priority.NORMAL
) : BackgroundProcessingCommand {
    
    companion object {
        private const val TAG = "ScheduleTimedTaskCommand"
    }
    
    override fun execute() {
        throw UnsupportedOperationException("Use executeInBackground() instead")
    }
    
    override suspend fun executeInBackground(): Result<Unit> {
        return try {
            Log.d(TAG, "Scheduling timed task for background processing")
            
            val result = scheduler.scheduleTaskForTime(
                prompt = prompt,
                scheduledTime = scheduledTime,
                priority = priority
            )
            
            result.fold(
                onSuccess = { taskId ->
                    Log.d(TAG, "Timed task scheduled successfully: $taskId")
                    Result.success(Unit)
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to schedule timed task", error)
                    Result.failure(error)
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error executing ScheduleTimedTaskCommand", e)
            Result.failure(e)
        }
    }
}

/**
 * Command to cancel a background task
 * Following Single Responsibility Principle - only handles task cancellation
 */
class CancelBackgroundTaskCommand(
    private val scheduler: BackgroundTaskScheduler,
    private val taskId: String
) : BackgroundProcessingCommand {
    
    companion object {
        private const val TAG = "CancelBackgroundTaskCommand"
    }
    
    override fun execute() {
        throw UnsupportedOperationException("Use executeInBackground() instead")
    }
    
    override suspend fun executeInBackground(): Result<Unit> {
        return try {
            Log.d(TAG, "Cancelling background task: $taskId")
            
            val result = scheduler.cancelTask(taskId)
            
            result.fold(
                onSuccess = {
                    Log.d(TAG, "Background task cancelled successfully: $taskId")
                    Result.success(Unit)
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to cancel background task: $taskId", error)
                    Result.failure(error)
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error executing CancelBackgroundTaskCommand", e)
            Result.failure(e)
        }
    }
}

/**
 * Command to get background task status
 * Following Single Responsibility Principle - only handles status retrieval
 */
class GetTaskStatusCommand(
    private val scheduler: BackgroundTaskScheduler,
    private val taskId: String
) : Command {
    
    companion object {
        private const val TAG = "GetTaskStatusCommand"
    }
    
    override fun execute() {
        throw UnsupportedOperationException("This command returns a value, use executeAsync instead")
    }
    
    suspend fun executeAsync(): TaskStatus? {
        return try {
            Log.d(TAG, "Getting status for background task: $taskId")
            
            val result = scheduler.getTaskStatus(taskId)
            
            result.fold(
                onSuccess = { status ->
                    Log.d(TAG, "Task status retrieved: $taskId -> $status")
                    status
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to get task status: $taskId", error)
                    null
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error executing GetTaskStatusCommand", e)
            null
        }
    }
}

/**
 * Command to get all pending background tasks
 * Following Single Responsibility Principle - only handles pending task retrieval
 */
class GetPendingTasksCommand(
    private val scheduler: BackgroundTaskScheduler
) : Command {
    
    companion object {
        private const val TAG = "GetPendingTasksCommand"
    }
    
    override fun execute() {
        throw UnsupportedOperationException("This command returns a value, use executeAsync instead")
    }
    
    suspend fun executeAsync(): List<BackgroundTask> {
        return try {
            Log.d(TAG, "Getting all pending background tasks")
            
            val result = scheduler.getPendingTasks()
            
            result.fold(
                onSuccess = { tasks ->
                    Log.d(TAG, "Found ${tasks.size} pending tasks")
                    tasks
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to get pending tasks", error)
                    emptyList()
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error executing GetPendingTasksCommand", e)
            emptyList()
        }
    }
}

/**
 * Command to clear completed background tasks
 * Following Single Responsibility Principle - only handles completed task cleanup
 */
class ClearCompletedTasksCommand(
    private val scheduler: BackgroundTaskScheduler
) : BackgroundProcessingCommand {
    
    companion object {
        private const val TAG = "ClearCompletedTasksCommand"
    }
    
    override fun execute() {
        throw UnsupportedOperationException("Use executeInBackground() instead")
    }
    
    override suspend fun executeInBackground(): Result<Unit> {
        return try {
            Log.d(TAG, "Clearing completed background tasks")
            
            val result = scheduler.clearCompletedTasks()
            
            result.fold(
                onSuccess = {
                    Log.d(TAG, "Completed tasks cleared successfully")
                    Result.success(Unit)
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to clear completed tasks", error)
                    Result.failure(error)
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error executing ClearCompletedTasksCommand", e)
            Result.failure(e)
        }
    }
}

/**
 * Command to add a custom background task with specific parameters
 * Following Single Responsibility Principle - only handles custom task creation
 */
class AddCustomBackgroundTaskCommand(
    private val scheduler: BackgroundTaskScheduler,
    private val task: BackgroundTask
) : BackgroundProcessingCommand {
    
    companion object {
        private const val TAG = "AddCustomBackgroundTaskCommand"
    }
    
    override fun execute() {
        throw UnsupportedOperationException("Use executeInBackground() instead")
    }
    
    override suspend fun executeInBackground(): Result<Unit> {
        return try {
            Log.d(TAG, "Adding custom background task: ${task.type} - ${task.id}")
            
            val result = scheduler.addAndScheduleTask(task)
            
            result.fold(
                onSuccess = {
                    Log.d(TAG, "Custom background task added successfully: ${task.id}")
                    Result.success(Unit)
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to add custom background task: ${task.id}", error)
                    Result.failure(error)
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error executing AddCustomBackgroundTaskCommand", e)
            Result.failure(e)
        }
    }
}