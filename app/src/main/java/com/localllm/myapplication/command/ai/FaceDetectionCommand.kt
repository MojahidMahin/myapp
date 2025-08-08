package com.localllm.myapplication.command.ai

import android.graphics.Bitmap
import com.localllm.myapplication.data.AIResult
import com.localllm.myapplication.data.FaceDetectionResult
import com.localllm.myapplication.service.ai.FaceDetectionService

class FaceDetectionCommand(
    private val faceDetectionService: FaceDetectionService,
    private val image: Bitmap
) : AIProcessingCommand<List<FaceDetectionResult>>() {

    override suspend fun process(): AIResult<List<FaceDetectionResult>> {
        return faceDetectionService.detectFaces(image)
    }
}