package com.localllm.myapplication.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.localllm.myapplication.R
import com.localllm.myapplication.command.model.ModelService
import com.localllm.myapplication.command.model.ModelStateRepository
import com.localllm.myapplication.command.model.ModelStateRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Background service that keeps the model loaded persistently
 * Following SOLID principles:
 * S - Single Responsibility: Only handles persistent model management
 * O - Open/Closed: Extensible through ModelService interface
 * L - Liskov Substitution: Uses ModelService abstraction
 * I - Interface Segregation: Focused interface for persistence
 * D - Dependency Inversion: Depends on ModelService abstraction
 */
class ModelPersistenceService : Service() {
    
    companion object {
        private const val TAG = "ModelPersistenceService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "model_persistence_channel"
        private const val CHANNEL_NAME = "Model Persistence"
        
        // Service actions following Command pattern
        const val ACTION_START_PERSISTENT_MODE = "action_start_persistent_mode"
        const val ACTION_STOP_PERSISTENT_MODE = "action_stop_persistent_mode"
        const val ACTION_LOAD_MODEL = "action_load_model"
        const val ACTION_UNLOAD_MODEL = "action_unload_model"
        const val ACTION_STOP_GENERATION = "action_stop_generation"
        
        // Intent extras
        const val EXTRA_MODEL_PATH = "extra_model_path"
        
        /**
         * Start the persistent model service
         */
        fun startPersistentMode(context: Context) {
            val intent = Intent(context, ModelPersistenceService::class.java).apply {
                action = ACTION_START_PERSISTENT_MODE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        /**
         * Stop the persistent model service
         */
        fun stopPersistentMode(context: Context) {
            val intent = Intent(context, ModelPersistenceService::class.java).apply {
                action = ACTION_STOP_PERSISTENT_MODE
            }
            context.startService(intent)
        }
        
        /**
         * Load model in persistent service
         */
        fun loadModelInService(context: Context, modelPath: String) {
            val intent = Intent(context, ModelPersistenceService::class.java).apply {
                action = ACTION_LOAD_MODEL
                putExtra(EXTRA_MODEL_PATH, modelPath)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
    
    // Service binder for client communication
    inner class ModelPersistenceBinder : Binder() {
        fun getService(): ModelPersistenceService = this@ModelPersistenceService
    }
    
    private val binder = ModelPersistenceBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // Following Dependency Inversion Principle - depend on abstractions
    private lateinit var stateRepository: ModelStateRepository
    private lateinit var modelService: ModelService
    private var isPersistentModeActive = false
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "ModelPersistenceService created")
        
        // Initialize dependencies following Dependency Injection
        stateRepository = ModelStateRepositoryImpl(this)
        modelService = ModelServiceImpl(this, stateRepository)
        
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START_PERSISTENT_MODE -> {
                startPersistentMode()
            }
            ACTION_STOP_PERSISTENT_MODE -> {
                stopPersistentMode()
            }
            ACTION_LOAD_MODEL -> {
                val modelPath = intent.getStringExtra(EXTRA_MODEL_PATH)
                if (modelPath != null) {
                    loadModelInBackground(modelPath)
                }
            }
            ACTION_UNLOAD_MODEL -> {
                unloadModelInBackground()
            }
            ACTION_STOP_GENERATION -> {
                stopGenerationInBackground()
            }
        }
        
        // Return START_STICKY to restart service if killed by system
        return START_STICKY
    }
    
    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "Service bound")
        return binder
    }
    
    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        scope.cancel()
        
        // Clean up model service if needed
        if (modelService.isModelLoaded()) {
            scope.launch {
                modelService.unloadModel()
            }
        }
        
