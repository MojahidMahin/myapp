package com.localllm.myapplication.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localllm.myapplication.service.ai.AIProcessingFacade
import com.localllm.myapplication.service.ai.ImageProcessingResults
import com.localllm.myapplication.service.ai.TextProcessingResults
import kotlinx.coroutines.launch

class AIAnalysisViewModel(application: Application) : AndroidViewModel(application) {
    
    private val aiProcessingFacade = AIProcessingFacade(application)
    
    var isLoading by mutableStateOf(false)
        private set
    
    var errorMessage by mutableStateOf<String?>(null)
        private set
    
    var imageResults by mutableStateOf<ImageProcessingResults?>(null)
        private set
    
    var textResults by mutableStateOf<TextProcessingResults?>(null)
        private set
    
    fun processImage(bitmap: Bitmap) {
        viewModelScope.launch {
            try {
                isLoading = true
                errorMessage = null
                
                val results = aiProcessingFacade.processImage(bitmap)
                imageResults = results
                
            } catch (e: Exception) {
                errorMessage = "Failed to process image: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }
    
    fun processText(text: String) {
        viewModelScope.launch {
            try {
                isLoading = true
                errorMessage = null
                
                val results = aiProcessingFacade.processText(text)
                textResults = results
                
            } catch (e: Exception) {
                errorMessage = "Failed to process text: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }
    
    fun clearResults() {
        imageResults = null
        textResults = null
        errorMessage = null
    }
    
    fun setError(message: String) {
        errorMessage = message
    }
    
    override fun onCleared() {
        super.onCleared()
        aiProcessingFacade.cleanup()
    }
}