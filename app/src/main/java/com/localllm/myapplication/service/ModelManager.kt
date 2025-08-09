package com.localllm.myapplication.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.localllm.myapplication.command.model.*
import com.localllm.myapplication.command.service.*
import com.localllm.myapplication.command.chat.*
import com.localllm.myapplication.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val serviceCommandInvoker: ServiceCommandInvoker = ServiceCommandInvoker()
    
    // Chat history management
    private val chatHistoryRepository: ChatHistoryRepository = ChatHistoryRepositoryImpl(context)
    private val chatCommandInvoker: ChatCommandInvoker = ChatCommandInvoker()
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentGenerationCommandId: String? = null
    
    // Service binding for persistent mode
    private var persistenceService: ModelPersistenceService? = null
    private var isServiceBound = false
    private var isPersistentModeEnabled = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ModelPersistenceService.ModelPersistenceBinder
            persistenceService = binder.getService()
            isServiceBound = true
            isPersistentModeEnabled = persistenceService?.isPersistentModeActive() == true
            
            // Stop local model service to prevent conflicts
            scope.launch {
                if (modelService.isModelLoaded()) {
                    Log.d(TAG, "Stopping local model service to prevent MediaPipe conflicts")
                    modelService.unloadModel()
                }
            }
            
            Log.d(TAG, "Connected to ModelPersistenceService, persistent mode: $isPersistentModeEnabled")
            
            // Update combined state flows when service connects
            updateCombinedStateFlows()
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            persistenceService = null
            isServiceBound = false
            Log.d(TAG, "Disconnected from ModelPersistenceService")
            
            // Update combined state flows to use local service
            updateCombinedStateFlows()
        }
    }
    
    // Combined state flows that merge both local and persistent service states
    private val _combinedIsModelLoaded = MutableStateFlow(false)
    val isModelLoaded: StateFlow<Boolean> = _combinedIsModelLoaded.asStateFlow()
    
    private val _combinedIsModelLoading = MutableStateFlow(false)
    val isModelLoading: StateFlow<Boolean> = _combinedIsModelLoading.asStateFlow()
    
    private val _combinedCurrentModelPath = MutableStateFlow<String?>(null)
    val currentModelPath: StateFlow<String?> = _combinedCurrentModelPath.asStateFlow()
    
    private val _combinedModelLoadError = MutableStateFlow<String?>(null)
    val modelLoadError: StateFlow<String?> = _combinedModelLoadError.asStateFlow()
    
    private val _combinedGenerationInProgress = MutableStateFlow(false)
    val generationInProgress: StateFlow<Boolean> = _combinedGenerationInProgress.asStateFlow()
    
    init {
        // Try to bind to existing service
        bindToServiceIfExists()
        
        // Set up state flow synchronization
        setupStateFlowSynchronization()
    }
    
    // ========== PERSISTENT MODE MANAGEMENT ==========
    
    /**
     * Enable persistent mode - model stays loaded even when app is closed
     * Following Single Responsibility and Command Pattern
     */
    fun enablePersistentMode(onResult: ((Result<Unit>) -> Unit)? = null) {
        val command = StartPersistentModeCommand(context)
        serviceCommandInvoker.executeAsync(
            command = command,
            onSuccess = {
                isPersistentModeEnabled = true
                // Give service time to start, then bind
                scope.launch {
                    kotlinx.coroutines.delay(1000) // Wait 1 second for service to fully start
                    bindToServiceIfExists()
                    Log.d(TAG, "Persistent mode enabled and service bound")
                    scope.launch(Dispatchers.Main) {
                        onResult?.invoke(Result.success(Unit))
                    }
                }
            },
            onFailure = { error ->
                Log.e(TAG, "Failed to enable persistent mode", error)
                scope.launch(Dispatchers.Main) {
                    onResult?.invoke(Result.failure(error))
                }
            }
        )
    }
    
    /**
     * Disable persistent mode - stops background service
     * Following Single Responsibility and Command Pattern
     */
    fun disablePersistentMode(onResult: ((Result<Unit>) -> Unit)? = null) {
        val command = StopPersistentModeCommand(context)
        serviceCommandInvoker.executeAsync(
            command = command,
            onSuccess = {
                isPersistentModeEnabled = false
                unbindFromService()
                Log.d(TAG, "Persistent mode disabled")
                scope.launch(Dispatchers.Main) {
                    onResult?.invoke(Result.success(Unit))
                }
            },
            onFailure = { error ->
                Log.e(TAG, "Failed to disable persistent mode", error)
                scope.launch(Dispatchers.Main) {
                    onResult?.invoke(Result.failure(error))
                }
            }
        )
    }
    
    /**
     * Check if persistent mode is enabled
     */
    fun isPersistentModeEnabled(): Boolean = isPersistentModeEnabled
    
    /**
     * Bind to service if it exists
     */
    private fun bindToServiceIfExists() {
        if (!isServiceBound) {
            try {
                val intent = Intent(context, ModelPersistenceService::class.java)
                context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            } catch (e: Exception) {
                Log.w(TAG, "Could not bind to service", e)
            }
        }
    }
    
    /**
     * Unbind from service
     */
    private fun unbindFromService() {
        if (isServiceBound) {
            try {
                context.unbindService(serviceConnection)
                isServiceBound = false
                persistenceService = null
            } catch (e: Exception) {
                Log.w(TAG, "Error unbinding from service", e)
            }
        }
    }
    
    // ========== MODEL LOADING METHODS ==========
    
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
     * Automatically enables persistent mode for background operation
     * Following Single Responsibility Principle - just coordinates the command
     */
    fun loadModel(modelPath: String, onResult: ((Result<Unit>) -> Unit)? = null) {
        // Enable persistent mode first, then load model in service
        if (!isPersistentModeEnabled) {
            enablePersistentMode { enableResult ->
                enableResult.fold(
                    onSuccess = {
                        // Now load the model in the persistent service
                        loadModelInPersistentService(modelPath, onResult)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to enable persistent mode", error)
                        // Fallback to regular model loading
                        loadModelLocally(modelPath, onResult)
                    }
                )
            }
        } else {
            // Persistent mode already enabled, load in service
            loadModelInPersistentService(modelPath, onResult)
        }
    }
    
    /**
     * Load model in persistent service
     */
    private fun loadModelInPersistentService(modelPath: String, onResult: ((Result<Unit>) -> Unit)?) {
        // Wait a moment to ensure service is fully bound
        scope.launch {
            kotlinx.coroutines.delay(500) // Short delay for binding
            
            if (!isServiceBound) {
                Log.w(TAG, "Service not bound, trying to bind again")
                bindToServiceIfExists()
                kotlinx.coroutines.delay(1000) // Wait for binding
            }
            
            if (isServiceBound) {
                val command = LoadModelInServiceCommand(context, modelPath)
                serviceCommandInvoker.executeAsync(
                    command = command,
                    onSuccess = {
                        Log.d(TAG, "Model loaded successfully in persistent service")
                        scope.launch(Dispatchers.Main) {
                            onResult?.invoke(Result.success(Unit))
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Model loading failed in persistent service, trying locally", error)
                        // Fallback to local loading
                        loadModelLocally(modelPath, onResult)
                    }
                )
            } else {
                Log.w(TAG, "Could not bind to service, falling back to local loading")
                loadModelLocally(modelPath, onResult)
            }
        }
    }
    
    /**
     * Load model locally (fallback)
     */
    private fun loadModelLocally(modelPath: String, onResult: ((Result<Unit>) -> Unit)?) {
        val command = LoadModelCommand(modelPath, modelService)
        commandInvoker.executeAsync(
            command = command,
            onSuccess = { 
                Log.d(TAG, "Model loaded successfully via local command")
                scope.launch(Dispatchers.Main) {
                    onResult?.invoke(Result.success(Unit))
                }
            },
            onFailure = { error ->
                Log.e(TAG, "Model loading failed via local command", error)
                scope.launch(Dispatchers.Main) {
                    onResult?.invoke(Result.failure(error))
                }
            }
        )
    }
    
    /**
     * Reconnect to previously loaded model after app restart
     * Useful when app crashes and user wants to restore previous model
     */
    fun reconnectToPreviousModel(onResult: ((Result<Unit>) -> Unit)? = null) {
        scope.launch(Dispatchers.IO) {
            try {
                val result = modelService.reconnectToPreviousModel()
                // Ensure callback runs on main thread for UI operations
                scope.launch(Dispatchers.Main) {
                    onResult?.invoke(result)
                }
                if (result.isSuccess) {
                    Log.d(TAG, "Successfully reconnected to previous model")
                } else {
                    Log.w(TAG, "Failed to reconnect to previous model: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reconnecting to previous model", e)
                // Ensure callback runs on main thread for UI operations
                scope.launch(Dispatchers.Main) {
                    onResult?.invoke(Result.failure(e))
                }
            }
        }
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
                // Ensure callback runs on main thread for UI operations
                scope.launch(Dispatchers.Main) {
                    onResult?.invoke(Result.failure(error))
                }
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
                // Ensure callback runs on main thread for UI operations
                scope.launch(Dispatchers.Main) {
                    onResult?.invoke(Result.success(Unit))
                }
            },
            onFailure = { error ->
                Log.e(TAG, "Model unloading failed via command", error)
                // Ensure callback runs on main thread for UI operations
                scope.launch(Dispatchers.Main) {
                    onResult?.invoke(Result.failure(error))
                }
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
        // Use persistent service when available, fallback to local service
        val serviceToUse = if (isServiceBound && persistenceService != null) {
            persistenceService!!.getModelService()
        } else {
            modelService
        }
        
        val command = GenerateResponseCommand(prompt, images, onPartialResult, serviceToUse)
        
        // Store the command ID for potential cancellation
        val commandId = commandInvoker.executeAsync(
            command = command,
            onSuccess = { response ->
                currentGenerationCommandId = null
                Log.d(TAG, "Response generated successfully via command")
                // Ensure callback runs on main thread for UI operations
                scope.launch(Dispatchers.Main) {
                    onResult?.invoke(Result.success(response))
                }
            },
            onFailure = { error ->
                currentGenerationCommandId = null
                if (error is InterruptedException) {
                    Log.d(TAG, "Response generation was cancelled")
                    // Ensure callback runs on main thread for UI operations
                    scope.launch(Dispatchers.Main) {
                        onResult?.invoke(Result.failure(error))
                    }
                } else {
                    Log.e(TAG, "Response generation failed via command", error)
                    // Ensure callback runs on main thread for UI operations
                    scope.launch(Dispatchers.Main) {
                        onResult?.invoke(Result.failure(error))
                    }
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
        
        // Use appropriate stop mechanism based on current service
        if (isServiceBound && persistenceService != null) {
            // Use service command to stop generation in persistent service
            val command = StopGenerationInServiceCommand(context)
            serviceCommandInvoker.executeAsync(
                command = command,
                onSuccess = {
                    Log.d(TAG, "Generation stopped via persistent service")
                    scope.launch(Dispatchers.Main) {
                        // Update UI state if needed
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to stop generation via persistent service", error)
                    // Fallback to local command
                    stopGenerationLocally()
                }
            )
        } else {
            // Use local service command
            stopGenerationLocally()
        }
    }
    
    /**
     * Stop generation using local service
     */
    private fun stopGenerationLocally() {
        val command = StopGenerationCommand(modelService)
        commandInvoker.executeAsync(
            command = command,
            onSuccess = {
                Log.d(TAG, "Generation stopped via local StopGenerationCommand")
            },
            onFailure = { error ->
                Log.e(TAG, "Failed to stop generation locally", error)
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
    
    // ========== STATE FLOW SYNCHRONIZATION ==========
    
    /**
     * Set up state flow synchronization between local and persistent services
     */
    private fun setupStateFlowSynchronization() {
        // Observe local model service state
        scope.launch {
            modelService.isModelLoadedFlow.collect { isLoaded ->
                if (!isServiceBound) {
                    _combinedIsModelLoaded.value = isLoaded
                }
            }
        }
        
        scope.launch {
            modelService.isModelLoadingFlow.collect { isLoading ->
                if (!isServiceBound) {
                    _combinedIsModelLoading.value = isLoading
                }
            }
        }
        
        scope.launch {
            modelService.currentModelPathFlow.collect { path ->
                if (!isServiceBound) {
                    _combinedCurrentModelPath.value = path
                }
            }
        }
        
        scope.launch {
            modelService.modelLoadErrorFlow.collect { error ->
                if (!isServiceBound) {
                    _combinedModelLoadError.value = error
                }
            }
        }
        
        scope.launch {
            modelService.isGeneratingFlow.collect { generating ->
                if (!isServiceBound) {
                    _combinedGenerationInProgress.value = generating
                }
            }
        }
    }
    
    /**
     * Update combined state flows when persistent service connects/disconnects
     */
    private fun updateCombinedStateFlows() {
        if (isServiceBound && persistenceService != null) {
            // Use persistent service state
            val serviceFlows = persistenceService!!.getModelStateFlows()
            
            scope.launch {
                serviceFlows.isModelLoaded.collect { isLoaded ->
                    _combinedIsModelLoaded.value = isLoaded
                }
            }
            
            scope.launch {
                serviceFlows.isModelLoading.collect { isLoading ->
                    _combinedIsModelLoading.value = isLoading
                }
            }
            
            scope.launch {
                serviceFlows.currentModelPath.collect { path ->
                    _combinedCurrentModelPath.value = path
                }
            }
            
            scope.launch {
                serviceFlows.modelLoadError.collect { error ->
                    _combinedModelLoadError.value = error
                }
            }
            
            scope.launch {
                serviceFlows.isGenerating.collect { generating ->
                    _combinedGenerationInProgress.value = generating
                }
            }
        } else {
            // Fall back to local service state immediately
            _combinedIsModelLoaded.value = modelService.isModelLoaded()
            _combinedIsModelLoading.value = modelService.isModelLoading()
            _combinedCurrentModelPath.value = modelService.getCurrentModelPath()
            _combinedModelLoadError.value = modelService.getModelLoadError()
            _combinedGenerationInProgress.value = modelService.isGenerating()
        }
    }
    
    // ========== CHAT HISTORY METHODS ==========
    
    /**
     * Get chat history repository for direct access
     */
    fun getChatHistoryRepository(): ChatHistoryRepository = chatHistoryRepository
    
    /**
     * Create a new chat session
     */
    fun createChatSession(onResult: ((Result<ChatSession>) -> Unit)? = null) {
        val modelPath = currentModelPath.value
        val command = CreateSessionCommand(chatHistoryRepository, modelPath)
        chatCommandInvoker.executeAsync(
            command = command,
            onSuccess = { session ->
                Log.d(TAG, "Chat session created: ${session.id}")
                scope.launch(Dispatchers.Main) {
                    onResult?.invoke(Result.success(session))
                }
            },
            onFailure = { error ->
                Log.e(TAG, "Failed to create chat session", error)
                scope.launch(Dispatchers.Main) {
                    onResult?.invoke(Result.failure(error))
                }
            }
        )
    }
    
    /**
     * Save a message to current session
     */
    fun saveMessage(message: ChatMessage, onResult: ((Result<Unit>) -> Unit)? = null) {
        scope.launch {
            val currentSession = chatHistoryRepository.getCurrentSession()
            currentSession.fold(
                onSuccess = { session ->
                    if (session != null) {
                        val command = SaveMessageCommand(chatHistoryRepository, session.id, message)
                        chatCommandInvoker.executeAsync(
                            command = command,
                            onSuccess = {
                                Log.d(TAG, "Message saved successfully")
                                scope.launch(Dispatchers.Main) {
                                    onResult?.invoke(Result.success(Unit))
                                }
                            },
                            onFailure = { error ->
                                Log.e(TAG, "Failed to save message", error)
                                scope.launch(Dispatchers.Main) {
                                    onResult?.invoke(Result.failure(error))
                                }
                            }
                        )
                    } else {
                        // Create new session if none exists
                        createChatSession { sessionResult ->
                            sessionResult.fold(
                                onSuccess = { newSession ->
                                    saveMessage(message, onResult)
                                },
                                onFailure = { error ->
                                    onResult?.invoke(Result.failure(error))
                                }
                            )
                        }
                    }
                },
                onFailure = { error ->
                    scope.launch(Dispatchers.Main) {
                        onResult?.invoke(Result.failure(error))
                    }
                }
            )
        }
    }
    
    /**
     * Update a message content (for streaming responses)
     */
    fun updateMessage(messageId: String, content: String, onResult: ((Result<Unit>) -> Unit)? = null) {
        scope.launch {
            val currentSession = chatHistoryRepository.getCurrentSession()
            currentSession.fold(
                onSuccess = { session ->
                    if (session != null) {
                        val command = UpdateMessageCommand(chatHistoryRepository, session.id, messageId, content)
                        chatCommandInvoker.executeAsync(
                            command = command,
                            onSuccess = {
                                // Don't log every update to avoid spam during streaming
                                scope.launch(Dispatchers.Main) {
                                    onResult?.invoke(Result.success(Unit))
                                }
                            },
                            onFailure = { error ->
                                Log.e(TAG, "Failed to update message", error)
                                scope.launch(Dispatchers.Main) {
                                    onResult?.invoke(Result.failure(error))
                                }
                            }
                        )
                    } else {
                        scope.launch(Dispatchers.Main) {
                            onResult?.invoke(Result.failure(Exception("No current session")))
                        }
                    }
                },
                onFailure = { error ->
                    scope.launch(Dispatchers.Main) {
                        onResult?.invoke(Result.failure(error))
                    }
                }
            )
        }
    }
    
    fun cleanup() {
        // Note: Don't disable persistent mode on cleanup - let it run in background
        // Only unbind from service connection
        unbindFromService()
        
        // Clean up local resources
        commandInvoker.cleanup()
        serviceCommandInvoker.cleanup()
        chatCommandInvoker.cleanup()
        scope.cancel()
    }
    
    /**
     * Force cleanup including stopping persistent mode
     * Call this only when truly shutting down the app permanently
     */
    fun forceCleanup() {
        disablePersistentMode()
        unloadModel()
        commandInvoker.cleanup()
        serviceCommandInvoker.cleanup()
        chatCommandInvoker.cleanup()
        scope.cancel()
    }
}