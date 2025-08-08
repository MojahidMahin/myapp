package com.localllm.myapplication.service

import android.content.Context
import android.util.Log
import com.localllm.myapplication.command.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Refactored ModelManager following SOLID principles and Command pattern
 * 
 * S - Single Responsibility: Only coordinates model operations through commands
 * O - Open/Closed: Extensible through new commands without modification
 * L - Liskov Substitution: Uses interfaces that can be substituted
 * I - Interface Segregation: Uses focused, specific interfaces
 * D - Dependency Inversion: Depends on abstractions (ModelService, commands)
 */
class ModelManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "ModelManager"
        
        @Volatile
        private var INSTANCE: ModelManager? = null
        
        fun getInstance(context: Context): ModelManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ModelManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // Following Dependency Inversion Principle - depend on abstractions
    private val stateRepository: ModelStateRepository = ModelStateRepositoryImpl(context)
    private val modelService: ModelService = ModelServiceImpl(context, stateRepository)
    private val commandInvoker: ModelCommandInvoker = ModelCommandInvoker()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentGenerationCommandId: String? = null
    
    // Expose state flows from service
    val isModelLoaded: StateFlow<Boolean> = modelService.isModelLoadedFlow
    val isModelLoading: StateFlow<Boolean> = modelService.isModelLoadingFlow
    val currentModelPath: StateFlow<String?> = modelService.currentModelPathFlow
    val modelLoadError: StateFlow<String?> = modelService.modelLoadErrorFlow
    val generationInProgress: StateFlow<Boolean> = modelService.isGeneratingFlow
    
    /**
     * Load a model from file picker using Command pattern
     * Following Single Responsibility Principle - just coordinates the command
     */
    fun loadModelFromFilePicker(onResult: ((Result<Unit>) -> Unit)? = null) {
        // This will be called from UI after file selection
        Log.d(TAG, "Initiating model file selection")
        onResult?.invoke(Result.success(Unit)) // Signal UI to show file picker
    }
    
    /**
     * Load a model with specific path using Command pattern
     * Following Single Responsibility Principle - just coordinates the command
     */
    fun loadModel(modelPath: String, onResult: ((Result<Unit>) -> Unit)? = null) {
        val command = LoadModelCommand(modelPath, modelService)
        commandInvoker.executeAsync(
            command = command,
            onSuccess = { 
                Log.d(TAG, "Model loaded successfully via command")
                onResult?.invoke(Result.success(Unit))
            },
            onFailure = { error ->
                Log.e(TAG, "Model loading failed via command", error)
                onResult?.invoke(Result.failure(error))
            }
        )
    }
    
    /**
     * Process selected file URI and load model
     */
    fun loadModelFromUri(uri: android.net.Uri, onResult: ((Result<Unit>) -> Unit)? = null) {
        val filePickerCommand = FilePickerCommand(context)
        filePickerCommand.setSelectedUri(uri)
        
        commandInvoker.executeAsync(
            command = filePickerCommand,
            onSuccess = { modelPath ->
                Log.d(TAG, "File processed successfully: $modelPath")
                // Now load the model with the processed path
                loadModel(modelPath, onResult)
            },
            onFailure = { error ->
                Log.e(TAG, "File processing failed", error)
                onResult?.invoke(Result.failure(error))
            }
        )
    }
    
    /**
     * Unload model using Command pattern
     */
    fun unloadModel(onResult: ((Result<Unit>) -> Unit)? = null) {
        val command = UnloadModelCommand(modelService)
        commandInvoker.executeAsync(
            command = command,
            onSuccess = { 
                Log.d(TAG, "Model unloaded successfully via command")
                onResult?.invoke(Result.success(Unit))
            },
            onFailure = { error ->
                Log.e(TAG, "Model unloading failed via command", error)
                onResult?.invoke(Result.failure(error))
            }
        )
    }
    
    /**
     * Generate response using cancellable Command pattern
     * Following SOLID principles with proper cancellation support
     */
    fun generateResponse(
        prompt: String,
        images: List<android.graphics.Bitmap> = emptyList(),
        onPartialResult: ((String) -> Unit)? = null,
        onResult: ((Result<String>) -> Unit)? = null
    ) {
        // Use the regular command through ModelService for proper abstraction
        val command = GenerateResponseCommand(prompt, images, onPartialResult, modelService)
        
        // Store the command ID for potential cancellation
        val commandId = commandInvoker.executeAsync(
            command = command,
            onSuccess = { response ->
                currentGenerationCommandId = null
                Log.d(TAG, "Response generated successfully via command")
                onResult?.invoke(Result.success(response))
            },
            onFailure = { error ->
                currentGenerationCommandId = null
                if (error is InterruptedException) {
                    Log.d(TAG, "Response generation was cancelled")
                    onResult?.invoke(Result.failure(error))
                } else {
                    Log.e(TAG, "Response generation failed via command", error)
                    onResult?.invoke(Result.failure(error))
                }
            }
        )
        
        currentGenerationCommandId = commandId
    }
    
    /**
     * Stop generation using enhanced Command pattern with proper cancellation
     */
    fun stopGeneration() {
        // First, try to cancel the current generation command if it exists
        currentGenerationCommandId?.let { commandId ->
            val cancelled = commandInvoker.cancelCommand(commandId)
            if (cancelled) {
                Log.d(TAG, "Cancelled generation command: $commandId")
                currentGenerationCommandId = null
                return
            }
        }
        
        // Fallback: use the stop command through model service
        val command = StopGenerationCommand(modelService)
        commandInvoker.executeAsync(
            command = command,
            onSuccess = {
                Log.d(TAG, "Generation stopped via StopGenerationCommand")
            },
            onFailure = { error ->
                Log.e(TAG, "Failed to stop generation", error)
            }
        )
    }
    
    /**
     * Reset session
     */
    fun resetSession() {
        scope.launch {
            modelService.resetSession()
        }
    }
    
    /**
     * Get available models - Following Single Responsibility Principle
     * This method only handles model discovery
     */
    fun getAvailableModels(): List<String> {
        val models = mutableListOf<String>()
        
        // Check cache directory
        val cacheDir = context.cacheDir
        val cacheModels = cacheDir.listFiles { file ->
            file.name.endsWith(".task") || file.name.contains("gemma")
        }?.map { it.absolutePath } ?: emptyList()
        models.addAll(cacheModels)
        
        // Check files directory
        val filesDir = context.filesDir
        val fileModels = filesDir.listFiles { file ->
            file.name.endsWith(".task") || file.name.contains("gemma")
        }?.map { it.absolutePath } ?: emptyList()
        models.addAll(fileModels)
        
        // Check assets
        try {
            val assetFiles = context.assets.list("") ?: emptyArray()
            val assetModels = assetFiles.filter { 
                it.endsWith(".task") || it.contains("gemma") 
            }
            models.addAll(assetModels)
        } catch (e: Exception) {
            Log.w(TAG, "Could not list assets", e)
        }
        
        // Add common paths
        val commonPaths = listOf(
            "/data/user/0/com.localllm.myapplication/cache/gemma-3n-E2B-it-int4.task",
            "${cacheDir.absolutePath}/gemma-3n-E2B-it-int4.task",
            "${filesDir.absolutePath}/gemma-3n-E2B-it-int4.task"
        )
        
        commonPaths.forEach { path ->
            if (File(path).exists() && !models.contains(path)) {
                models.add(path)
            }
        }
        
        return models
    }
    
    /**
     * Copy model to cache - utility method
     */
    fun copyModelToCache(sourcePath: String): String? {
        return try {
            val sourceFile = File(sourcePath)
            if (!sourceFile.exists()) {
                Log.e(TAG, "Source model file does not exist: $sourcePath")
                return null
            }
            
            val cacheDir = context.cacheDir
            val targetFile = File(cacheDir, sourceFile.name)
            
            sourceFile.copyTo(targetFile, overwrite = true)
            Log.d(TAG, "Model copied to cache: ${targetFile.absolutePath}")
            targetFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy model to cache", e)
            null
        }
    }
    
    /**
     * Get command execution history - useful for debugging
     */
    fun getCommandHistory(): List<String> {
        return commandInvoker.getCommandHistory()
    }
    
    fun cleanup() {
        unloadModel()
        commandInvoker.cleanup()
        scope.cancel()
    }
}