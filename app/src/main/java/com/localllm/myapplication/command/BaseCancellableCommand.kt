package com.localllm.myapplication.command

import kotlinx.coroutines.Job
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Abstract base class for cancellable commands
 * Following SOLID Principles:
 * - Single Responsibility: Manages cancellation state and job lifecycle
 * - Template Method Pattern: Provides cancellation template, subclasses implement execution
 */
abstract class BaseCancellableCommand : CancellableCommand {
    
    private val cancelled = AtomicBoolean(false)
    private var job: Job? = null
    
    override fun cancel() {
        if (!cancelled.getAndSet(true)) {
            job?.cancel()
            onCancelled()
        }
    }
    
    override fun isCancelled(): Boolean = cancelled.get()
    
    override fun setJob(job: Job) {
        this.job = job
    }
    
    override fun getJob(): Job? = job
    
    /**
     * Template method - called when command is cancelled
     * Subclasses can override to perform cleanup
     */
    protected open fun onCancelled() {
        // Default implementation does nothing
    }
    
    /**
     * Check if cancelled and throw exception if so
     * Useful for long-running operations to check periodically
     */
    protected fun throwIfCancelled() {
        if (isCancelled()) {
            throw InterruptedException("Command was cancelled")
        }
    }
}