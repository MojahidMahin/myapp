package com.localllm.myapplication.command.ai

import android.util.Log
import com.localllm.myapplication.command.BackgroundCommand
import com.localllm.myapplication.data.PromptTemplate
import com.localllm.myapplication.service.ai.MediaPipeLLMService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PromptLabCommand(
    private val mediaPipeLLMService: MediaPipeLLMService,
    private val promptTemplate: PromptTemplate,
    private val userInput: String,
    private val templateParameters: Map<String, String> = emptyMap(),
    private val onResult: (PromptLabResult) -> Unit,
    private val onPartialResult: ((String) -> Unit)? = null,
    private val onError: (Exception) -> Unit = {}
) : BackgroundCommand {

    companion object {
        private const val TAG = "PromptLabCommand"
    }

    override fun execute() {
        Log.d(TAG, "Processing prompt lab request: ${getExecutionTag()}")
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val fullPrompt = promptTemplate.buildPrompt(userInput, templateParameters)
                val startTime = System.currentTimeMillis()
                
                var currentResponse = ""
                val result = mediaPipeLLMService.generateResponse(
                    prompt = fullPrompt,
                    images = emptyList(),
                    onPartialResult = { partialText ->
                        currentResponse = partialText
                        onPartialResult?.invoke(partialText)
                    }
                )
                
                result.fold(
                    onSuccess = { finalResponse ->
                        val endTime = System.currentTimeMillis()
                        val response = if (finalResponse.isNotEmpty()) finalResponse else currentResponse
                        
                        val promptLabResult = PromptLabResult(
                            template = promptTemplate,
                            userInput = userInput,
                            fullPrompt = fullPrompt,
                            response = response,
                            latencyMs = endTime - startTime,
                            parameters = templateParameters
                        )
                        
                        onResult(promptLabResult)
                        onExecutionComplete()
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Prompt lab execution failed", error)
                        onError(error as Exception)
                        onExecutionFailed(error as Exception)
                    }
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Prompt lab command failed", e)
                onError(e)
                onExecutionFailed(e)
            }
        }
    }

    override fun canExecuteInBackground(): Boolean = true

    override fun getExecutionTag(): String = 
        "PromptLabCommand_${promptTemplate.type.name}_${System.currentTimeMillis()}"

    override fun onExecutionComplete() {
        Log.d(TAG, "Prompt lab command completed successfully")
    }

    override fun onExecutionFailed(error: Exception) {
        Log.e(TAG, "Prompt lab command failed", error)
    }
}

data class PromptLabResult(
    val template: PromptTemplate,
    val userInput: String,
    val fullPrompt: String,
    val response: String,
    val latencyMs: Long,
    val parameters: Map<String, String>
)