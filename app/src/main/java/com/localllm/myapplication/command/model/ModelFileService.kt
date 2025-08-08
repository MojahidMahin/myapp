package com.localllm.myapplication.command.model

import android.net.Uri

/**
 * Service interface for model file operations
 * Following Interface Segregation Principle - focused on file operations only
 */
interface ModelFileService {
    suspend fun pickModelFile(onResult: (Result<String>) -> Unit)
    fun validateModelFile(fileName: String): Boolean
    fun getAvailableModels(): List<String>
    suspend fun copyModelToCache(sourcePath: String): Result<String>
}

/**
 * Callback interface for file picker operations
 * Following Dependency Inversion Principle
 */
interface FilePickerCallback {
    fun onFileSelected(uri: Uri)
    fun onFileCancelled()
    fun onFileError(error: String)
}