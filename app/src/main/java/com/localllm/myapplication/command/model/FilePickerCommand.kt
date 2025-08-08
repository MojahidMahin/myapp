package com.localllm.myapplication.command.model

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import java.io.File

/**
 * Command for handling file selection and validation
 * Following Command Pattern and Single Responsibility Principle
 */
class FilePickerCommand(
    private val context: Context
) : ModelCommand<String> {
    
    companion object {
        private const val TAG = "FilePickerCommand"
    }
    
    private var selectedUri: Uri? = null
    
    fun setSelectedUri(uri: Uri) {
        selectedUri = uri
    }
    
    override suspend fun execute(): Result<String> {
        return try {
            val uri = selectedUri ?: return Result.failure(Exception("No file selected"))
            
            Log.d(TAG, "Processing selected file URI: $uri")
            
            // Validate file type first
            val fileName = getFileName(uri)
            if (!isValidModelFile(fileName)) {
                return Result.failure(Exception("Invalid model file. Please select a .task or .tflite file"))
            }
            
            // Convert URI to accessible path
            val modelPath = convertUriToPath(uri)
            if (modelPath == null) {
                return Result.failure(Exception("Unable to access the selected file"))
            }
            
            Log.d(TAG, "File successfully processed: $modelPath")
            Result.success(modelPath)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing file selection", e)
            Result.failure(e)
        }
    }
    
    override fun canExecute(): Boolean {
        return selectedUri != null
    }
    
    override fun getDescription(): String {
        return "Process selected model file from device storage"
    }
    
    private fun isValidModelFile(fileName: String?): Boolean {
        if (fileName == null) return false
        return fileName.endsWith(".task", ignoreCase = true) || 
               fileName.endsWith(".tflite", ignoreCase = true) ||
               fileName.contains("gemma", ignoreCase = true)
    }
    
    private fun getFileName(uri: Uri): String? {
        return try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) it.getString(nameIndex) else null
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not get file name", e)
            null
        }
    }
    
    private fun convertUriToPath(uri: Uri): String? {
        return try {
            when {
                uri.scheme == "file" -> {
                    // Direct file path
                    uri.path
                }
                DocumentsContract.isDocumentUri(context, uri) -> {
                    // Copy document to cache for access
                    copyToCache(uri)
                }
                else -> {
                    // Fallback: copy to cache
                    copyToCache(uri)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting URI to path", e)
            null
        }
    }
    
    private fun copyToCache(uri: Uri): String? {
        return try {
            val fileName = getFileName(uri) ?: "model_${System.currentTimeMillis()}.task"
            val inputStream = context.contentResolver.openInputStream(uri)
            
            if (inputStream != null) {
                val cacheFile = File(context.cacheDir, fileName)
                cacheFile.outputStream().use { output ->
                    inputStream.copyTo(output)
                }
                inputStream.close()
                
                Log.d(TAG, "File copied to cache: ${cacheFile.absolutePath}")
                cacheFile.absolutePath
            } else {
                Log.e(TAG, "Could not open input stream for URI")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error copying file to cache", e)
            null
        }
    }
}