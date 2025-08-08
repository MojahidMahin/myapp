package com.localllm.myapplication.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.localllm.myapplication.di.AppContainer
import com.localllm.myapplication.ui.screen.AIGalleryScreen

class AIGalleryActivity : ComponentActivity() {

    companion object {
        private const val TAG = "AIGalleryActivity"
    }

    private val aiGalleryViewModel by lazy { 
        Log.d(TAG, "Creating AIGalleryViewModel...")
        val viewModel = AppContainer.provideAIGalleryViewModel(this)
        Log.d(TAG, "AIGalleryViewModel created: $viewModel")
        viewModel
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")
        
        try {
            setContent {
                Log.d(TAG, "Inside setContent block")
                MaterialTheme {
                    Log.d(TAG, "Inside MaterialTheme block")
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Log.d(TAG, "Inside Surface block, about to call AIGalleryScreen")
                        AIGalleryScreen(
                            viewModel = aiGalleryViewModel,
                            onNavigateBack = { finish() }
                        )
                        Log.d(TAG, "AIGalleryScreen called successfully")
                    }
                }
            }
            Log.d(TAG, "setContent completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called")
    }
}