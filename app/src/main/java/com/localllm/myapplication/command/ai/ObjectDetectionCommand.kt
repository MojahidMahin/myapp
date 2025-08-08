package com.localllm.myapplication.command.ai

import android.graphics.Bitmap
import com.localllm.myapplication.data.AIResult
import com.localllm.myapplication.data.DetectionResult
import com.localllm.myapplication.service.ai.ObjectDetectionService

class ObjectDetectionCommand(
    private val objectDetectionService: ObjectDetectionService,
    private val image: Bitmap
) : AIProcessingCommand<List<DetectionResult>>() {

    override suspend fun process(): AIResult<List<DetectionResult>> {
        return objectDetectionService.detectObjects(image)
    }
}