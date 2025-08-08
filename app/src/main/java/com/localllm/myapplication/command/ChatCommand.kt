package com.localllm.myapplication.command

import android.graphics.Bitmap
import android.util.Log
import com.localllm.myapplication.data.ChatMessage
import com.localllm.myapplication.data.MessageType
import com.localllm.myapplication.service.ai.MediaPipeLLMService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChatCommand(
    private val mediaPipeLLMService: MediaPipeLLMService,
    private val prompt: String,
    private val image: Bitmap? = null,
    private val onResponse: (ChatMessage) -> Unit,
    private val onPartialResponse: ((String) -> Unit)? = null,
    private val onError: (Exception) -> Unit = {}
) : BackgroundCommand {

    companion object {
        private const val TAG = "ChatCommand"
    }

    override fun execute() {
        Log.d(TAG, "Processing chat request with MediaPipe: ${getExecutionTag()}")
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val images = if (image != null) listOf(image) else emptyList()
                
                var currentPartialResponse = ""
                val result = mediaPipeLLMService.generateResponse(
                    prompt = prompt,
                    images = images,
                    onPartialResult = { partialText ->
                        currentPartialResponse = partialText
                        onPartialResponse?.invoke(partialText)
                    }
                )
                
                result.fold(
                    onSuccess = { responseText ->
                        val finalResponse = if (responseText.isNotEmpty()) responseText else currentPartialResponse
                        val responseMessage = ChatMessage(
                            text = finalResponse,
                            image = null,
                            isFromUser = false,
                            messageType = if (image != null) MessageType.MULTIMODAL else MessageType.TEXT_ONLY
                        )
                        
                        onResponse(responseMessage)
                        onExecutionComplete()
                    },
                    onFailure = { error ->
                        Log.e(TAG, "MediaPipe chat failed", error)
                        onError(error as Exception)
                        onExecutionFailed(error as Exception)
                    }
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Chat command failed", e)
                onError(e)
                onExecutionFailed(e)
            }
        }
    }

    override fun canExecuteInBackground(): Boolean = true

    override fun getExecutionTag(): String = 
        "ChatCommand_${if (image != null) "multimodal" else "text"}_${System.currentTimeMillis()}"

    override fun onExecutionComplete() {
        Log.d(TAG, "Chat command completed successfully")
    }

    override fun onExecutionFailed(error: Exception) {
        Log.e(TAG, "Chat command failed", error)
    }
}