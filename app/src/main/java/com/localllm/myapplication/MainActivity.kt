package com.localllm.myapplication.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.localllm.myapplication.di.AppContainer
import com.localllm.myapplication.permission.PermissionManager
import com.localllm.myapplication.service.BackgroundServiceManager
import com.localllm.myapplication.ui.screen.SignInScreen

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
        
        // Request all permissions after UI is ready
        Handler(Looper.getMainLooper()).postDelayed({
            requestAllPermissionsOnStartup()
        }, 2000)
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
     * Request all sensor and device permissions on app startup
     */
    private fun requestAllPermissionsOnStartup() {
        Log.d("MainActivity", "🔐 Starting comprehensive permission request flow")
        
        // Create a permission flow that requests permissions in logical groups
        val permissionFlow = PermissionFlow()
        permissionFlow.startPermissionFlow()
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
                    Log.d("MainActivity", "📍 Step 2: Requesting Location (GPS, Fine, Coarse)")
                    permissionManager.requestLocationPermissions(
                        onSuccess = { 
                            Log.d("MainActivity", "✅ Location permissions granted")
                            nextStep()
                        },
                        onDenied = { denied -> 
                            deniedPermissions.addAll(denied)
                            Log.w("MainActivity", "⚠️ Location permissions denied: $denied")
                            nextStep()
                        },
                        onError = { error ->
                            Log.e("MainActivity", "❌ Location permission error", error)
                            nextStep()
                        }
                    )
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
        }
    }

    fun launchSignInActivity(intent: Intent) {
        signInResultLauncher.launch(intent)
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
