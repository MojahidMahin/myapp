package com.localllm.myapplication.command

import android.graphics.Bitmap
import android.util.Log
import com.localllm.myapplication.data.ChatMessage
import com.localllm.myapplication.data.LLMRepository
import com.localllm.myapplication.data.MessageType

class ChatCommand(
    private val llmRepository: LLMRepository,
    private val prompt: String,
    private val image: Bitmap? = null,
    private val onResponse: (ChatMessage) -> Unit,
    private val onError: (Exception) -> Unit = {}
) : BackgroundCommand {

    companion object {
        private const val TAG = "ChatCommand"
    }

    override fun execute() {
        Log.d(TAG, "Processing chat request: ${getExecutionTag()}")
        
        Thread {
            try {
                val responseText = if (image != null) {
                    kotlinx.coroutines.runBlocking {
                        llmRepository.generateMultimodalResponse(prompt, image)
                    }
                } else {
                    kotlinx.coroutines.runBlocking {
                        llmRepository.generateTextResponse(prompt)
                    }
                }
                
                val responseMessage = ChatMessage(
                    text = responseText,
                    image = null,
                    isFromUser = false,
                    messageType = if (image != null) MessageType.MULTIMODAL else MessageType.TEXT_ONLY
                )
                
                onResponse(responseMessage)
                onExecutionComplete()
                
            } catch (e: Exception) {
                Log.e(TAG, "Chat command failed", e)
                onError(e)
                onExecutionFailed(e)
            }
        }.start()
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