package com.localllm.myapplication.service.ai

import android.content.Context
import android.graphics.Bitmap
import com.localllm.myapplication.command.ai.*
import com.localllm.myapplication.config.ConfigLoader
import com.localllm.myapplication.data.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class AIProcessingFacade(private val context: Context) {
    
    private val configLoader = ConfigLoader(context)
    
    private val objectDetectionService by lazy { ObjectDetectionService(context, configLoader) }
    private val imageClassificationService by lazy { ImageClassificationService(context, configLoader) }
    private val faceDetectionService by lazy { FaceDetectionService(context, configLoader) }
    private val textClassificationService by lazy { TextClassificationService(context, configLoader) }
    private val languageDetectionService by lazy { LanguageDetectionService(context, configLoader) }
    
    suspend fun processImage(bitmap: Bitmap): ImageProcessingResults = coroutineScope {
        val objectDetectionDeferred = async {
            ObjectDetectionCommand(objectDetectionService, bitmap).executeAsync()
        }
        val imageClassificationDeferred = async {
            ImageClassificationCommand(imageClassificationService, bitmap).executeAsync()
        }
        val faceDetectionDeferred = async {
            FaceDetectionCommand(faceDetectionService, bitmap).executeAsync()
        }
        
        val results = awaitAll(objectDetectionDeferred, imageClassificationDeferred, faceDetectionDeferred)
        
        ImageProcessingResults(
            objectDetection = results[0] as AIResult<List<DetectionResult>>,
            imageClassification = results[1] as AIResult<List<ClassificationResult>>,
            faceDetection = results[2] as AIResult<List<FaceDetectionResult>>
        )
    }
    
    suspend fun processText(text: String): TextProcessingResults = coroutineScope {
        val textClassificationDeferred = async {
            TextClassificationCommand(textClassificationService, text).executeAsync()
        }
        val languageDetectionDeferred = async {
            LanguageDetectionCommand(languageDetectionService, text).executeAsync()
        }
        
        val results = awaitAll(textClassificationDeferred, languageDetectionDeferred)
        
        TextProcessingResults(
            textClassification = results[0] as AIResult<List<ClassificationResult>>,
            languageDetection = results[1] as AIResult<List<LanguageDetectionResult>>
        )
    }
    
    fun cleanup() {
        objectDetectionService.cleanup()
        imageClassificationService.cleanup()
        faceDetectionService.cleanup()
        textClassificationService.cleanup()
        languageDetectionService.cleanup()
    }
}

data class ImageProcessingResults(
    val objectDetection: AIResult<List<DetectionResult>>,
    val imageClassification: AIResult<List<ClassificationResult>>,
    val faceDetection: AIResult<List<FaceDetectionResult>>
)

data class TextProcessingResults(
    val textClassification: AIResult<List<ClassificationResult>>,
    val languageDetection: AIResult<List<LanguageDetectionResult>>
)