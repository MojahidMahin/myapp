package com.localllm.myapplication.service

import android.content.Context
import android.util.Log
import com.localllm.myapplication.command.model.ModelService
import com.localllm.myapplication.command.model.ModelStateRepository
import com.localllm.myapplication.service.ai.MediaPipeLLMService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Implementation of ModelService interface
 * Following Single Responsibility Principle - only handles model operations
 * Following Dependency Inversion Principle - depends on abstractions
 */
class ModelServiceImpl(
    private val context: Context,
    private val stateRepository: ModelStateRepository
) : ModelService {
    
    companion object {
        private const val TAG = "ModelServiceImpl"
    }
    
    private var llmService: MediaPipeLLMService? = null
    
    // State flows - Following Observer Pattern
    private val _isModelLoaded = MutableStateFlow(false)
    override val isModelLoadedFlow: StateFlow<Boolean> = _isModelLoaded.asStateFlow()
    
    private val _isModelLoading = MutableStateFlow(false)
    override val isModelLoadingFlow: StateFlow<Boolean> = _isModelLoading.asStateFlow()
    
    private val _isGenerating = MutableStateFlow(false)
    override val isGeneratingFlow: StateFlow<Boolean> = _isGenerating.asStateFlow()
    
    private val _currentModelPath = MutableStateFlow<String?>(null)
    override val currentModelPathFlow: StateFlow<String?> = _currentModelPath.asStateFlow()
    
    private val _modelLoadError = MutableStateFlow<String?>(null)
    override val modelLoadErrorFlow: StateFlow<String?> = _modelLoadError.asStateFlow()
    
    init {
        restorePreviousState()
    }
    
    override suspend fun loadModel(modelPath: String): Result<Unit> {
        return try {
            Log.d(TAG, "Loading model: $modelPath")
            _isModelLoading.value = true
            _modelLoadError.value = null
            
            // Clean up existing service
            llmService?.cleanup()
            
            // Small delay to ensure cleanup completes
            kotlinx.coroutines.delay(200)
            
            // Create new service and initialize
            llmService = MediaPipeLLMService(context)
            val result = llmService!!.initialize(modelPath)
            
            result.fold(
                onSuccess = {
                    _isModelLoaded.value = true
                    _currentModelPath.value = modelPath
                    _isModelLoading.value = false
                    stateRepository.saveModelState(modelPath, true)
                    Log.d(TAG, "Model loaded successfully: $modelPath")
                    Result.success(Unit)
                },
                onFailure = { error ->
                    _isModelLoaded.value = false
                    _isModelLoading.value = false
                    _modelLoadError.value = error.message
                    stateRepository.saveModelState(null, false)
                    Log.e(TAG, "Failed to load model: $modelPath", error)
                    Result.failure(error)
                }
            )
        } catch (e: OutOfMemoryError) {
            _isModelLoaded.value = false
            _isModelLoading.value = false
            _modelLoadError.value = "Insufficient memory to load model"
            stateRepository.saveModelState(null, false)
            Log.e(TAG, "Out of memory loading model: $modelPath", e)
            Result.failure(Exception("Insufficient memory to load model", e))
        } catch (e: Exception) {
            _isModelLoaded.value = false
            _isModelLoading.value = false
            _modelLoadError.value = e.message
            stateRepository.saveModelState(null, false)
            Log.e(TAG, "Exception loading model: $modelPath", e)
            Result.failure(e)
        }
    }
    
    override suspend fun unloadModel(): Result<Unit> {
        return try {
            llmService?.cleanup()
            llmService = null
            _isModelLoaded.value = false
            _currentModelPath.value = null
            _modelLoadError.value = null
            _isGenerating.value = false
            stateRepository.clearModelState()
            Log.d(TAG, "Model unloaded successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error unloading model", e)
            Result.failure(e)
        }
    }
    
    override suspend fun generateResponse(
        prompt: String,
        images: List<android.graphics.Bitmap>,
        onPartialResult: ((String) -> Unit)?
    ): Result<String> {
        if (!isModelLoaded()) {
            return Result.failure(Exception("No model loaded"))
        }
        
        _isGenerating.value = true
        return try {
            val result = llmService!!.generateResponse(prompt, images, onPartialResult)
            _isGenerating.value = false
            result
        } catch (e: InterruptedException) {
            _isGenerating.value = false
            Log.d(TAG, "Generation was cancelled by user")
            Result.failure(e)
        } catch (e: Exception) {
            _isGenerating.value = false
            Result.failure(e)
        }
    }
    
    override suspend fun stopGeneration(): Result<Unit> {
        return try {
            llmService?.cancelGeneration()
            _isGenerating.value = false
            Log.d(TAG, "Generation stopped successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping generation", e)
            Result.failure(e)
        }
    }
    
    override suspend fun resetSession(): Result<Unit> {
        return try {
            llmService?.resetSession()
            _isGenerating.value = false
            Log.d(TAG, "Session reset")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting session", e)
            Result.failure(e)
        }
    }
    
    // State queries
    override fun isModelLoaded(): Boolean = _isModelLoaded.value
    override fun isModelLoading(): Boolean = _isModelLoading.value
    override fun isGenerating(): Boolean = _isGenerating.value
    override fun getCurrentModelPath(): String? = _currentModelPath.value
    override fun getModelLoadError(): String? = _modelLoadError.value
    
    /**
     * Reconnect to previously loaded model after app restart
     */
    override suspend fun reconnectToPreviousModel(): Result<Unit> {
        val savedModelPath = stateRepository.getLastModelPath()
        val wasModelLoaded = stateRepository.wasModelLoaded()
        
        if (savedModelPath != null && wasModelLoaded) {
            Log.d(TAG, "Reconnecting to previous model: $savedModelPath")
            return loadModel(savedModelPath)
        }
        
        return Result.failure(Exception("No previous model to reconnect to"))
    }
    
    private fun restorePreviousState() {
        val savedModelPath = stateRepository.getLastModelPath()
        val wasModelLoaded = stateRepository.wasModelLoaded()
        
        if (savedModelPath != null && wasModelLoaded) {
            _currentModelPath.value = savedModelPath
            // Show that model was previously loaded but needs reconnection
            _isModelLoaded.value = false // Don't set to true since service isn't initialized
            Log.d(TAG, "Restored previous model state: $savedModelPath (needs reconnection)")
            // Note: Auto-loading removed - user must explicitly reconnect to restored model
        }
    }
}