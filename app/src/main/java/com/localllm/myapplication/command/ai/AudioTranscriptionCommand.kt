package com.localllm.myapplication.command.ai

import android.util.Log
import com.localllm.myapplication.command.BackgroundCommand
import com.localllm.myapplication.service.ai.MediaPipeLLMService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AudioTranscriptionCommand(
    private val mediaPipeLLMService: MediaPipeLLMService,
    private val audioData: ByteArray,
    private val transcriptionMode: TranscriptionMode = TranscriptionMode.TRANSCRIBE_ONLY,
    private val onResult: (String) -> Unit,
    private val onError: (Exception) -> Unit = {}
) : BackgroundCommand {

    companion object {
        private const val TAG = "AudioTranscriptionCommand"
    }

    override fun execute() {
        Log.d(TAG, "Processing audio transcription request: ${getExecutionTag()}")
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val prompt = when (transcriptionMode) {
                    TranscriptionMode.TRANSCRIBE_ONLY -> "Please transcribe the following audio:"
                    TranscriptionMode.TRANSCRIBE_AND_TRANSLATE -> "Please transcribe and translate the following audio to English:"
                    TranscriptionMode.SUMMARIZE -> "Please transcribe the following audio and provide a brief summary:"
                }
                
                val result = mediaPipeLLMService.generateResponse(
                    prompt = prompt,
                    images = emptyList(),
                    onPartialResult = null // Audio transcription doesn't need streaming
                )
                
                result.fold(
                    onSuccess = { transcription ->
                        onResult(transcription)
                        onExecutionComplete()
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Audio transcription failed", error)
                        onError(error as Exception)
                        onExecutionFailed(error as Exception)
                    }
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Audio transcription command failed", e)
                onError(e)
                onExecutionFailed(e)
            }
        }
    }

    override fun canExecuteInBackground(): Boolean = true

    override fun getExecutionTag(): String = 
        "AudioTranscriptionCommand_${transcriptionMode.name}_${System.currentTimeMillis()}"

    override fun onExecutionComplete() {
        Log.d(TAG, "Audio transcription command completed successfully")
    }

    override fun onExecutionFailed(error: Exception) {
        Log.e(TAG, "Audio transcription command failed", error)
    }
}

enum class TranscriptionMode {
    TRANSCRIBE_ONLY,
    TRANSCRIBE_AND_TRANSLATE,
    SUMMARIZE
}