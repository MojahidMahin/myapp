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
import com.localllm.myapplication.ui.screen.ChatScreen

class ChatActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ChatActivity"
    }

    private val chatViewModel by lazy { 
        Log.d(TAG, "Creating ChatViewModel...")
        val viewModel = AppContainer.provideChatViewModel(this)
        Log.d(TAG, "ChatViewModel created successfully")
        viewModel
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "ChatActivity onCreate() called")
        
        try {
            Log.d(TAG, "Setting content...")
            setContent {
                Log.d(TAG, "Inside setContent block")
                MaterialTheme {
                    Log.d(TAG, "Inside MaterialTheme block")
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Log.d(TAG, "Inside Surface block, about to call ChatScreen")
                        ChatScreen(viewModel = chatViewModel)
                        Log.d(TAG, "ChatScreen called successfully")
                    }
                }
            }
            Log.d(TAG, "setContent completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            throw e
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            // Clean up LLM resources when activity is finishing
            AppContainer.cleanup()
        }
    }
}