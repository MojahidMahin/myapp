package com.localllm.myapplication.command.service

import android.content.Context
import android.util.Log
import com.localllm.myapplication.service.ModelPersistenceService

/**
 * Command to stop persistent model mode
 * Following Command Design Pattern and Single Responsibility Principle
 */
class StopPersistentModeCommand(
    private val context: Context
) : ServiceCommand<Unit> {
    
    companion object {
        private const val TAG = "StopPersistentModeCommand"
    }
    
    override suspend fun executeService(): Result<Unit> {
        return try {
            Log.d(TAG, "Stopping persistent mode")
            ModelPersistenceService.stopPersistentMode(context)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop persistent mode", e)
            Result.failure(e)
        }
    }
    
    override fun getDescription(): String = "Stop persistent model mode"
    
    override fun canExecute(): Boolean = true
}