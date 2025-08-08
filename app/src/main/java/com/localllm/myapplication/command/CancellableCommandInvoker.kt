package com.localllm.myapplication.command

import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Command Invoker that supports cancellation
 * Following SOLID Principles:
 * - Single Responsibility: Manages command execution and cancellation
 * - Open/Closed: Extensible for new command types without modification
 * - Dependency Inversion: Depends on Command abstractions
 */
class CancellableCommandInvoker {
    
    private val activeCommands = ConcurrentHashMap<String, CancellableCommand>()
    private val commandScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    
    /**
     * Execute a command asynchronously with cancellation support
     */
    fun <T> executeAsync(
        command: Command,
        commandId: String? = null,
        onSuccess: ((T) -> Unit)? = null,
        onFailure: ((Throwable) -> Unit)? = null
    ): String {
        val id = commandId ?: generateCommandId()
        
        val job = commandScope.launch {
            try {
                // Set job reference for cancellable commands
                if (command is CancellableCommand) {
                    command.setJob(coroutineContext[Job]!!)
                    activeCommands[id] = command
                }
                
                @Suppress("UNCHECKED_CAST")
                val result = command.execute() as T
                onSuccess?.invoke(result)
            } catch (e: CancellationException) {
                // Command was cancelled - don't call onFailure
                return@launch
            } catch (e: Exception) {
                onFailure?.invoke(e)
            } finally {
                // Clean up after execution
                if (command is CancellableCommand) {
                    activeCommands.remove(id)
                }
            }
        }
        
        return id
    }
    
    /**
     * Cancel a specific command by ID
     */
    fun cancelCommand(commandId: String): Boolean {
        val command = activeCommands[commandId]
        return if (command != null) {
            command.cancel()
            activeCommands.remove(commandId)
            true
        } else {
            false
        }
    }
    
    /**
     * Cancel all active commands
     */
    fun cancelAllCommands() {
        activeCommands.values.forEach { it.cancel() }
        activeCommands.clear()
    }
    
    /**
     * Get list of active command IDs
     */
    fun getActiveCommandIds(): List<String> {
        return activeCommands.keys.toList()
    }
    
    /**
     * Check if a command is active
     */
    fun isCommandActive(commandId: String): Boolean {
        return activeCommands.containsKey(commandId)
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        cancelAllCommands()
        commandScope.cancel()
    }
    
    private fun generateCommandId(): String {
        return "cmd_${System.currentTimeMillis()}_${(Math.random() * 1000).toInt()}"
    }
}