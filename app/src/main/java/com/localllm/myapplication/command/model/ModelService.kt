package com.localllm.myapplication.command.model

import kotlinx.coroutines.flow.StateFlow

/**
 * Service interface for model operations
 * Following Dependency Inversion Principle - depend on abstractions, not concretions
 */
interface ModelService {
    suspend fun loadModel(modelPath: String): Result<Unit>
    suspend fun unloadModel(): Result<Unit>
    suspend fun generateResponse(
        prompt: String,
        images: List<android.graphics.Bitmap> = emptyList(),
        onPartialResult: ((String) -> Unit)? = null
    ): Result<String>
    suspend fun stopGeneration(): Result<Unit>
    suspend fun resetSession(): Result<Unit>
    
    // State queries
    fun isModelLoaded(): Boolean
    fun isModelLoading(): Boolean
    fun isGenerating(): Boolean
    fun getCurrentModelPath(): String?
    fun getModelLoadError(): String?
    
    // State flows for reactive UI
    val isModelLoadedFlow: StateFlow<Boolean>
    val isModelLoadingFlow: StateFlow<Boolean>
    val isGeneratingFlow: StateFlow<Boolean>
    val currentModelPathFlow: StateFlow<String?>
    val modelLoadErrorFlow: StateFlow<String?>
}

/**
 * Repository interface for model state persistence
 * Following Single Responsibility Principle - separate persistence concerns
 */
interface ModelStateRepository {
    fun saveModelState(modelPath: String?, isLoaded: Boolean)
    fun getLastModelPath(): String?
    fun wasModelLoaded(): Boolean
    fun clearModelState()
}