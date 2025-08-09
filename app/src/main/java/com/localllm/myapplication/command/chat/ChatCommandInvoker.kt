package com.localllm.myapplication.command.chat

import android.util.Log
import kotlinx.coroutines.*
import java.util.ArrayDeque

/**
 * Command Invoker for chat operations
 * Following Command Pattern and Single Responsibility Principle
 */
class ChatCommandInvoker {
    
    companion object {
        private const val TAG = "ChatCommandInvoker"
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commandHistory = ArrayDeque<ChatCommand<*>>()
    private val maxHistorySize = 50
    
    /**
     * Execute a chat command asynchronously
     */
    fun <T> executeAsync(
        command: ChatCommand<T>,
        onSuccess: ((T) -> Unit)? = null,
        onFailure: ((Throwable) -> Unit)? = null
    ) {
        scope.launch {
            try {
                executeCommand(command, onSuccess, onFailure)
            } catch (e: Exception) {
                Log.e(TAG, "Command execution failed", e)
                onFailure?.invoke(e)
            }
        }
    }
    
    /**
     * Execute a chat command synchronously
     */
    suspend fun <T> execute(command: ChatCommand<T>): Result<T> {
        return try {
            if (!command.canExecute()) {
                Result.failure(IllegalStateException("Command cannot be executed: ${command.getDescription()}"))
            } else {
                Log.d(TAG, "Executing command: ${command.getDescription()}")
                val result = command.executeChat()
                addToHistory(command)
                result
            }
        } catch (e: Exception) {
            Log.e(TAG, "Command execution failed: ${command.getDescription()}", e)
            Result.failure(e)
        }
    }
    
    private suspend fun <T> executeCommand(
        command: ChatCommand<T>,
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
    
    private fun addToHistory(command: ChatCommand<*>) {
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
    
    fun cleanup() {
        scope.cancel()
    }
}