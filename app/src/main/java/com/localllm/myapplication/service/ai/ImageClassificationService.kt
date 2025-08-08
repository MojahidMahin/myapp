package com.localllm.myapplication.service.ai

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifier
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifierResult
import com.localllm.myapplication.config.ConfigLoader
import com.localllm.myapplication.data.AIResult
import com.localllm.myapplication.data.ClassificationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImageClassificationService(
    private val context: Context,
    private val configLoader: ConfigLoader
) {
    private var imageClassifier: ImageClassifier? = null
    
    private fun initializeClassifier(): ImageClassifier {
        return imageClassifier ?: run {
            val options = ImageClassifier.ImageClassifierOptions.builder()
                .setBaseOptions(
                    com.google.mediapipe.tasks.core.BaseOptions.builder()
                        .setModelAssetPath("efficientnet_lite0.tflite")
                        .build()
                )
                .setMaxResults(3)
                .setScoreThreshold(0.5f)
                .build()
            
            val classifier = ImageClassifier.createFromOptions(context, options)
            imageClassifier = classifier
            classifier
        }
    }
    
    suspend fun classifyImage(bitmap: Bitmap): AIResult<List<ClassificationResult>> = withContext(Dispatchers.IO) {
        try {
            val classifier = initializeClassifier()
            
            // Convert Android Bitmap to MediaPipe MPImage
            val mpImage = BitmapImageBuilder(bitmap).build()
            
            // Run inference
            val result: ImageClassifierResult = classifier.classify(mpImage)
            
            // Convert MediaPipe results to app format
            val classifications = result.classificationResult().classifications().firstOrNull()?.categories()
                ?: emptyList()
            
            val classificationResults = classifications.map { category ->
                ClassificationResult(
                    label = category.categoryName() ?: "Unknown",
                    confidence = category.score()
                )
            }
            
            AIResult.Success(classificationResults)
        } catch (e: Exception) {
            AIResult.Error("Image classification failed: ${e.message}")
        }
    }
    
    fun cleanup() {
        imageClassifier?.close()
        imageClassifier = null
    }
}