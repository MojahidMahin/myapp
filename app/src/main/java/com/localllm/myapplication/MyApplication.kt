package com.localllm.myapplication

import android.app.Application
import android.util.Log
import com.localllm.myapplication.di.AppContainer

class MyApplication : Application() {

    companion object {
        private const val TAG = "MyApplication"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Application onCreate - starting background service")
        
        // Start background service when app starts
        val backgroundServiceManager = AppContainer.provideBackgroundServiceManager(this)
        backgroundServiceManager.startBackgroundService()
    }

    override fun onTerminate() {
        super.onTerminate()
        Log.d(TAG, "Application onTerminate - stopping background service")
        
        // Only cleanup when the entire app process is terminating
        AppContainer.cleanup()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "Application onLowMemory - background service will continue running")
    }
}