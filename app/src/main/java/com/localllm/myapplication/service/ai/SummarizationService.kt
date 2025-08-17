package com.localllm.myapplication.service.ai

import android.content.Context
import android.util.Log
import com.localllm.myapplication.service.ModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Service responsible for text summarization using local LLM
 * Follows Single Responsibility Principle - only handles summarization
 */
interface SummarizationService {
    suspend fun summarizeText(
        text: String,
        maxLength: Int = 100,
        style: SummarizationStyle = SummarizationStyle.CONCISE
    ): Result<String>
    
    suspend fun summarizeEmail(
        subject: String,
        body: String,
        sender: String
    ): Result<EmailSummary>
}

/**
 * Concrete implementation using local LLM model
 */
class LocalLLMSummarizationService(
    private val modelManager: ModelManager,
    private val context: Context
) : SummarizationService {
    
    companion object {
        private const val TAG = "SummarizationService"
    }
    
    override suspend fun summarizeText(
        text: String,
        maxLength: Int,
        style: SummarizationStyle
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Summarizing text of length: ${text.length}")
            
            if (text.isBlank()) {
                return@withContext Result.failure(Exception("Text is empty"))
            }
            
            // Strategy 1: Try AI summarization first
            val aiSummary = tryAISummarization(text, maxLength, style)
            if (aiSummary.isSuccess) {
                Log.d(TAG, "AI summarization successful")
                return@withContext aiSummary
            }
            
            Log.w(TAG, "AI summarization failed: ${aiSummary.exceptionOrNull()?.message}")
            
            // Strategy 2: Fallback to extractive summarization
            Log.i(TAG, "Falling back to extractive summarization")
            val extractiveSummary = createExtractiveSummary(text, maxLength, style)
            
            Log.i(TAG, "Fallback summarization completed successfully")
            Result.success(extractiveSummary)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in summarizeText", e)
            
            // Strategy 3: Emergency fallback - basic truncation with key info
            Log.w(TAG, "Using emergency fallback summarization")
            val emergencySummary = createEmergencySummary(text, maxLength)
            Result.success(emergencySummary)
        }
    }
    
    /**
     * Attempt AI-powered summarization
     */
    private suspend fun tryAISummarization(
        text: String,
        maxLength: Int,
        style: SummarizationStyle
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Check if model is available before attempting
            val isModelReady = checkModelAvailability()
            if (!isModelReady) {
                Log.w(TAG, "LLM model not available, skipping AI summarization")
                return@withContext Result.failure(Exception("LLM model not loaded"))
            }
            
            val prompt = buildSummarizationPrompt(text, maxLength, style)
            
            var summary: String? = null
            var error: Throwable? = null
            
            // Use the model manager to process the summarization
            modelManager.generateResponse(prompt) { result ->
                result.fold(
                    onSuccess = { response ->
                        summary = cleanSummaryResponse(response)
                        Log.d(TAG, "AI summarization successful: ${summary?.take(50)}...")
                    },
                    onFailure = { err ->
                        error = err
                        Log.e(TAG, "AI summarization failed", err)
                    }
                )
            }
            
            // Wait for response with timeout
            var attempts = 0
            while (summary == null && error == null && attempts < 100) {
                kotlinx.coroutines.delay(100)
                attempts++
            }
            
            when {
                summary != null -> Result.success(summary!!)
                error != null -> Result.failure(error!!)
                else -> Result.failure(Exception("AI summarization timeout"))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in AI summarization", e)
            Result.failure(e)
        }
    }
    
    /**
     * Check if LLM model is loaded and ready
     */
    private suspend fun checkModelAvailability(): Boolean {
        return try {
            // Try a simple test query to check if model is responsive
            var isReady = false
            var testCompleted = false
            
            modelManager.generateResponse("test") { result ->
                isReady = result.isSuccess
                testCompleted = true
            }
            
            // Wait for test completion with short timeout
            var attempts = 0
            while (!testCompleted && attempts < 10) {
                delay(50)
                attempts++
            }
            
            isReady && testCompleted
        } catch (e: Exception) {
            Log.w(TAG, "Model availability check failed", e)
            false
        }
    }
    
    /**
     * Create extractive summary using rule-based approach
     */
    private fun createExtractiveSummary(
        text: String,
        maxLength: Int,
        style: SummarizationStyle
    ): String {
        Log.d(TAG, "Creating extractive summary")
        
        // Clean and prepare text
        val sentences = text.split(Regex("[.!?]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.length > 10 }
        
        if (sentences.isEmpty()) {
            return createEmergencySummary(text, maxLength)
        }
        
        // Score sentences based on various factors
        val scoredSentences = sentences.mapIndexed { index, sentence ->
            val score = calculateSentenceScore(sentence, index, sentences.size, text)
            sentence to score
        }.sortedByDescending { it.second }
        
        // Select top sentences up to maxLength
        val selectedSentences = mutableListOf<String>()
        var currentLength = 0
        
        for ((sentence, _) in scoredSentences) {
            val sentenceWords = sentence.split("\\s+".toRegex()).size
            if (currentLength + sentenceWords <= maxLength) {
                selectedSentences.add(sentence)
                currentLength += sentenceWords
            }
            
            if (currentLength >= maxLength * 0.8) break // 80% of target length
        }
        
        // Format based on style
        return when (style) {
            SummarizationStyle.STRUCTURED -> formatStructuredSummary(selectedSentences)
            SummarizationStyle.DETAILED -> selectedSentences.joinToString(". ") + "."
            else -> selectedSentences.firstOrNull() ?: createEmergencySummary(text, maxLength)
        }
    }
    
    /**
     * Calculate importance score for a sentence
     */
    private fun calculateSentenceScore(
        sentence: String,
        position: Int,
        totalSentences: Int,
        fullText: String
    ): Double {
        var score = 0.0
        
        // Position bias - first and last sentences are often important
        score += when {
            position == 0 -> 2.0  // First sentence
            position == totalSentences - 1 -> 1.5  // Last sentence
            position < totalSentences * 0.3 -> 1.2  // Early sentences
            else -> 1.0
        }
        
        // Length bias - medium length sentences are often good
        val wordCount = sentence.split("\\s+".toRegex()).size
        score += when {
            wordCount in 8..25 -> 1.5
            wordCount in 5..30 -> 1.0
            else -> 0.5
        }
        
        // Keyword importance
        val importantKeywords = listOf(
            "important", "urgent", "key", "main", "primary", "essential",
            "required", "request", "please", "action", "needed", "asap",
            "meeting", "deadline", "project", "update", "report"
        )
        
        val keywordCount = importantKeywords.count { keyword ->
            sentence.lowercase().contains(keyword.lowercase())
        }
        score += keywordCount * 0.5
        
        // Email-specific keywords
        val emailKeywords = listOf(
            "subject:", "from:", "to:", "cc:", "bcc:", "dear", "hello", "hi",
            "regards", "sincerely", "thank", "thanks"
        )
        
        val emailKeywordCount = emailKeywords.count { keyword ->
            sentence.lowercase().contains(keyword.lowercase())
        }
        score += emailKeywordCount * 0.3
        
        return score
    }
    
    /**
     * Format sentences as structured summary
     */
    private fun formatStructuredSummary(sentences: List<String>): String {
        return when {
            sentences.isEmpty() -> "No content available for summarization."
            sentences.size == 1 -> "• ${sentences[0]}"
            else -> sentences.take(3).joinToString("\n") { "• $it" }
        }
    }
    
    /**
     * Emergency fallback - basic text truncation with smart boundaries
     */
    private fun createEmergencySummary(text: String, maxLength: Int): String {
        Log.w(TAG, "Using emergency summary generation")
        
        if (text.isBlank()) {
            return "No content available for summarization."
        }
        
        // Try to find a good breaking point
        val words = text.split("\\s+".toRegex())
        
        if (words.size <= maxLength) {
            return text.trim()
        }
        
        // Take first portion and try to end at sentence boundary
        val truncated = words.take(maxLength).joinToString(" ")
        val lastSentenceEnd = truncated.lastIndexOfAny(charArrayOf('.', '!', '?'))
        
        return if (lastSentenceEnd > truncated.length / 2) {
            truncated.substring(0, lastSentenceEnd + 1).trim()
        } else {
            "$truncated...".trim()
        }
    }
    
    override suspend fun summarizeEmail(
        subject: String,
        body: String,
        sender: String
    ): Result<EmailSummary> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Summarizing email from: $sender")
            
            val emailText = "Subject: $subject\nFrom: $sender\nContent: $body"
            val summaryResult = summarizeText(emailText, 150, SummarizationStyle.STRUCTURED)
            
            summaryResult.fold(
                onSuccess = { summary ->
                    val emailSummary = EmailSummary(
                        summary = summary,
                        sender = sender,
                        subject = subject,
                        keyPoints = extractKeyPoints(summary),
                        urgencyLevel = assessUrgency(subject, body, summary)
                    )
                    Result.success(emailSummary)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in summarizeEmail", e)
            Result.failure(e)
        }
    }
    
    private fun buildSummarizationPrompt(
        text: String,
        maxLength: Int,
        style: SummarizationStyle
    ): String {
        val styleInstruction = when (style) {
            SummarizationStyle.CONCISE -> "Provide a very concise summary"
            SummarizationStyle.DETAILED -> "Provide a detailed summary with key points"
            SummarizationStyle.STRUCTURED -> "Provide a structured summary with bullet points"
            SummarizationStyle.KEYWORDS_FOCUSED -> "Summarize focusing on important keywords and actions"
        }
        
        return """
            $styleInstruction of the following text in approximately $maxLength words or less.
            Focus on the main ideas, key information, and actionable items.
            
            Text to summarize:
            $text
            
            Summary:
        """.trimIndent()
    }
    
    private fun cleanSummaryResponse(response: String): String {
        return response
            .trim()
            .removePrefix("Summary:")
            .removePrefix("summary:")
            .trim()
            .take(500) // Ensure reasonable length
    }
    
    private fun extractKeyPoints(summary: String): List<String> {
        // Simple key point extraction - in real implementation, might use LLM
        return summary
            .split(".")
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length > 10 }
            .take(5)
    }
    
    private fun assessUrgency(subject: String, body: String, summary: String): UrgencyLevel {
        val urgentKeywords = listOf(
            "urgent", "asap", "immediately", "emergency", "critical", 
            "deadline", "important", "priority", "rush", "quickly"
        )
        
        val allText = "$subject $body $summary".lowercase()
        val urgentCount = urgentKeywords.count { keyword ->
            allText.contains(keyword)
        }
        
        return when {
            urgentCount >= 3 -> UrgencyLevel.HIGH
            urgentCount >= 1 -> UrgencyLevel.MEDIUM
            else -> UrgencyLevel.LOW
        }
    }
}

/**
 * Different styles of summarization
 */
enum class SummarizationStyle {
    CONCISE,        // Very brief summary
    DETAILED,       // Comprehensive summary
    STRUCTURED,     // Bullet points and organized
    KEYWORDS_FOCUSED // Focus on keywords and actions
}

/**
 * Structured email summary with metadata
 */
data class EmailSummary(
    val summary: String,
    val sender: String,
    val subject: String,
    val keyPoints: List<String>,
    val urgencyLevel: UrgencyLevel
)

/**
 * Urgency levels for prioritization
 */
enum class UrgencyLevel {
    LOW, MEDIUM, HIGH
}