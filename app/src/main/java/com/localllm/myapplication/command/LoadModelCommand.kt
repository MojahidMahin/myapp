package com.localllm.myapplication.command

import android.util.Log
import com.localllm.myapplication.data.LLMRepository

class LoadModelCommand(
    private val llmRepository: LLMRepository,
    private val modelPath: String,
    private val onSuccess: () -> Unit = {},
    private val onError: (Exception) -> Unit = {}
) : Command {

    companion object {
        private const val TAG = "LoadModelCommand"
    }

    override fun execute() {
        Log.d(TAG, "Loading model from: $modelPath")
        
        Thread {
            try {
                // This will run on background thread
                val success = kotlinx.coroutines.runBlocking {
                    llmRepository.loadModel(modelPath)
                }
                
                if (success) {
                    Log.d(TAG, "Model loaded successfully")
                    onSuccess()
                } else {
                    Log.e(TAG, "Failed to load model")
                    onError(Exception("Failed to load model from $modelPath"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading model", e)
                onError(e)
            }
        }.start()
    }
}