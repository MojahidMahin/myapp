package com.localllm.myapplication.command.ai

import com.localllm.myapplication.data.AIResult
import com.localllm.myapplication.data.LanguageDetectionResult
import com.localllm.myapplication.service.ai.LanguageDetectionService

class LanguageDetectionCommand(
    private val languageDetectionService: LanguageDetectionService,
    private val text: String
) : AIProcessingCommand<List<LanguageDetectionResult>>() {

    override suspend fun process(): AIResult<List<LanguageDetectionResult>> {
        return languageDetectionService.detectLanguage(text)
    }
}