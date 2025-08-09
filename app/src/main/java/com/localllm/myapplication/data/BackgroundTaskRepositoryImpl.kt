package com.localllm.myapplication.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Implementation of BackgroundTaskRepository using SharedPreferences for persistence
 * Following Single Responsibility Principle - only handles background task persistence
 * Following Dependency Inversion Principle - implements abstract interface
 */
class BackgroundTaskRepositoryImpl(
    private val context: Context
) : BackgroundTaskRepository {
    
    companion object {
        private const val TAG = "BackgroundTaskRepository"
        private const val PREFS_NAME = "background_tasks_prefs"
        private const val KEY_TASKS = "tasks"
        private const val KEY_CURRENT_SESSION = "current_session"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val mutex = Mutex()
    
    // In-memory cache for better performance
    private val _tasksFlow = MutableStateFlow<List<BackgroundTask>>(emptyList())
    private val tasksFlow = _tasksFlow.asStateFlow()
    
    init {
        // Load tasks from persistence on initialization
        loadTasksFromPrefs()
    }
    
    override suspend fun addTask(task: BackgroundTask): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            mutex.withLock {
                val currentTasks = _tasksFlow.value.toMutableList()
                currentTasks.add(task)
                
                // Save to persistent storage
                saveTasksToPrefs(currentTasks)
                
                // Update in-memory cache
                _tasksFlow.value = currentTasks
                
                Log.d(TAG, "Task added successfully: ${task.id}")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add task", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getNextTask(): Result<BackgroundTask?> = withContext(Dispatchers.IO) {
        return@withContext try {
            mutex.withLock {
                val pendingTasks = _tasksFlow.value.filter { 
                    it.status == TaskStatus.PENDING && 
                    it.scheduledTime <= System.currentTimeMillis() 
                }
                
                // Sort by priority and scheduled time
                val nextTask = pendingTasks
                    .sortedWith(compareBy<BackgroundTask> { it.priority.ordinal }
                        .thenBy { it.scheduledTime })
                    .firstOrNull()
                
                Log.d(TAG, "Next task retrieved: ${nextTask?.id}")
                Result.success(nextTask)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get next task", e)
            Result.failure(e)
        }
    }
    
    override suspend fun updateTaskStatus(taskId: String, status: TaskStatus): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            mutex.withLock {
                val currentTasks = _tasksFlow.value.toMutableList()
                val taskIndex = currentTasks.indexOfFirst { it.id == taskId }
                
                if (taskIndex != -1) {
                    val updatedTask = currentTasks[taskIndex].copy(status = status)
                    currentTasks[taskIndex] = updatedTask
                    
                    // Save to persistent storage
                    saveTasksToPrefs(currentTasks)
                    
                    // Update in-memory cache
                    _tasksFlow.value = currentTasks
                    
                    Log.d(TAG, "Task status updated: $taskId -> $status")
                    Result.success(Unit)
                } else {
                    val error = Exception("Task not found: $taskId")
                    Log.e(TAG, "Task not found for status update: $taskId")
                    Result.failure(error)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update task status", e)
            Result.failure(e)
        }
    }
    
    override suspend fun markTaskCompleted(taskId: String, result: String): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            mutex.withLock {
                val currentTasks = _tasksFlow.value.toMutableList()
                val taskIndex = currentTasks.indexOfFirst { it.id == taskId }
                
                if (taskIndex != -1) {
                    val updatedTask = currentTasks[taskIndex].copy(
                        status = TaskStatus.COMPLETED,
                        metadata = currentTasks[taskIndex].metadata + ("result" to result)
                    )
                    currentTasks[taskIndex] = updatedTask
                    
                    // Save to persistent storage
                    saveTasksToPrefs(currentTasks)
                    
                    // Update in-memory cache
                    _tasksFlow.value = currentTasks
                    
                    Log.d(TAG, "Task marked as completed: $taskId")
                    Result.success(Unit)
                } else {
                    val error = Exception("Task not found: $taskId")
                    Log.e(TAG, "Task not found for completion: $taskId")
                    Result.failure(error)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark task as completed", e)
            Result.failure(e)
        }
    }
    
    override suspend fun markTaskFailed(taskId: String, error: String): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            mutex.withLock {
                val currentTasks = _tasksFlow.value.toMutableList()
                val taskIndex = currentTasks.indexOfFirst { it.id == taskId }
                
                if (taskIndex != -1) {
                    val task = currentTasks[taskIndex]
                    val shouldRetry = task.currentRetries < task.maxRetries
                    
                    val updatedTask = if (shouldRetry) {
                        task.copy(
                            status = TaskStatus.RETRY,
                            currentRetries = task.currentRetries + 1,
                            metadata = task.metadata + ("lastError" to error)
                        )
                    } else {
                        task.copy(
                            status = TaskStatus.FAILED,
                            metadata = task.metadata + ("finalError" to error)
                        )
                    }
                    
                    currentTasks[taskIndex] = updatedTask
                    
                    // Save to persistent storage
                    saveTasksToPrefs(currentTasks)
                    
                    // Update in-memory cache
                    _tasksFlow.value = currentTasks
                    
                    Log.d(TAG, "Task marked as ${updatedTask.status}: $taskId")
                    Result.success(Unit)
                } else {
                    val taskError = Exception("Task not found: $taskId")
                    Log.e(TAG, "Task not found for failure marking: $taskId")
                    Result.failure(taskError)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark task as failed", e)
            Result.failure(e)
        }
    }
    
    override suspend fun retryTask(taskId: String): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            mutex.withLock {
                val currentTasks = _tasksFlow.value.toMutableList()
                val taskIndex = currentTasks.indexOfFirst { it.id == taskId }
                
                if (taskIndex != -1) {
                    val task = currentTasks[taskIndex]
                    if (task.currentRetries < task.maxRetries) {
                        val updatedTask = task.copy(
                            status = TaskStatus.PENDING,
                            scheduledTime = System.currentTimeMillis() + 60000 // Retry after 1 minute
                        )
                        currentTasks[taskIndex] = updatedTask
                        
                        // Save to persistent storage
                        saveTasksToPrefs(currentTasks)
                        
                        // Update in-memory cache
                        _tasksFlow.value = currentTasks
                        
                        Log.d(TAG, "Task scheduled for retry: $taskId")
                        Result.success(Unit)
                    } else {
                        val error = Exception("Task has exceeded max retries: $taskId")
                        Log.e(TAG, "Task retry failed - max retries exceeded: $taskId")
                        Result.failure(error)
                    }
                } else {
                    val error = Exception("Task not found: $taskId")
                    Log.e(TAG, "Task not found for retry: $taskId")
                    Result.failure(error)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retry task", e)
            Result.failure(e)
        }
    }
    
    override suspend fun cancelTask(taskId: String): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            mutex.withLock {
                val currentTasks = _tasksFlow.value.toMutableList()
                val taskIndex = currentTasks.indexOfFirst { it.id == taskId }
                
                if (taskIndex != -1) {
                    val updatedTask = currentTasks[taskIndex].copy(status = TaskStatus.CANCELLED)
                    currentTasks[taskIndex] = updatedTask
                    
                    // Save to persistent storage
                    saveTasksToPrefs(currentTasks)
                    
                    // Update in-memory cache
                    _tasksFlow.value = currentTasks
                    
                    Log.d(TAG, "Task cancelled: $taskId")
                    Result.success(Unit)
                } else {
                    val error = Exception("Task not found: $taskId")
                    Log.e(TAG, "Task not found for cancellation: $taskId")
                    Result.failure(error)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel task", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getAllTasks(): Result<List<BackgroundTask>> = withContext(Dispatchers.IO) {
        return@withContext try {
            Result.success(_tasksFlow.value.toList())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get all tasks", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getPendingTasks(): Result<List<BackgroundTask>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val pendingTasks = _tasksFlow.value.filter { 
                it.status == TaskStatus.PENDING || it.status == TaskStatus.RETRY
            }
            Result.success(pendingTasks)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get pending tasks", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getTasksByType(type: TaskType): Result<List<BackgroundTask>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val filteredTasks = _tasksFlow.value.filter { it.type == type }
            Result.success(filteredTasks)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get tasks by type", e)
            Result.failure(e)
        }
    }
    
    override suspend fun clearCompletedTasks(): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            mutex.withLock {
                val activeTasks = _tasksFlow.value.filter { 
                    it.status != TaskStatus.COMPLETED && it.status != TaskStatus.FAILED 
                }
                
                // Save to persistent storage
                saveTasksToPrefs(activeTasks)
                
                // Update in-memory cache
                _tasksFlow.value = activeTasks
                
                Log.d(TAG, "Completed tasks cleared")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear completed tasks", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getTaskCount(): Result<Int> = withContext(Dispatchers.IO) {
        return@withContext try {
            Result.success(_tasksFlow.value.size)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get task count", e)
            Result.failure(e)
        }
    }
    
    // Reactive flows
    override fun getPendingTasksFlow(): Flow<List<BackgroundTask>> {
        return tasksFlow.map { tasks ->
            tasks.filter { it.status == TaskStatus.PENDING || it.status == TaskStatus.RETRY }
        }
    }
    
    override fun getTaskStatusFlow(taskId: String): Flow<TaskStatus?> {
        return tasksFlow.map { tasks ->
            tasks.find { it.id == taskId }?.status
        }
    }
    
    // Private helper methods
    private fun loadTasksFromPrefs() {
        try {
            val tasksJson = prefs.getString(KEY_TASKS, "[]")
            val listType = object : TypeToken<List<BackgroundTask>>() {}.type
            val tasks = gson.fromJson<List<BackgroundTask>>(tasksJson, listType) ?: emptyList()
            _tasksFlow.value = tasks
            Log.d(TAG, "Loaded ${tasks.size} tasks from preferences")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load tasks from preferences", e)
            _tasksFlow.value = emptyList()
        }
    }
    
    private fun saveTasksToPrefs(tasks: List<BackgroundTask>) {
        try {
            val tasksJson = gson.toJson(tasks)
            prefs.edit()
                .putString(KEY_TASKS, tasksJson)
                .apply()
            Log.d(TAG, "Saved ${tasks.size} tasks to preferences")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save tasks to preferences", e)
        }
    }
}