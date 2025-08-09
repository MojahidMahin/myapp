package com.localllm.myapplication.command.service

import android.content.Context
import android.util.Log
import com.localllm.myapplication.service.ModelPersistenceService

/**
 * Command to start persistent model mode
 * Following Command Design Pattern and Single Responsibility Principle
 */
class StartPersistentModeCommand(
    private val context: Context
) : ServiceCommand<Unit> {
    
    companion object {
        private const val TAG = "StartPersistentModeCommand"
    }
    
    override suspend fun executeService(): Result<Unit> {
        return try {
            Log.d(TAG, "Starting persistent mode")
            ModelPersistenceService.startPersistentMode(context)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start persistent mode", e)
            Result.failure(e)
        }
    }
    
    override fun getDescription(): String = "Start persistent model mode"
    
    override fun canExecute(): Boolean = true
}