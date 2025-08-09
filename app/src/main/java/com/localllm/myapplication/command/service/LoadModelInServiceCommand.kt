package com.localllm.myapplication.command.service

import android.content.Context
import android.util.Log
import com.localllm.myapplication.service.ModelPersistenceService

/**
 * Command to load model in persistent service
 * Following Command Design Pattern and Single Responsibility Principle
 */
class LoadModelInServiceCommand(
    private val context: Context,
    private val modelPath: String
) : ServiceCommand<Unit> {
    
    companion object {
        private const val TAG = "LoadModelInServiceCommand"
    }
    
    override suspend fun executeService(): Result<Unit> {
        return try {
            Log.d(TAG, "Loading model in service: $modelPath")
            ModelPersistenceService.loadModelInService(context, modelPath)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model in service", e)
            Result.failure(e)
        }
    }
    
    override fun getDescription(): String = "Load model in persistent service: $modelPath"
    
    override fun canExecute(): Boolean = modelPath.isNotBlank()
}