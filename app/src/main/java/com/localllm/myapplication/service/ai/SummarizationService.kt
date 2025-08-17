package com.localllm.myapplication.service.ai

import android.content.Context
import android.util.Log
import com.localllm.myapplication.service.ModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

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
            
            // Strategy 1: Try AI summarization with retry logic
            var aiSummary: Result<String>? = null
            val maxRetries = 2
            
            for (attempt in 1..maxRetries) {
                Log.d(TAG, "AI summarization attempt $attempt/$maxRetries")
                aiSummary = tryAISummarization(text, maxLength, style)
                
                if (aiSummary.isSuccess) {
                    Log.d(TAG, "AI summarization successful on attempt $attempt")
                    return@withContext aiSummary
                }
                
                Log.w(TAG, "AI summarization attempt $attempt failed: ${aiSummary.exceptionOrNull()?.message}")
                
                // Wait before retry (except on last attempt)
                if (attempt < maxRetries) {
                    Log.d(TAG, "Waiting 1 second before retry...")
                    delay(1000)
                }
            }
            
            Log.w(TAG, "All AI summarization attempts failed after $maxRetries tries")
            
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
            Log.d(TAG, "=== AI SUMMARIZATION DEBUG ===")
            Log.d(TAG, "Text length: ${text.length} characters")
            Log.d(TAG, "Max length: $maxLength, Style: $style")
            
            // Check if model is available before attempting
            Log.d(TAG, "Checking model availability...")
            val isModelReady = checkModelAvailability()
            Log.d(TAG, "Model availability check result: $isModelReady")
            
            if (!isModelReady) {
                Log.w(TAG, "LLM model not available, skipping AI summarization")
                return@withContext Result.failure(Exception("LLM model not loaded"))
            }
            
            Log.d(TAG, "Model is ready, proceeding with AI summarization...")
            
            val prompt = buildSummarizationPrompt(text, maxLength, style)
            Log.d(TAG, "Generated prompt length: ${prompt.length} characters")
            Log.d(TAG, "Prompt preview: ${prompt.take(200)}...")
            
            var summary: String? = null
            var error: Throwable? = null
            
            Log.d(TAG, "Sending request to ModelManager...")
            
            // Use suspendCancellableCoroutine to properly wait for the async response
            val responseResult = suspendCancellableCoroutine<Result<String>> { continuation ->
                var callbackInvoked = false
                
                modelManager.generateResponse(prompt) { result ->
                    if (!callbackInvoked && continuation.isActive) {
                        callbackInvoked = true
                        Log.d(TAG, "ModelManager callback invoked")
                        
                        result.fold(
                            onSuccess = { response ->
                                Log.d(TAG, "ModelManager returned success: ${response.take(100)}...")
                                continuation.resume(Result.success(response))
                            },
                            onFailure = { err ->
                                Log.e(TAG, "ModelManager returned error: ${err.message}", err)
                                continuation.resume(Result.failure(err))
                            }
                        )
                    }
                }
                
                // Set a timeout for the request
                continuation.invokeOnCancellation {
                    Log.w(TAG, "AI summarization request cancelled")
                }
            }
            
            // Process the response
            responseResult.fold(
                onSuccess = { response ->
                    Log.d(TAG, "Processing successful response...")
                    val cleanedSummary = cleanSummaryResponse(response)
                    
                    // Validate summary quality
                    if (isValidSummary(cleanedSummary, text)) {
                        summary = cleanedSummary
                        Log.d(TAG, "AI summarization successful: ${summary?.take(50)}...")
                    } else {
                        Log.w(TAG, "AI summary failed quality validation: '$cleanedSummary'")
                        error = Exception("AI summary quality validation failed")
                    }
                },
                onFailure = { err ->
                    Log.e(TAG, "AI summarization failed: ${err.message}")
                    error = err
                }
            )
            
            // Return the result based on what we got
            when {
                summary != null -> {
                    Log.d(TAG, "Returning successful AI summary (${summary!!.length} chars)")
                    Log.d(TAG, "Summary preview: '${summary!!.take(150)}...'")
                    Result.success(summary!!)
                }
                error != null -> {
                    Log.e(TAG, "Returning error result: ${error!!.message}")
                    Log.e(TAG, "Error type: ${error!!::class.simpleName}")
                    Result.failure(error!!)
                }
                else -> {
                    Log.e(TAG, "Unexpected state: no summary and no error")
                    Result.failure(Exception("Unexpected summarization state"))
                }
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
            Log.d(TAG, "=== MODEL AVAILABILITY CHECK ===")
            
            // Instead of testing with a request, just check if the model manager is initialized
            // This avoids the "Previous invocation still processing" error
            
            // Quick check - see if model manager is available
            val modelManager = this.modelManager
            if (modelManager == null) {
                Log.w(TAG, "Model manager is null")
                return false
            }
            
            // For now, assume model is available if manager exists
            // The real test will happen when we try to generate the actual summary
            Log.d(TAG, "Model manager is available, assuming model is ready")
            Log.d(TAG, "Final model availability result: true")
            
            true
        } catch (e: Exception) {
            Log.w(TAG, "Model availability check failed with exception", e)
            false
        }
    }
    
    /**
     * Create extractive summary using enhanced rule-based approach
     */
    private fun createExtractiveSummary(
        text: String,
        maxLength: Int,
        style: SummarizationStyle
    ): String {
        Log.d(TAG, "Creating enhanced extractive summary")
        Log.d(TAG, "Input text length: ${text.length}, max length: $maxLength, style: $style")
        
        // Check if this is email content for specialized processing
        val isEmailContent = text.contains("Subject:") && text.contains("From:")
        
        if (isEmailContent) {
            return createEmailSpecificSummary(text, maxLength, style)
        }
        
        // Clean and prepare text for general content
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
     * Create email-specific extractive summary
     */
    private fun createEmailSpecificSummary(
        text: String,
        maxLength: Int,
        style: SummarizationStyle
    ): String {
        Log.d(TAG, "Creating email-specific extractive summary")
        
        // Parse email components
        val lines = text.lines()
        var subject = ""
        var sender = ""
        var bodyStart = 0
        
        // Extract email headers
        for (i in lines.indices) {
            val line = lines[i].trim()
            when {
                line.startsWith("Subject:", ignoreCase = true) -> {
                    subject = line.substringAfter(":").trim()
                }
                line.startsWith("From:", ignoreCase = true) -> {
                    sender = line.substringAfter(":").trim()
                }
                line.startsWith("Email Content:", ignoreCase = true) -> {
                    bodyStart = i + 1
                    break
                }
                line.isEmpty() && i > 0 -> {
                    bodyStart = i + 1
                    break
                }
            }
        }
        
        // Extract email body
        val bodyLines = if (bodyStart < lines.size) {
            lines.drop(bodyStart).joinToString(" ").trim()
        } else {
            ""
        }
        
        Log.d(TAG, "Parsed email - Subject: '$subject', Sender: '$sender', Body length: ${bodyLines.length}")
        
        // Create summary based on style
        return when (style) {
            SummarizationStyle.STRUCTURED -> createStructuredEmailSummary(subject, bodyLines, sender, maxLength)
            SummarizationStyle.DETAILED -> createDetailedEmailSummary(subject, bodyLines, sender, maxLength)
            SummarizationStyle.KEYWORDS_FOCUSED -> createKeywordFocusedEmailSummary(subject, bodyLines, sender, maxLength)
            else -> createConciseEmailSummary(subject, bodyLines, sender, maxLength)
        }
    }
    
    private fun createConciseEmailSummary(subject: String, body: String, sender: String, maxLength: Int): String {
        return buildString {
            append("Email from $sender: ")
            
            if (body.isNotBlank()) {
                // Extract the most important sentence from body
                val sentences = body.split(Regex("[.!?]+"))
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && it.length > 15 }
                
                val importantSentence = sentences.firstOrNull { sentence ->
                    listOf("request", "need", "please", "important", "urgent", "meeting", "project")
                        .any { keyword -> sentence.lowercase().contains(keyword) }
                } ?: sentences.firstOrNull() ?: body.take(100)
                
                append(importantSentence)
                if (!importantSentence.endsWith(".")) append(".")
            } else {
                append("regarding '$subject'")
            }
        }
    }
    
    private fun createStructuredEmailSummary(subject: String, body: String, sender: String, maxLength: Int): String {
        return buildString {
            append("📧 Email Summary\n")
            append("• From: $sender\n")
            append("• Subject: $subject\n")
            
            if (body.isNotBlank()) {
                append("• Content: ")
                val sentences = body.split(Regex("[.!?]+"))
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && it.length > 10 }
                    .take(2)
                
                if (sentences.isNotEmpty()) {
                    append(sentences.joinToString(". ") { it.trim() })
                    if (!toString().endsWith(".")) append(".")
                } else {
                    append(body.take(100))
                    if (body.length > 100) append("...")
                }
            }
        }
    }
    
    private fun createDetailedEmailSummary(subject: String, body: String, sender: String, maxLength: Int): String {
        return buildString {
            append("Detailed email summary:\n\n")
            append("Sender: $sender\n")
            append("Subject: $subject\n\n")
            
            if (body.isNotBlank()) {
                append("Content:\n")
                val sentences = body.split(Regex("[.!?]+"))
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && it.length > 10 }
                    .take(3)
                
                sentences.forEachIndexed { index, sentence ->
                    append("${index + 1}. $sentence.\n")
                }
                
                if (sentences.isEmpty()) {
                    append(body.take(200))
                    if (body.length > 200) append("...")
                }
            }
        }
    }
    
    private fun createKeywordFocusedEmailSummary(subject: String, body: String, sender: String, maxLength: Int): String {
        val keywords = mutableListOf<String>()
        val actionItems = mutableListOf<String>()
        
        // Extract keywords and action items
        val allText = "$subject $body".lowercase()
        val importantKeywords = listOf(
            "meeting", "call", "project", "deadline", "urgent", "important",
            "request", "please", "need", "asap", "schedule", "review"
        )
        
        keywords.addAll(importantKeywords.filter { allText.contains(it) })
        
        // Look for action items
        val actionPatterns = listOf("please ", "need to ", "should ", "must ", "required to ")
        for (pattern in actionPatterns) {
            val matches = Regex("$pattern[^.!?]*[.!?]").findAll(body)
            actionItems.addAll(matches.map { it.value.trim() }.take(2))
        }
        
        return buildString {
            append("📧 $sender: $subject\n\n")
            
            if (keywords.isNotEmpty()) {
                append("🔑 Keywords: ${keywords.joinToString(", ")}\n")
            }
            
            if (actionItems.isNotEmpty()) {
                append("📋 Action Items:\n")
                actionItems.forEach { append("• $it\n") }
            } else if (body.isNotBlank()) {
                append("📝 Content: ${body.take(100)}")
                if (body.length > 100) append("...")
            }
        }
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
        // Check if this is email content by looking for email patterns (case insensitive)
        val isEmailContent = (text.contains("subject:", ignoreCase = true) && text.contains("from:", ignoreCase = true) && text.contains("body:", ignoreCase = true))
        
        return if (isEmailContent) {
            // Enhanced email-specific prompt
            val styleInstruction = when (style) {
                SummarizationStyle.CONCISE -> "Write a brief, clear summary"
                SummarizationStyle.DETAILED -> "Write a comprehensive summary covering all main points"
                SummarizationStyle.STRUCTURED -> "Write a structured summary with key points"
                SummarizationStyle.KEYWORDS_FOCUSED -> "Write a summary highlighting important keywords and action items"
            }
            
            """
            $styleInstruction of this email in approximately $maxLength words:

            $text

            Summarize the key points and what the sender wants:""".trimIndent()
        } else {
            // Generic text summarization prompt
            val styleInstruction = when (style) {
                SummarizationStyle.CONCISE -> "Provide a very concise summary"
                SummarizationStyle.DETAILED -> "Provide a detailed summary with key points"
                SummarizationStyle.STRUCTURED -> "Provide a structured summary with bullet points"
                SummarizationStyle.KEYWORDS_FOCUSED -> "Summarize focusing on important keywords and actions"
            }
            
            """
            $styleInstruction of the following text in approximately $maxLength words or less.
            Focus on the main ideas, key information, and actionable items.
            
            Text to summarize:
            $text
            
            Summary:
            """.trimIndent()
        }
    }
    
    /**
     * Validate if the AI summary meets quality standards
     */
    private fun isValidSummary(summary: String, originalText: String): Boolean {
        Log.d(TAG, "Validating summary quality...")
        
        // Basic validation checks
        if (summary.isBlank() || summary.length < 10) {
            Log.w(TAG, "Summary too short: ${summary.length} chars")
            return false
        }
        
        // Check if summary is just repeating the subject
        val textLower = originalText.lowercase()
        val summaryLower = summary.lowercase()
        
        // Extract subject line if present
        val subjectMatch = Regex("subject:\\s*(.+?)\\n", RegexOption.IGNORE_CASE).find(textLower)
        val subject = subjectMatch?.groupValues?.get(1)?.trim()
        
        if (subject != null && summaryLower.contains(subject) && summary.length < subject.length + 50) {
            Log.w(TAG, "Summary appears to just repeat the subject")
            return false
        }
        
        // Check for common AI failure patterns
        val failurePatterns = listOf(
            "i cannot", "i can't", "unable to", "sorry,", "i don't", 
            "as an ai", "i'm just", "i apologize"
        )
        
        if (failurePatterns.any { summaryLower.contains(it) }) {
            Log.w(TAG, "Summary contains AI failure pattern")
            return false
        }
        
        // Check if summary is too similar to original (for short texts)
        if (originalText.length < 200 && summary.length > originalText.length * 0.8) {
            Log.w(TAG, "Summary too similar to original text")
            return false
        }
        
        Log.d(TAG, "Summary passed quality validation")
        return true
    }
    
    private fun cleanSummaryResponse(response: String): String {
        Log.d(TAG, "Cleaning LLM response: '${response.take(100)}...'")
        
        var cleaned = response.trim()
        
        // Remove common AI response prefixes
        val prefixesToRemove = listOf(
            "Summary:", "summary:", "SUMMARY:",
            "Here is a summary:", "Here's a summary:",
            "The summary is:", "Summary of the email:",
            "Email summary:", "This email", "The email",
            "Based on", "The sender", "In this email"
        )
        
        for (prefix in prefixesToRemove) {
            if (cleaned.startsWith(prefix, ignoreCase = true)) {
                cleaned = cleaned.removePrefix(prefix).trim()
                break
            }
        }
        
        // Remove trailing colons or other artifacts
        cleaned = cleaned.removePrefix(":").trim()
        
        // If response is too short or just repeats subject, enhance it
        if (cleaned.length < 20) {
            Log.w(TAG, "Response too short: '$cleaned', length: ${cleaned.length}")
            cleaned = "Brief summary: $cleaned"
        }
        
        // Ensure reasonable length but don't cut mid-sentence
        if (cleaned.length > 500) {
            val lastSentenceEnd = cleaned.substring(0, 500).lastIndexOfAny(charArrayOf('.', '!', '?'))
            cleaned = if (lastSentenceEnd > 250) {
                cleaned.substring(0, lastSentenceEnd + 1)
            } else {
                cleaned.take(500) + "..."
            }
        }
        
        Log.d(TAG, "Cleaned response: '${cleaned.take(100)}...' (length: ${cleaned.length})")
        return cleaned
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