        super.onDestroy()
    }
    
    /**
     * Start persistent mode with foreground notification
     * Following Single Responsibility Principle
     */
    private fun startPersistentMode() {
        if (isPersistentModeActive) {
            Log.d(TAG, "Persistent mode already active")
            return
        }
        
        isPersistentModeActive = true
        val notification = createPersistentNotification("Model service running")
        startForeground(NOTIFICATION_ID, notification)
        
        Log.d(TAG, "Persistent mode started - service ready for model loading")
    }
    
    /**
     * Stop persistent mode
     * Following Single Responsibility Principle
     */
    private fun stopPersistentMode() {
        isPersistentModeActive = false
        
        // Unload model before stopping
        if (modelService.isModelLoaded()) {
            scope.launch {
                modelService.unloadModel()
                stopSelf()
            }
        } else {
            stopSelf()
        }
        
        Log.d(TAG, "Persistent mode stopped")
    }
    
    /**
     * Load model in background using Command pattern principles
     */
    private fun loadModelInBackground(modelPath: String) {
        scope.launch {
            try {
                Log.d(TAG, "Loading model in background: $modelPath")
                updateNotification("Loading model...")
                
                val result = modelService.loadModel(modelPath)
                result.fold(
                    onSuccess = {
                        Log.d(TAG, "Model loaded successfully in background")
                        updateNotification("Model loaded: ${getModelDisplayName(modelPath)}")
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to load model in background", error)
                        updateNotification("Failed to load model")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception loading model in background", e)
                updateNotification("Error loading model")
            }
        }
    }
    
    /**
     * Unload model in background
     */
    private fun unloadModelInBackground() {
        scope.launch {
            try {
                Log.d(TAG, "Unloading model in background")
                updateNotification("Unloading model...")
                
                val result = modelService.unloadModel()
                result.fold(
                    onSuccess = {
                        Log.d(TAG, "Model unloaded successfully in background")
                        updateNotification("Model service running")
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to unload model in background", error)
                        updateNotification("Error unloading model")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception unloading model in background", e)
                updateNotification("Error unloading model")
            }
        }
    }
    
    /**
     * Stop generation in background
     */
    private fun stopGenerationInBackground() {
        scope.launch {
            try {
                Log.d(TAG, "Stopping generation in background")
                
                val result = modelService.stopGeneration()
                result.fold(
                    onSuccess = {
                        Log.d(TAG, "Generation stopped successfully in background")
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to stop generation in background", error)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception stopping generation in background", e)
            }
        }
    }
    
    /**
     * Create notification channel for persistent service
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the model loaded in background"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Create persistent notification
     */
    private fun createPersistentNotification(message: String): Notification {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Local LLM Service")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification) // You'll need to add this icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    /**
     * Update notification with new message
     */
    private fun updateNotification(message: String) {
        if (isPersistentModeActive) {
            val notification = createPersistentNotification(message)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }
    
    /**
     * Get display name for model path
     */
    private fun getModelDisplayName(modelPath: String): String {
        return modelPath.substringAfterLast("/").substringBeforeLast(".")
    }
    
    // Public interface for bound clients
    /**
     * Get model service instance for direct access
     * Following Dependency Inversion Principle
     */
    fun getModelService(): ModelService = modelService
    
    /**
     * Check if persistent mode is active
     */
    fun isPersistentModeActive(): Boolean = isPersistentModeActive
    
    /**
     * Get model state flows for reactive UI
     */
    fun getModelStateFlows(): ModelStateFlows {
        return ModelStateFlows(
            isModelLoaded = modelService.isModelLoadedFlow,
            isModelLoading = modelService.isModelLoadingFlow,
            currentModelPath = modelService.currentModelPathFlow,
            modelLoadError = modelService.modelLoadErrorFlow,
            isGenerating = modelService.isGeneratingFlow
        )
    }
}

/**
 * Data class to hold model state flows
 */
data class ModelStateFlows(
    val isModelLoaded: StateFlow<Boolean>,
    val isModelLoading: StateFlow<Boolean>,
    val currentModelPath: StateFlow<String?>,
    val modelLoadError: StateFlow<String?>,
    val isGenerating: StateFlow<Boolean>
)