package com.localllm.myapplication.command.service

import android.content.Context
import android.content.Intent
import android.util.Log
import com.localllm.myapplication.service.ModelPersistenceService

/**
 * Command to stop generation in persistent service
 * Following Command Design Pattern and Single Responsibility Principle
 */
class StopGenerationInServiceCommand(
    private val context: Context
) : ServiceCommand<Unit> {
    
    companion object {
        private const val TAG = "StopGenerationInServiceCommand"
    }
    
    override suspend fun executeService(): Result<Unit> {
        return try {
            Log.d(TAG, "Stopping generation in persistent service")
            
            // Send stop generation action to the service
            val intent = Intent(context, ModelPersistenceService::class.java).apply {
                action = ModelPersistenceService.ACTION_STOP_GENERATION
            }
            context.startService(intent)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop generation in persistent service", e)
            Result.failure(e)
        }
    }
    
    override fun getDescription(): String = "Stop generation in persistent service"
    
    override fun canExecute(): Boolean = true
}