package com.localllm.myapplication.command.service

import com.localllm.myapplication.command.Command

/**
 * Base interface for service commands
 * Following Command Design Pattern and Interface Segregation Principle
 */
interface ServiceCommand<T> : Command {
    suspend fun executeService(): Result<T>
    fun getDescription(): String
    fun canExecute(): Boolean = true
    
    // Override base execute to call executeService
    override fun execute() {
        // This is handled by the invoker asynchronously
    }
}