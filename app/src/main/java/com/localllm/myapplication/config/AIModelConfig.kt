package com.localllm.myapplication.config

import com.google.gson.annotations.SerializedName

data class AIModelConfig(
    @SerializedName("vision_models")
    val visionModels: VisionModels = VisionModels(),
    
    @SerializedName("text_models")
    val textModels: TextModels = TextModels(),
    
    @SerializedName("model_assets_path")
    val modelAssetsPath: String = "models/"
)

data class VisionModels(
    @SerializedName("object_detection")
    val objectDetection: ModelInfo = ModelInfo(
        modelPath = "efficientdet_lite0.tflite",
        threshold = 0.5f,
        maxResults = 5
    ),
    
    @SerializedName("image_classification")
    val imageClassification: ModelInfo = ModelInfo(
        modelPath = "efficientnet_lite0.tflite",
        threshold = 0.3f,
        maxResults = 3
    ),
    
    @SerializedName("face_detection")
    val faceDetection: ModelInfo = ModelInfo(
        modelPath = "face_detection_short_range.tflite",
        threshold = 0.5f,
        maxResults = 10
    )
)

data class TextModels(
    @SerializedName("text_classification")
    val textClassification: ModelInfo = ModelInfo(
        modelPath = "text_classifier.tflite",
        threshold = 0.6f,
        maxResults = 5
    ),
    
    @SerializedName("language_detection")
    val languageDetection: ModelInfo = ModelInfo(
        modelPath = "language_detector.tflite",
        threshold = 0.7f,
        maxResults = 3
    )
)

data class ModelInfo(
    @SerializedName("model_path")
    val modelPath: String,
    
    @SerializedName("threshold")
    val threshold: Float = 0.5f,
    
    @SerializedName("max_results")
    val maxResults: Int = 5,
    
    @SerializedName("enabled")
    val enabled: Boolean = true
)