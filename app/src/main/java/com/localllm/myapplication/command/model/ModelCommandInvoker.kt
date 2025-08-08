package com.localllm.myapplication.command.model

import android.util.Log
import kotlinx.coroutines.*
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

/**
 * Command Invoker for model operations
 * Following Command Pattern - encapsulates requests as objects
 * Also implements Single Responsibility Principle - only handles command execution
 */
class ModelCommandInvoker {
    
    companion object {
        private const val TAG = "ModelCommandInvoker"
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commandHistory = ArrayDeque<ModelCommand<*>>()
    private val maxHistorySize = 50
    private val activeJobs = ConcurrentHashMap<String, Job>()
    
    /**
     * Execute a command asynchronously and return command ID for cancellation
     */
    fun <T> executeAsync(
        command: ModelCommand<T>,
        onSuccess: ((T) -> Unit)? = null,
        onFailure: ((Throwable) -> Unit)? = null
    ): String {
        val commandId = generateCommandId()
        val job = scope.launch {
            try {
                executeCommand(command, onSuccess, onFailure)
            } catch (e: CancellationException) {
                Log.d(TAG, "Command cancelled: ${command.getDescription()}")
                onFailure?.invoke(InterruptedException("Command was cancelled"))
            } finally {
                activeJobs.remove(commandId)
            }
        }
        activeJobs[commandId] = job
        return commandId
    }
    
    /**
     * Cancel a command by ID
     */
    fun cancelCommand(commandId: String): Boolean {
        val job = activeJobs[commandId]
        return if (job != null && job.isActive) {
            job.cancel()
            activeJobs.remove(commandId)
            Log.d(TAG, "Command cancelled: $commandId")
            true
        } else {
            false
        }
    }
    
    /**
     * Execute a command synchronously
     */
    suspend fun <T> execute(command: ModelCommand<T>): Result<T> {
        return try {
            if (!command.canExecute()) {
                Result.failure(IllegalStateException("Command cannot be executed: ${command.getDescription()}"))
            } else {
                Log.d(TAG, "Executing command: ${command.getDescription()}")
                val result = command.execute()
                addToHistory(command)
                result
            }
        } catch (e: Exception) {
            Log.e(TAG, "Command execution failed: ${command.getDescription()}", e)
            Result.failure(e)
        }
    }
    
    private suspend fun <T> executeCommand(
        command: ModelCommand<T>,
        onSuccess: ((T) -> Unit)? = null,
        onFailure: ((Throwable) -> Unit)? = null
    ) {
        try {
            val result = execute(command)
            result.fold(
                onSuccess = { value -> onSuccess?.invoke(value) },
                onFailure = { error -> onFailure?.invoke(error) }
            )
        } catch (e: Exception) {
            onFailure?.invoke(e)
        }
    }
    
    private fun addToHistory(command: ModelCommand<*>) {
        commandHistory.addLast(command)
        if (commandHistory.size > maxHistorySize) {
            commandHistory.removeFirst()
        }
        Log.d(TAG, "Command completed: ${command.getDescription()}")
    }
    
    fun getCommandHistory(): List<String> {
        return commandHistory.map { it.getDescription() }
    }
    
    fun clearHistory() {
        commandHistory.clear()
    }
    
    /**
     * Cancel all active commands
     */
    fun cancelAllCommands() {
        activeJobs.values.forEach { job ->
            if (job.isActive) {
                job.cancel()
            }
        }
        activeJobs.clear()
    }
    
    /**
     * Get count of active commands
     */
    fun getActiveCommandCount(): Int = activeJobs.size
    
    private fun generateCommandId(): String {
        return "cmd_${System.currentTimeMillis()}_${(Math.random() * 1000).toInt()}"
    }
    
    fun cleanup() {
        cancelAllCommands()
        scope.cancel()
    }
}