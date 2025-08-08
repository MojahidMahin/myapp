package com.localllm.myapplication.command

import kotlinx.coroutines.Job

/**
 * Interface for commands that can be cancelled
 * Following SOLID Principles:
 * - Single Responsibility: Only handles cancellation concern
 * - Interface Segregation: Separate cancellable behavior from basic Command
 * - Open/Closed: Extensible for different cancellation strategies
 */
interface CancellableCommand : Command {
    /**
     * Cancel the command execution
     * Should be safe to call multiple times
     */
    fun cancel()
    
    /**
     * Check if command is cancelled
     */
    fun isCancelled(): Boolean
    
    /**
     * Set the coroutine job for this command (for cancellation)
     */
    fun setJob(job: Job)
    
    /**
     * Get the coroutine job for this command
     */
    fun getJob(): Job?
}