package com.localllm.myapplication.command.model

import kotlinx.coroutines.flow.StateFlow

/**
 * Command pattern interface for model operations
 * Following Command Pattern from Gang of Four design patterns
 */
interface ModelCommand<T> {
    suspend fun execute(): Result<T>
    fun canExecute(): Boolean = true
    fun getDescription(): String
}

/**
 * Specific command implementations for model operations
 */
class LoadModelCommand(
    private val modelPath: String,
    private val modelService: ModelService
) : ModelCommand<Unit> {
    
    override suspend fun execute(): Result<Unit> {
        return modelService.loadModel(modelPath)
    }
    
    override fun canExecute(): Boolean {
        return modelPath.isNotBlank() && !modelService.isModelLoaded()
    }
    
    override fun getDescription(): String = "Load model from: $modelPath"
}

class UnloadModelCommand(
    private val modelService: ModelService
) : ModelCommand<Unit> {
    
    override suspend fun execute(): Result<Unit> {
        return modelService.unloadModel()
    }
    
    override fun canExecute(): Boolean {
        val serviceLoaded = modelService.isModelLoaded()
        android.util.Log.d("UnloadModelCommand", "modelService.isModelLoaded() = $serviceLoaded")
        // Allow unloading even if service thinks model isn't loaded
        // This handles state synchronization issues between UI and service
        android.util.Log.d("UnloadModelCommand", "Allowing unload command execution")
        return true
    }
    
    override fun getDescription(): String = "Unload current model"
}

class GenerateResponseCommand(
    private val prompt: String,
    private val images: List<android.graphics.Bitmap> = emptyList(),
    private val onPartialResult: ((String) -> Unit)? = null,
    private val modelService: ModelService
) : ModelCommand<String> {
    
    override suspend fun execute(): Result<String> {
        return modelService.generateResponse(prompt, images, onPartialResult)
    }
    
    override fun canExecute(): Boolean {
        return prompt.isNotBlank() && modelService.isModelLoaded()
    }
    
    override fun getDescription(): String = "Generate response for prompt: ${prompt.take(50)}..."
}

class StopGenerationCommand(
    private val modelService: ModelService
) : ModelCommand<Unit> {
    
    override suspend fun execute(): Result<Unit> {
        return modelService.stopGeneration()
    }
    
    override fun canExecute(): Boolean {
        return modelService.isGenerating()
    }
    
    override fun getDescription(): String = "Stop current generation"
}