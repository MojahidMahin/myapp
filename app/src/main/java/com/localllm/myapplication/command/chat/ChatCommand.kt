package com.localllm.myapplication.command.chat

import com.localllm.myapplication.command.Command

/**
 * Base interface for chat commands
 * Following Command Design Pattern and Interface Segregation Principle
 */
interface ChatCommand<T> : Command {
    suspend fun executeChat(): Result<T>
    fun getDescription(): String
    fun canExecute(): Boolean = true
    
    // Override base execute to call executeChat
    override fun execute() {
        // This is handled by the invoker asynchronously
    }
}