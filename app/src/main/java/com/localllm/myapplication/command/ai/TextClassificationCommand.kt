package com.localllm.myapplication.command.ai

import com.localllm.myapplication.data.AIResult
import com.localllm.myapplication.data.ClassificationResult
import com.localllm.myapplication.service.ai.TextClassificationService

class TextClassificationCommand(
    private val textClassificationService: TextClassificationService,
    private val text: String
) : AIProcessingCommand<List<ClassificationResult>>() {

    override suspend fun process(): AIResult<List<ClassificationResult>> {
        return textClassificationService.classifyText(text)
    }
}