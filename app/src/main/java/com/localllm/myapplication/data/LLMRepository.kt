package com.localllm.myapplication.data

import android.graphics.Bitmap

interface LLMRepository {
    suspend fun loadModel(modelPath: String): Boolean
    suspend fun generateTextResponse(prompt: String): String
    suspend fun generateMultimodalResponse(prompt: String, image: Bitmap): String
    fun isModelLoaded(): Boolean
    fun unloadModel()
}