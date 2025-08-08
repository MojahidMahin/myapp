package com.localllm.myapplication.command.ai

import android.graphics.Bitmap
import com.localllm.myapplication.data.AIResult
import com.localllm.myapplication.data.ClassificationResult
import com.localllm.myapplication.service.ai.ImageClassificationService

class ImageClassificationCommand(
    private val imageClassificationService: ImageClassificationService,
    private val image: Bitmap
) : AIProcessingCommand<List<ClassificationResult>>() {

    override suspend fun process(): AIResult<List<ClassificationResult>> {
        return imageClassificationService.classifyImage(image)
    }
}