package com.localllm.myapplication.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Send
import androidx.compose.ui.graphics.vector.ImageVector

enum class AIFeatureType(
    val id: String,
    val label: String, 
    val description: String,
    val icon: ImageVector
) {
    LLM_CHAT(
        id = "llm_chat",
        label = "AI Chat",
        description = "Chat with on-device large language models",
        icon = Icons.Filled.Send
    ),
    ASK_IMAGE(
        id = "ask_image", 
        label = "Ask Image",
        description = "Ask questions about images with on-device models",
        icon = Icons.Filled.Add
    ),
    AUDIO_TRANSCRIPTION(
        id = "audio_transcription",
        label = "Audio Scribe", 
        description = "Transcribe and translate audio clips",
        icon = Icons.Filled.Add
    ),
    PROMPT_LAB(
        id = "prompt_lab",
        label = "Prompt Lab",
        description = "Single turn use cases with prompt templates",
        icon = Icons.Filled.Send
    ),
    GALLERY_ANALYSIS(
        id = "gallery_analysis",
        label = "Gallery Analysis",
        description = "Smart AI analysis of gallery images with OCR and face detection",
        icon = Icons.Filled.Add
    )
}

data class AIFeature(
    val type: AIFeatureType,
    val isEnabled: Boolean = true,
    val modelRequirements: List<String> = emptyList()
)