package com.localllm.myapplication.command.background

import android.util.Log
import com.localllm.myapplication.command.Command
import com.localllm.myapplication.data.TaskStatus
import com.localllm.myapplication.data.BackgroundTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Invoker for background processing commands
 * Following Command Pattern and Single Responsibility Principle
 * Manages execution of background processing commands asynchronously
 */
class BackgroundCommandInvoker {
    
    companion object {
        private const val TAG = "BackgroundCommandInvoker"
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val runningCommands = ConcurrentHashMap<String, Boolean>()
    private val commandHistory = mutableListOf<String>()
    
    /**
     * Execute a command that returns a value asynchronously
     */
    fun executeAsyncWithValue(
        command: GetTaskStatusCommand,
        onSuccess: ((TaskStatus?) -> Unit)? = null,
        onFailure: ((Throwable) -> Unit)? = null
    ): String {
        val commandId = UUID.randomUUID().toString()
        
        scope.launch {
            try {
                runningCommands[commandId] = true
                
                Log.d(TAG, "Executing status command: ${command::class.java.simpleName}")
                commandHistory.add("${System.currentTimeMillis()}: ${command::class.java.simpleName}")
                
                val result = command.executeAsync()
                
                Log.d(TAG, "Status command completed: ${command::class.java.simpleName}")
                onSuccess?.invoke(result)
                
            } catch (e: Exception) {
                Log.e(TAG, "Status command failed: ${command::class.java.simpleName}", e)
                onFailure?.invoke(e)
            } finally {
                runningCommands.remove(commandId)
            }
        }
        
        return commandId
    }
    
    /**
     * Execute a command that returns a list of tasks asynchronously
     */
    fun executeAsyncWithList(
        command: GetPendingTasksCommand,
        onSuccess: ((List<BackgroundTask>) -> Unit)? = null,
        onFailure: ((Throwable) -> Unit)? = null
    ): String {
        val commandId = UUID.randomUUID().toString()
        
        scope.launch {
            try {
                runningCommands[commandId] = true
                
                Log.d(TAG, "Executing pending tasks command: ${command::class.java.simpleName}")
                commandHistory.add("${System.currentTimeMillis()}: ${command::class.java.simpleName}")
                
                val result = command.executeAsync()
                
                Log.d(TAG, "Pending tasks command completed: ${command::class.java.simpleName}")
                onSuccess?.invoke(result)
                
            } catch (e: Exception) {
                Log.e(TAG, "Pending tasks command failed: ${command::class.java.simpleName}", e)
                onFailure?.invoke(e)
            } finally {
                runningCommands.remove(commandId)
            }
        }
        
        return commandId
    }
    
    /**
     * Execute a background processing command with Result wrapper
     * Following Command Pattern with Result handling
     */
    fun executeBackgroundCommand(
        command: BackgroundProcessingCommand,
        onResult: ((Result<Unit>) -> Unit)? = null
    ): String {
        val commandId = UUID.randomUUID().toString()
        
        scope.launch {
            try {
                runningCommands[commandId] = true
                
                Log.d(TAG, "Executing background processing command: ${command.javaClass.simpleName}")
                commandHistory.add("${System.currentTimeMillis()}: ${command.javaClass.simpleName}")
                
                val result = command.executeInBackground()
                
                Log.d(TAG, "Background processing command completed: ${command.javaClass.simpleName}")
                onResult?.invoke(result)
                
            } catch (e: Exception) {
                Log.e(TAG, "Background processing command failed: ${command.javaClass.simpleName}", e)
                onResult?.invoke(Result.failure(e))
            } finally {
                runningCommands.remove(commandId)
            }
        }
        
        return commandId
    }
    
    /**
     * Execute multiple background commands concurrently
     * Following Command Pattern with batch processing
     */
    fun executeBatch(
        commands: List<BackgroundProcessingCommand>,
        onBatchComplete: ((List<Result<Unit>>) -> Unit)? = null
    ): String {
        val batchId = UUID.randomUUID().toString()
        
        scope.launch {
            try {
                runningCommands[batchId] = true
                
                Log.d(TAG, "Executing batch of ${commands.size} background commands")
                commandHistory.add("${System.currentTimeMillis()}: Batch execution (${commands.size} commands)")
                
                val results = mutableListOf<Result<Unit>>()
                
                // Execute all commands concurrently
                commands.forEach { command ->
                    launch {
                        try {
                            val result = command.executeInBackground()
                            synchronized(results) {
                                results.add(result)
                            }
                        } catch (e: Exception) {
                            synchronized(results) {
                                results.add(Result.failure(e))
                            }
                        }
                    }
                }
                
                // Wait for all to complete (simplified approach)
                while (results.size < commands.size) {
                    kotlinx.coroutines.delay(100)
                }
                
                Log.d(TAG, "Batch execution completed: ${results.size} results")
                onBatchComplete?.invoke(results)
                
            } catch (e: Exception) {
                Log.e(TAG, "Batch execution failed", e)
                onBatchComplete?.invoke(emptyList())
            } finally {
                runningCommands.remove(batchId)
            }
        }
        
        return batchId
    }
    
    /**
     * Execute a command with retry logic
     * Following Retry Pattern combined with Command Pattern
     */
    fun executeWithRetry(
        command: BackgroundProcessingCommand,
        maxRetries: Int = 3,
        retryDelayMs: Long = 1000,
        onResult: ((Result<Unit>) -> Unit)? = null
    ): String {
        val commandId = UUID.randomUUID().toString()
        
        scope.launch {
            try {
                runningCommands[commandId] = true
                
                Log.d(TAG, "Executing command with retry: ${command.javaClass.simpleName} (max retries: $maxRetries)")
                commandHistory.add("${System.currentTimeMillis()}: ${command.javaClass.simpleName} (with retry)")
                
                var lastException: Exception? = null
                var attempt = 0
                
                while (attempt <= maxRetries) {
                    try {
                        val result = command.executeInBackground()
                        
                        if (result.isSuccess) {
                            Log.d(TAG, "Command succeeded on attempt ${attempt + 1}: ${command.javaClass.simpleName}")
                            onResult?.invoke(result)
                            return@launch
                        } else {
                            lastException = result.exceptionOrNull() as? Exception
                        }
                        
                    } catch (e: Exception) {
                        lastException = e
                        Log.w(TAG, "Command attempt ${attempt + 1} failed: ${command.javaClass.simpleName}", e)
                    }
                    
                    attempt++
                    
                    if (attempt <= maxRetries) {
                        Log.d(TAG, "Retrying in ${retryDelayMs}ms (attempt $attempt/$maxRetries)")
                        kotlinx.coroutines.delay(retryDelayMs)
                    }
                }
                
                Log.e(TAG, "Command failed after $maxRetries retries: ${command.javaClass.simpleName}")
                onResult?.invoke(Result.failure(lastException ?: Exception("Max retries exceeded")))
                
            } catch (e: Exception) {
                Log.e(TAG, "Command with retry failed: ${command.javaClass.simpleName}", e)
                onResult?.invoke(Result.failure(e))
            } finally {
                runningCommands.remove(commandId)
            }
        }
        
        return commandId
    }
    
    /**
     * Execute a command with timeout
     * Following Timeout Pattern combined with Command Pattern
     */
    fun executeWithTimeout(
        command: BackgroundProcessingCommand,
        timeoutMs: Long = 30000,
        onResult: ((Result<Unit>) -> Unit)? = null
    ): String {
        val commandId = UUID.randomUUID().toString()
        
        scope.launch {
            try {
                runningCommands[commandId] = true
                
                Log.d(TAG, "Executing command with timeout: ${command.javaClass.simpleName} (timeout: ${timeoutMs}ms)")
                commandHistory.add("${System.currentTimeMillis()}: ${command.javaClass.simpleName} (with timeout)")
                
                val timeoutJob = launch {
                    kotlinx.coroutines.delay(timeoutMs)
                    Log.w(TAG, "Command timed out: ${command.javaClass.simpleName}")
                    onResult?.invoke(Result.failure(Exception("Command execution timed out")))
                }
                
                val executionJob = launch {
                    try {
                        val result = command.executeInBackground()
                        timeoutJob.cancel()
                        
                        Log.d(TAG, "Command completed within timeout: ${command.javaClass.simpleName}")
                        onResult?.invoke(result)
                        
                    } catch (e: Exception) {
                        timeoutJob.cancel()
                        Log.e(TAG, "Command failed within timeout: ${command.javaClass.simpleName}", e)
                        onResult?.invoke(Result.failure(e))
                    }
                }
                
                // Wait for either completion or timeout
                executionJob.join()
                
            } catch (e: Exception) {
                Log.e(TAG, "Command with timeout failed: ${command.javaClass.simpleName}", e)
                onResult?.invoke(Result.failure(e))
            } finally {
                runningCommands.remove(commandId)
            }
        }
        
        return commandId
    }
    
    /**
     * Check if a command is currently running
     */
    fun isCommandRunning(commandId: String): Boolean {
        return runningCommands.containsKey(commandId)
    }
    
    /**
     * Get the number of currently running commands
     */
    fun getRunningCommandCount(): Int {
        return runningCommands.size
    }
    
    /**
     * Get command execution history
     */
    fun getCommandHistory(): List<String> {
        return commandHistory.toList()
    }
    
    /**
     * Clear command history
     */
    fun clearHistory() {
        commandHistory.clear()
        Log.d(TAG, "Command history cleared")
    }
    
    /**
     * Cancel all running commands (cleanup)
     */
    fun cleanup() {
        try {
            runningCommands.clear()
            commandHistory.clear()
            Log.d(TAG, "Background command invoker cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}