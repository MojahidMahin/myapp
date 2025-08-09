package com.localllm.myapplication.data

import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for background task operations
 * Following Dependency Inversion Principle - depend on abstractions
 * Following Interface Segregation Principle - focused interface for background tasks
 */
interface BackgroundTaskRepository {
    suspend fun addTask(task: BackgroundTask): Result<Unit>
    suspend fun getNextTask(): Result<BackgroundTask?>
    suspend fun updateTaskStatus(taskId: String, status: TaskStatus): Result<Unit>
    suspend fun markTaskCompleted(taskId: String, result: String): Result<Unit>
    suspend fun markTaskFailed(taskId: String, error: String): Result<Unit>
    suspend fun retryTask(taskId: String): Result<Unit>
    suspend fun cancelTask(taskId: String): Result<Unit>
    suspend fun getAllTasks(): Result<List<BackgroundTask>>
    suspend fun getPendingTasks(): Result<List<BackgroundTask>>
    suspend fun getTasksByType(type: TaskType): Result<List<BackgroundTask>>
    suspend fun clearCompletedTasks(): Result<Unit>
    suspend fun getTaskCount(): Result<Int>
    
    // Reactive flows
    fun getPendingTasksFlow(): Flow<List<BackgroundTask>>
    fun getTaskStatusFlow(taskId: String): Flow<TaskStatus?>
}