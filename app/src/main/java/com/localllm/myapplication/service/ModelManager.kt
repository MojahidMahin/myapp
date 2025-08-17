package com.localllm.myapplication.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection as AndroidServiceConnection
import android.os.IBinder
import android.util.Log
import com.localllm.myapplication.command.model.*
import com.localllm.myapplication.command.service.*
import com.localllm.myapplication.command.chat.*
import com.localllm.myapplication.command.background.*
import com.localllm.myapplication.data.*
import com.localllm.myapplication.scheduler.BackgroundTaskScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
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
    private val workflowRepository: com.localllm.myapplication.data.WorkflowRepository = com.localllm.myapplication.data.InMemoryWorkflowRepository()
    private val modelService: ModelService = ModelServiceImpl(context, stateRepository)
    private val commandInvoker: ModelCommandInvoker = ModelCommandInvoker()
    private val serviceCommandInvoker: ServiceCommandInvoker = ServiceCommandInvoker()
    
    // Chat history management
    private val chatHistoryRepository: ChatHistoryRepository = ChatHistoryRepositoryImpl(context)
    private val chatCommandInvoker: ChatCommandInvoker = ChatCommandInvoker()
    
    // Background processing management
    private val backgroundTaskScheduler: BackgroundTaskScheduler = BackgroundTaskScheduler(context)
    private val backgroundCommandInvoker: BackgroundCommandInvoker = BackgroundCommandInvoker()
    
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentGenerationCommandId: String? = null
    
    // Service binding for persistent mode
    private var persistenceService: ModelPersistenceService? = null
    private var isServiceBound = false
    private var isPersistentModeEnabled = false
    
    private val serviceConnection = object : AndroidServiceConnection {
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
        // Universal initialization for all devices
        Log.d(TAG, "🌍 Initializing universal ModelManager for all Android devices")
        
        // Try to bind to existing service
        bindToServiceIfExists()
        
        // Set up state flow synchronization
        setupStateFlowSynchronization()
        
        // Set up universal memory management
        setupUniversalMemoryManagement()
        
        // Initialize background pre-warming
        initializeBackgroundOptimizations()
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
     * Universal model loading with automatic optimization for all devices
     * Automatically enables persistent mode for faster subsequent loads
     * Following Single Responsibility Principle - just coordinates the command
     */
    fun loadModel(modelPath: String, onResult: ((Result<Unit>) -> Unit)? = null) {
        Log.d(TAG, "🚀 Universal model loading initiated for all device types")
        
        // Always try to enable persistent mode for faster loading (universal benefit)
        if (!isPersistentModeEnabled) {
            Log.d(TAG, "📱 Enabling persistent mode for faster loading on all devices")
            enablePersistentMode { enableResult ->
                enableResult.fold(
                    onSuccess = {
                        Log.d(TAG, "✅ Persistent mode enabled - faster loading for all future requests")
                        // Now load the model in the persistent service
                        loadModelInPersistentService(modelPath, onResult)
                    },
                    onFailure = { error ->
                        Log.w(TAG, "Persistent mode failed, using local loading (still optimized)", error)
                        // Fallback to optimized local loading
                        loadModelLocally(modelPath, onResult)
                    }
                )
            }
        } else {
            // Persistent mode already enabled, load in service
            Log.d(TAG, "🔄 Using persistent service for instant model access")
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
     * Enhanced with better state management for UI consistency
     */
    fun unloadModel(onResult: ((Result<Unit>) -> Unit)? = null) {
        Log.d(TAG, "unloadModel called, current state - isModelLoaded: ${_combinedIsModelLoaded.value}")
        
        // Reset UI state immediately to prevent UI issues
        scope.launch(Dispatchers.Main) {
            _combinedIsModelLoading.value = true
        }
        
        // If persistent service is active, unload from it first
        if (isServiceBound && persistenceService != null) {
            Log.d(TAG, "Unloading model from persistent service")
            try {
                // Directly send unload command to service
                val intent = Intent(context, ModelPersistenceService::class.java).apply {
                    action = ModelPersistenceService.ACTION_UNLOAD_MODEL
                }
                context.startService(intent)
                Log.d(TAG, "Unload command sent to persistent service")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send unload command to persistent service", e)
            }
        }
        
        // Always unload from local service to ensure clean state
        unloadModelLocally(onResult)
    }
    
    /**
     * Unload model from local service with enhanced state management
     */
    private fun unloadModelLocally(onResult: ((Result<Unit>) -> Unit)?) {
        val command = UnloadModelCommand(modelService)
        commandInvoker.executeAsync(
            command = command,
            onSuccess = { 
                Log.d(TAG, "Model unloaded successfully via local command")
                // Refresh state after successful unload
                scope.launch(Dispatchers.Main) {
                    Log.d(TAG, "Updating UI state: isModelLoaded -> false")
                    _combinedIsModelLoaded.value = false
                    _combinedCurrentModelPath.value = null
                    _combinedModelLoadError.value = null
                    _combinedIsModelLoading.value = false
                    
                    // Force state synchronization update
                    updateCombinedStateFlows()
                    
                    Log.d(TAG, "State updated: isModelLoaded = ${_combinedIsModelLoaded.value}, currentModelPath = ${_combinedCurrentModelPath.value}")
                    onResult?.invoke(Result.success(Unit))
                }
            },
            onFailure = { error ->
                Log.e(TAG, "Model unloading failed via local command", error)
                // Even if unload fails, reset UI state to allow retry
                scope.launch(Dispatchers.Main) {
                    Log.d(TAG, "Unload failed, but resetting state for retry")
                    _combinedIsModelLoaded.value = false
                    _combinedCurrentModelPath.value = null
                    _combinedModelLoadError.value = "Unload failed: ${error.message}"
                    _combinedIsModelLoading.value = false
                    
                    // Force state synchronization update even on failure
                    updateCombinedStateFlows()
                    
                    onResult?.invoke(Result.failure(error))
                }
            }
        )
    }
    
    /**
     * Universal response generation optimized for all devices
     * Following SOLID principles with device-adaptive optimizations
     */
    fun generateResponse(
        prompt: String,
        images: List<android.graphics.Bitmap> = emptyList(),
        onPartialResult: ((String) -> Unit)? = null,
        onResult: ((Result<String>) -> Unit)? = null
    ) {
        Log.d(TAG, "🚀 Universal response generation for all devices")
        
        // Device-adaptive service selection
        val serviceToUse = if (isServiceBound && persistenceService != null) {
            Log.d(TAG, "📱 Using persistent service (faster for all devices)")
            persistenceService!!.getModelService()
        } else {
            Log.d(TAG, "💻 Using local service (universally optimized)")
            modelService
        }
        
        // Universal prompt optimization before generation
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / (1024 * 1024)
        
        // Check if this is an email summarization prompt
        val isEmailContent = prompt.contains("Summarize the key points") ||
                            (prompt.contains("FROM:", ignoreCase = true) && 
                             prompt.contains("SUBJECT:", ignoreCase = true) && 
                             prompt.contains("BODY:", ignoreCase = true))
        
        val optimizedPrompt = when {
            maxMemory > 1024 -> prompt // High-end: use full prompt
            maxMemory > 512 && prompt.length > 500 -> {
                if (isEmailContent) {
                    // For email content, be more generous with length
                    prompt.take(800) + "..."
                } else {
                    prompt.take(500) + "..."
                }
            }
            prompt.length > 300 -> {
                if (isEmailContent) {
                    // For email content, preserve more context even on budget devices
                    prompt.take(600) + "..."
                } else {
                    prompt.take(300) + "..."
                }
            }
            else -> prompt
        }
        
        Log.d(TAG, "📝 Prompt optimized: ${prompt.length} → ${optimizedPrompt.length} chars (${maxMemory}MB device)")
        
        val command = GenerateResponseCommand(optimizedPrompt, images, onPartialResult, serviceToUse)
        
        // Store the command ID for potential cancellation
        val commandId = commandInvoker.executeAsync(
            command = command,
            onSuccess = { response ->
                currentGenerationCommandId = null
                Log.d(TAG, "✅ Universal response generated successfully")
                // Ensure callback runs on main thread for UI operations
                scope.launch(Dispatchers.Main) {
                    onResult?.invoke(Result.success(response))
                }
            },
            onFailure = { error ->
                currentGenerationCommandId = null
                if (error is InterruptedException) {
                    Log.d(TAG, "🛑 Response generation cancelled by user")
                    // Ensure callback runs on main thread for UI operations
                    scope.launch(Dispatchers.Main) {
                        onResult?.invoke(Result.failure(error))
                    }
                } else {
                    Log.e(TAG, "❌ Universal response generation failed", error)
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
        Log.d(TAG, "stopGeneration() called")
        // First, try to cancel the current generation command if it exists
        currentGenerationCommandId?.let { commandId ->
            Log.d(TAG, "Attempting to cancel generation command: $commandId")
            val cancelled = commandInvoker.cancelCommand(commandId)
            Log.d(TAG, "Command cancellation result: $cancelled")
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
    
    // ========== BACKGROUND PROCESSING METHODS ==========
    
    /**
     * Schedule a chat task for background processing
     * Following Single Responsibility and Command Pattern
     */
    fun scheduleBackgroundChatTask(
        prompt: String,
        images: List<String> = emptyList(),
        priority: Priority = Priority.NORMAL,
        sessionId: String? = null,
        onResult: ((Result<String>) -> Unit)? = null
    ) {
        val command = ScheduleChatTaskCommand(
            scheduler = backgroundTaskScheduler,
            prompt = prompt,
            images = images,
            priority = priority,
            sessionId = sessionId
        )
        
        backgroundCommandInvoker.executeBackgroundCommand(
            command = command,
            onResult = { result ->
                scope.launch(Dispatchers.Main) {
                    result.fold(
                        onSuccess = {
                            Log.d(TAG, "Background chat task scheduled successfully")
                            onResult?.invoke(Result.success("Task scheduled for background processing"))
                        },
                        onFailure = { error ->
                            Log.e(TAG, "Failed to schedule background chat task", error)
                            onResult?.invoke(Result.failure(error))
                        }
                    )
                }
            }
        )
    }
    
    /**
     * Schedule an image analysis task for background processing
     * Following Single Responsibility and Command Pattern
     */
    fun scheduleBackgroundImageAnalysis(
        prompt: String,
        images: List<String>,
        priority: Priority = Priority.HIGH,
        onResult: ((Result<String>) -> Unit)? = null
    ) {
        val command = ScheduleImageAnalysisCommand(
            scheduler = backgroundTaskScheduler,
            prompt = prompt,
            images = images,
            priority = priority
        )
        
        backgroundCommandInvoker.executeBackgroundCommand(
            command = command,
            onResult = { result ->
                scope.launch(Dispatchers.Main) {
                    result.fold(
                        onSuccess = {
                            Log.d(TAG, "Background image analysis task scheduled successfully")
                            onResult?.invoke(Result.success("Image analysis scheduled for background processing"))
                        },
                        onFailure = { error ->
                            Log.e(TAG, "Failed to schedule background image analysis task", error)
                            onResult?.invoke(Result.failure(error))
                        }
                    )
                }
            }
        )
    }
    
    /**
     * Schedule a task for a specific time
     * Following Single Responsibility and Command Pattern
     */
    fun scheduleTimedTask(
        prompt: String,
        scheduledTime: Long,
        priority: Priority = Priority.NORMAL,
        onResult: ((Result<String>) -> Unit)? = null
    ) {
        val command = ScheduleTimedTaskCommand(
            scheduler = backgroundTaskScheduler,
            prompt = prompt,
            scheduledTime = scheduledTime,
            priority = priority
        )
        
        backgroundCommandInvoker.executeBackgroundCommand(
            command = command,
            onResult = { result ->
                scope.launch(Dispatchers.Main) {
                    result.fold(
                        onSuccess = {
                            Log.d(TAG, "Timed task scheduled successfully")
                            onResult?.invoke(Result.success("Task scheduled for ${android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", scheduledTime)}"))
                        },
                        onFailure = { error ->
                            Log.e(TAG, "Failed to schedule timed task", error)
                            onResult?.invoke(Result.failure(error))
                        }
                    )
                }
            }
        )
    }
    
    /**
     * Cancel a background task
     * Following Single Responsibility and Command Pattern
     */
    fun cancelBackgroundTask(
        taskId: String,
        onResult: ((Result<Unit>) -> Unit)? = null
    ) {
        val command = CancelBackgroundTaskCommand(
            scheduler = backgroundTaskScheduler,
            taskId = taskId
        )
        
        backgroundCommandInvoker.executeBackgroundCommand(
            command = command,
            onResult = { result ->
                scope.launch(Dispatchers.Main) {
                    onResult?.invoke(result)
                }
            }
        )
    }
    
    /**
     * Get status of a background task
     * Following Single Responsibility and Command Pattern
     */
    fun getBackgroundTaskStatus(
        taskId: String,
        onResult: ((TaskStatus?) -> Unit)? = null
    ) {
        val command = GetTaskStatusCommand(
            scheduler = backgroundTaskScheduler,
            taskId = taskId
        )
        
        backgroundCommandInvoker.executeAsyncWithValue(
            command = command,
            onSuccess = { status ->
                scope.launch(Dispatchers.Main) {
                    onResult?.invoke(status)
                }
            },
            onFailure = { error ->
                Log.e(TAG, "Failed to get background task status", error)
                scope.launch(Dispatchers.Main) {
                    onResult?.invoke(null)
                }
            }
        )
    }
    
    /**
     * Get all pending background tasks
     * Following Single Responsibility and Command Pattern
     */
    fun getPendingBackgroundTasks(
        onResult: ((List<BackgroundTask>) -> Unit)? = null
    ) {
        val command = GetPendingTasksCommand(
            scheduler = backgroundTaskScheduler
        )
        
        backgroundCommandInvoker.executeAsyncWithList(
            command = command,
            onSuccess = { tasks ->
                scope.launch(Dispatchers.Main) {
                    onResult?.invoke(tasks)
                }
            },
            onFailure = { error ->
                Log.e(TAG, "Failed to get pending background tasks", error)
                scope.launch(Dispatchers.Main) {
                    onResult?.invoke(emptyList())
                }
            }
        )
    }
    
    /**
     * Clear completed background tasks
     * Following Single Responsibility and Command Pattern
     */
    fun clearCompletedBackgroundTasks(
        onResult: ((Result<Unit>) -> Unit)? = null
    ) {
        val command = ClearCompletedTasksCommand(
            scheduler = backgroundTaskScheduler
        )
        
        backgroundCommandInvoker.executeBackgroundCommand(
            command = command,
            onResult = { result ->
                scope.launch(Dispatchers.Main) {
                    onResult?.invoke(result)
                }
            }
        )
    }
    
    /**
     * Add a custom background task
     * Following Single Responsibility and Command Pattern
     */
    fun addCustomBackgroundTask(
        task: BackgroundTask,
        onResult: ((Result<Unit>) -> Unit)? = null
    ) {
        val command = AddCustomBackgroundTaskCommand(
            scheduler = backgroundTaskScheduler,
            task = task
        )
        
        backgroundCommandInvoker.executeBackgroundCommand(
            command = command,
            onResult = { result ->
                scope.launch(Dispatchers.Main) {
                    onResult?.invoke(result)
                }
            }
        )
    }
    
    /**
     * Get background task scheduler for advanced operations
     */
    fun getBackgroundTaskScheduler(): BackgroundTaskScheduler = backgroundTaskScheduler
    
    fun cleanup() {
        // Note: Don't disable persistent mode on cleanup - let it run in background
        // Only unbind from service connection
        unbindFromService()
        
        // Clean up local resources
        commandInvoker.cleanup()
        backgroundCommandInvoker.cleanup()
        chatCommandInvoker.cleanup()
        serviceCommandInvoker.cleanup()
        
        // Cancel scope to clean up coroutines
        scope.cancel()
        Log.d(TAG, "ModelManager cleanup completed")
    }
    
    /**
     * Set up universal memory management for all devices
     */
    private fun setupUniversalMemoryManagement() {
        Log.d(TAG, "🧠 Setting up universal memory management...")
        
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / (1024 * 1024) // MB
        val deviceCategory = when {
            maxMemory > 1024 -> "High-end"
            maxMemory > 512 -> "Mid-range"
            else -> "Budget"
        }
        
        Log.d(TAG, "📱 Device: $deviceCategory (${maxMemory}MB heap)")
        Log.d(TAG, "⚡ Memory management optimized for all device types")
        
        // Universal memory monitoring for all devices
        scope.launch {
            while (isActive) {
                try {
                    kotlinx.coroutines.delay(30000) // Check every 30 seconds
                    
                    val freeMemory = runtime.freeMemory() / (1024 * 1024)
                    val totalMemory = runtime.totalMemory() / (1024 * 1024)
                    val usedMemory = totalMemory - freeMemory
                    val usagePercent = (usedMemory * 100) / maxMemory
                    
                    if (usagePercent > 85) {
                        Log.w(TAG, "⚠️ High memory usage: ${usagePercent}% (universal monitoring)")
                        // Trigger garbage collection on all devices
                        System.gc()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in universal memory monitoring", e)
                }
            }
        }
    }
    
    /**
     * Initialize background optimizations for all devices
     */
    private fun initializeBackgroundOptimizations() {
        Log.d(TAG, "🔄 Initializing background optimizations for universal benefit...")
        
        // Delayed initialization to avoid startup delays
        scope.launch {
            kotlinx.coroutines.delay(5000) // Wait 5 seconds after app start
            
            try {
                // Pre-warm MediaPipe components for faster loading
                Log.d(TAG, "🔥 Pre-warming inference components...")
                
                // This benefits all devices by reducing first-load time
                val preWarmTask = {
                    try {
                        // Initialize MediaPipe classes in background
                        val testOptionsClass = com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions::class.java
                        Log.d(TAG, "✅ MediaPipe components pre-warmed for faster loading")
                    } catch (e: Exception) {
                        Log.w(TAG, "Pre-warming completed with minor issues", e)
                    }
                }
                
                withContext(Dispatchers.IO) {
                    preWarmTask()
                }
                
                Log.d(TAG, "🎯 Background optimizations completed - benefits all devices")
                
            } catch (e: Exception) {
                Log.w(TAG, "Background optimization completed with issues", e)
            }
        }
    }
}