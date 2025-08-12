package com.localllm.myapplication.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.localllm.myapplication.di.AppContainer
import com.localllm.myapplication.permission.PermissionManager
import com.localllm.myapplication.service.BackgroundServiceManager
import com.localllm.myapplication.ui.screen.SignInScreen

class MainActivity : ComponentActivity() {

    private val authViewModel by lazy { AppContainer.provideAuthViewModel(this) }
    private val backgroundServiceManager by lazy { AppContainer.provideBackgroundServiceManager(this) }
    private val permissionManager by lazy { AppContainer.providePermissionManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d("MainActivity", "🌍 Universal app initialization for all Android devices")
        
        // Start background service immediately (permissions will be requested later)
        backgroundServiceManager.startBackgroundService()
        
        setContent {
            SignInScreen(authViewModel)
        }
        
        // Universal optimizations for all devices
        initializeUniversalOptimizations()
        
        // Request permissions after UI is ready (optional)
        // Commented out to prevent ANR during startup
        /*
        Handler(Looper.getMainLooper()).postDelayed({
            permissionManager.requestNotificationPermission(
                onSuccess = { Log.d("MainActivity", "Notification permission granted") },
                onDenied = { Log.w("MainActivity", "Notification permission denied") },
                onError = { Log.e("MainActivity", "Permission error", it) }
            )
        }, 2000)
        */
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            9001 -> authViewModel.handleSignInResult(data)
            9002 -> authViewModel.handleChangeEmailResult(data)
            else -> permissionManager.handleActivityResult(requestCode, resultCode)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionManager.handleRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            AppContainer.cleanup()
        }
    }
}
