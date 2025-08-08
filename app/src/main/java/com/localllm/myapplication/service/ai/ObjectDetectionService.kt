package com.localllm.myapplication.service.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult
import com.localllm.myapplication.config.ConfigLoader
import com.localllm.myapplication.data.AIResult
import com.localllm.myapplication.data.DetectionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ObjectDetectionService(
    private val context: Context,
    private val configLoader: ConfigLoader
) {
    private var objectDetector: ObjectDetector? = null
    
    private fun initializeDetector(): ObjectDetector {
        return objectDetector ?: run {
            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(
                    com.google.mediapipe.tasks.core.BaseOptions.builder()
                        .setModelAssetPath("efficientdet_lite0.tflite")
                        .build()
                )
                .setMaxResults(5)
                .setScoreThreshold(0.5f)
                .build()
            
            val detector = ObjectDetector.createFromOptions(context, options)
            objectDetector = detector
            detector
        }
    }
    
    suspend fun detectObjects(bitmap: Bitmap): AIResult<List<DetectionResult>> = withContext(Dispatchers.IO) {
        try {
            val detector = initializeDetector()
            
            // Convert Android Bitmap to MediaPipe MPImage
            val mpImage = BitmapImageBuilder(bitmap).build()
            
            // Run inference
            val result: ObjectDetectorResult = detector.detect(mpImage)
            
            // Convert MediaPipe results to app format
            val detectionResults = result.detections().map { detection ->
                val boundingBox = detection.boundingBox()
                val rectF = RectF(
                    boundingBox.left,
                    boundingBox.top,
                    boundingBox.right,
                    boundingBox.bottom
                )
                
                val category = detection.categories().firstOrNull()
                
                DetectionResult(
                    label = category?.categoryName() ?: "Unknown",
                    confidence = category?.score() ?: 0f,
                    boundingBox = rectF
                )
            }
            
            AIResult.Success(detectionResults)
        } catch (e: Exception) {
            AIResult.Error("Object detection failed: ${e.message}")
        }
    }
    
    fun cleanup() {
        objectDetector?.close()
        objectDetector = null
    }
}