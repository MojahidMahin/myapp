package com.localllm.myapplication.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.localllm.myapplication.di.AppContainer
import com.localllm.myapplication.permission.PermissionManager
import com.localllm.myapplication.service.BackgroundServiceManager
import com.localllm.myapplication.ui.screen.SignInScreen
import com.localllm.myapplication.service.GeofencingService
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class MainActivity : ComponentActivity() {

    private val authViewModel by lazy { AppContainer.provideAuthViewModel(this) }
    private val backgroundServiceManager by lazy { AppContainer.provideBackgroundServiceManager(this) }
    private val permissionManager by lazy { AppContainer.providePermissionManager(this) }
    
    private val signInResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        authViewModel.handleSignInResult(result.data)
    }
    
    private val changeEmailResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        authViewModel.handleChangeEmailResult(result.data)
    }
    
    private val permissionRequestLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Convert the result map to arrays for compatibility with existing code
        val permissionArray = permissions.keys.toTypedArray()
        val grantResults = permissions.values.map { if (it) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED }.toIntArray()
        permissionManager.handleRequestPermissionsResult(0, permissionArray, grantResults)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d("MainActivity", "🌍 Universal app initialization for all Android devices")
        
        // Background service is now started by MyApplication - no need to start here
        
        setContent {
            SignInScreen(authViewModel)
        }
        
        // Universal optimizations for all devices
        initializeUniversalOptimizations()
        
        // Request critical location permissions first for geofencing
        Handler(Looper.getMainLooper()).postDelayed({
            requestCriticalPermissionsFirst()
        }, 1000)
    }
    
    /**
     * Initialize universal optimizations that benefit all devices
     */
    private fun initializeUniversalOptimizations() {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / (1024 * 1024) // MB
        val availableProcessors = runtime.availableProcessors()
        
        Log.d("MainActivity", "🚀 Device specs: ${maxMemory}MB heap, $availableProcessors cores")
        
        // Universal background optimization
        Handler(Looper.getMainLooper()).postDelayed({
            Log.d("MainActivity", "🔄 Starting universal background optimizations...")
            
            try {
                // Pre-warm model system for faster loading (benefits all devices)
                val modelManager = AppContainer.provideModelManager(this)
                val availableModels = modelManager.getAvailableModels()
                
                if (availableModels.isNotEmpty()) {
                    Log.d("MainActivity", "🔥 Found ${availableModels.size} models - system ready for fast loading")
                    // Don't auto-load, just prepare the system
                }
                
                // Universal memory optimization for all devices
                System.gc() // Initial cleanup
                Log.d("MainActivity", "✅ Universal optimizations completed - benefits all devices")
                
            } catch (e: Exception) {
                Log.w("MainActivity", "Universal optimization completed with minor issues", e)
            }
            
        }, 3000) // Delay to avoid startup interference
    }
    
    /**
     * Request critical location permissions first, then other permissions
     */
    private fun requestCriticalPermissionsFirst() {
        Log.d("MainActivity", "🔐 Starting CRITICAL location permission request for geofencing")
        
        // First, prioritize location permissions (especially background location)
        permissionManager.requestLocationPermissions(
            onSuccess = { 
                Log.d("MainActivity", "✅ CRITICAL location permissions granted - geofencing ready!")
                // Now request other permissions
                requestRemainingPermissions()
            },
            onDenied = { denied -> 
                Log.e("MainActivity", "❌ CRITICAL location permissions denied: $denied")
                Log.e("MainActivity", "⚠️ GEOFENCING WILL NOT WORK RELIABLY!")
                if (denied.contains(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                    showBackgroundLocationWarning()
                }
                // Still request other permissions
                requestRemainingPermissions()
            },
            onError = { error ->
                Log.e("MainActivity", "❌ Location permission error", error)
                requestRemainingPermissions()
            }
        )
    }
    
    /**
     * Request remaining non-critical permissions after location permissions
     */
    private fun requestRemainingPermissions() {
        Log.d("MainActivity", "🔐 Requesting remaining permissions...")
        
        // Create a permission flow that requests remaining permissions
        val permissionFlow = PermissionFlow()
        permissionFlow.startRemainingPermissionFlow()
    }
    
    /**
     * Show warning about background location permission
     */
    private fun showBackgroundLocationWarning() {
        Log.w("MainActivity", "🚨 CRITICAL WARNING: Background location permission denied!")
        Log.w("MainActivity", "📱 TO FIX: Settings > Apps > ${applicationInfo.loadLabel(packageManager)} > Permissions > Location > 'Allow all the time'")
        Log.w("MainActivity", "⚠️ Without this permission, location-based workflows will NOT trigger!")
        
        // Show this warning again in 30 seconds if still not granted
        Handler(Looper.getMainLooper()).postDelayed({
            checkBackgroundLocationAgain()
        }, 30000)
    }
    
    /**
     * Check background location permission again and show guidance
     */
    private fun checkBackgroundLocationAgain() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val hasBackground = androidx.core.app.ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            if (!hasBackground) {
                Log.w("MainActivity", "🔄 Background location still not granted - showing guidance again")
                showBackgroundLocationWarning()
            }
        }
    }
    
    /**
     * Sequential permission request flow for better user experience
     */
    private inner class PermissionFlow {
        private var currentStep = 0
        private val deniedPermissions = mutableListOf<String>()
        
        fun startPermissionFlow() {
            requestNextPermissionGroup()
        }
        
        fun startRemainingPermissionFlow() {
            // Skip location permissions since they were already requested
            currentStep = 1 // Start from notifications
            requestNextPermissionGroup()
        }
        
        private fun requestNextPermissionGroup() {
            when (currentStep) {
                0 -> {
                    Log.d("MainActivity", "📱 Step 1: Requesting Notifications (Essential)")
                    permissionManager.requestNotificationPermission(
                        onSuccess = { 
                            Log.d("MainActivity", "✅ Notifications granted")
                            nextStep()
                        },
                        onDenied = { denied -> 
                            deniedPermissions.addAll(denied)
                            Log.w("MainActivity", "⚠️ Notifications denied: $denied")
                            nextStep()
                        },
                        onError = { error ->
                            Log.e("MainActivity", "❌ Notification permission error", error)
                            nextStep()
                        }
                    )
                }
                1 -> {
                    // Skip location permissions in remaining flow since they were already requested
                    Log.d("MainActivity", "📍 Step 2: Skipping Location (already requested)")
                    nextStep()
                }
                2 -> {
                    Log.d("MainActivity", "📷 Step 3: Requesting Media (Camera, Gallery, Storage)")
                    permissionManager.requestMediaPermissions(
                        onSuccess = { 
                            Log.d("MainActivity", "✅ Media permissions granted")
                            nextStep()
                        },
                        onDenied = { denied -> 
                            deniedPermissions.addAll(denied)
                            Log.w("MainActivity", "⚠️ Media permissions denied: $denied")
                            nextStep()
                        },
                        onError = { error ->
                            Log.e("MainActivity", "❌ Media permission error", error)
                            nextStep()
                        }
                    )
                }
                3 -> {
                    Log.d("MainActivity", "🏃 Step 4: Requesting Sensors (Body, Activity, Motion)")
                    permissionManager.requestSensorPermissions(
                        onSuccess = { 
                            Log.d("MainActivity", "✅ Sensor permissions granted")
                            nextStep()
                        },
                        onDenied = { denied -> 
                            deniedPermissions.addAll(denied)
                            Log.w("MainActivity", "⚠️ Sensor permissions denied: $denied")
                            nextStep()
                        },
                        onError = { error ->
                            Log.e("MainActivity", "❌ Sensor permission error", error)
                            nextStep()
                        }
                    )
                }
                4 -> {
                    Log.d("MainActivity", "🔋 Step 5: Requesting Background App Optimization")
                    permissionManager.requestBackgroundAppPermission(
                        onSuccess = { 
                            Log.d("MainActivity", "✅ Background app permission granted")
                            nextStep()
                        },
                        onDenied = { denied -> 
                            deniedPermissions.addAll(denied)
                            Log.w("MainActivity", "⚠️ Background app permission denied: $denied")
                            nextStep()
                        },
                        onError = { error ->
                            Log.e("MainActivity", "❌ Background app permission error", error)
                            nextStep()
                        }
                    )
                }
                else -> {
                    // Permission flow completed
                    completionSummary()
                }
            }
        }
        
        private fun nextStep() {
            currentStep++
            // Small delay between permission requests for better UX
            Handler(Looper.getMainLooper()).postDelayed({
                requestNextPermissionGroup()
            }, 1000)
        }
        
        private fun completionSummary() {
            val totalRequested = currentStep * 2 // Approximate
            val granted = totalRequested - deniedPermissions.size
            
            Log.i("MainActivity", "🎉 Permission flow completed!")
            Log.i("MainActivity", "📊 Summary: $granted granted, ${deniedPermissions.size} denied")
            
            if (deniedPermissions.isNotEmpty()) {
                Log.w("MainActivity", "⚠️ Denied permissions: ${deniedPermissions.joinToString(", ")}")
                Log.i("MainActivity", "💡 App will work with reduced functionality. Users can re-grant in Settings.")
            } else {
                Log.i("MainActivity", "✨ All permissions granted! App has full sensor and device access.")
            }
            
            // Run geofencing diagnostic after permissions are processed
            runGeofencingDiagnostic()
            
            // If background location is missing, provide direct help
            Handler(Looper.getMainLooper()).postDelayed({
                checkAndPromptBackgroundLocationPermission()
            }, 3000)
        }
    }

    fun launchSignInActivity(intent: Intent) {
        signInResultLauncher.launch(intent)
    }
    
    /**
     * Check and help user with background location permission
     */
    private fun checkAndPromptBackgroundLocationPermission() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val workflowRepository = AppContainer.provideWorkflowRepository(this@MainActivity)
                val workflowEngine = AppContainer.provideWorkflowEngine(this@MainActivity)
                val geofencingService = GeofencingService(this@MainActivity, workflowRepository, workflowEngine)
                
                val settingsIntent = geofencingService.requestBackgroundLocationThroughSettings()
                if (settingsIntent != null) {
                    Log.w("MainActivity", "🔧 Background location permission needed - user should check Settings")
                    Log.w("MainActivity", "📱 To fix: Settings > Apps > MyApplication > Permissions > Location > 'Allow all the time'")
                    
                    // You could also start the settings activity here if desired
                    // startActivity(settingsIntent)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error checking background location permission", e)
            }
        }
    }
    
    /**
     * Run comprehensive geofencing diagnostic
     */
    private fun runGeofencingDiagnostic() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i("MainActivity", "🔍 Running geofencing system diagnostic...")
                
                val workflowRepository = AppContainer.provideWorkflowRepository(this@MainActivity)
                val workflowEngine = AppContainer.provideWorkflowEngine(this@MainActivity)
                val geofencingService = GeofencingService(this@MainActivity, workflowRepository, workflowEngine)
                
                // Run the comprehensive diagnostic
                val diagnosticReport = geofencingService.diagnoseGeofencingStatus()
                
                // Also log it under MainActivity tag for easy filtering
                Log.i("MainActivity", "📋 GEOFENCING DIAGNOSTIC COMPLETE - See GeofencingService logs for full report")
                
            } catch (e: Exception) {
                Log.e("MainActivity", "❌ Failed to run geofencing diagnostic: ${e.message}", e)
            }
        }
    }
    
    fun launchChangeEmailActivity(intent: Intent) {
        changeEmailResultLauncher.launch(intent)
    }
    
    fun requestPermissions(permissions: Array<String>) {
        permissionRequestLauncher.launch(permissions)
    }

    @Deprecated("Use launchSignInActivity() or launchChangeEmailActivity() instead")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            9001 -> authViewModel.handleSignInResult(data)
            9002 -> authViewModel.handleChangeEmailResult(data)
            else -> permissionManager.handleActivityResult(requestCode, resultCode)
        }
    }

    @Deprecated("Use requestPermissions() instead")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        @Suppress("DEPRECATION")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionManager.handleRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Don't cleanup AppContainer when activities finish - background service must persist
        // Only cleanup when the entire app process is being destroyed
        Log.d("MainActivity", "onDestroy called - background service will continue running")
    }
}
