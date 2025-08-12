package com.localllm.myapplication.service

import android.util.Log
import com.localllm.myapplication.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * AI Workflow Processor using in-app LLM instead of API calls
 * Handles all AI-related workflow actions using the local ModelManager
 */
class AIWorkflowProcessor(private val modelManager: ModelManager) {
    
    companion object {
        private const val TAG = "AIWorkflowProcessor"
    }
    
    /**
     * Process AI analyze text action
     */
    suspend fun processAnalyzeText(action: MultiUserAction.AIAnalyzeText, context: WorkflowExecutionContext): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (!modelManager.isModelLoaded.value) {
                    return@withContext Result.failure(Exception("No AI model loaded. Please load a model first."))
                }
                
                val prompt = "${action.analysisPrompt}\n\nText to analyze: ${action.inputText}"
                Log.d(TAG, "Processing AI text analysis with prompt length: ${prompt.length}")
                
                val result = generateResponse(prompt)
                result.fold(
                    onSuccess = { response ->
                        context.variables[action.outputVariable] = response
                        Log.d(TAG, "Text analysis completed successfully")
                        Result.success(response)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Text analysis failed", error)
                        Result.failure(error)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in text analysis", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Process AI generate response action
     */
    suspend fun processGenerateResponse(action: MultiUserAction.AIGenerateResponse, context: WorkflowExecutionContext): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (!modelManager.isModelLoaded.value) {
                    return@withContext Result.failure(Exception("No AI model loaded. Please load a model first."))
                }
                
                val prompt = "${action.prompt}\n\nContext: ${action.context}"
                Log.d(TAG, "Processing AI response generation")
                
                val result = generateResponse(prompt)
                result.fold(
                    onSuccess = { response ->
                        context.variables[action.outputVariable] = response
                        Log.d(TAG, "Response generation completed successfully")
                        Result.success(response)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Response generation failed", error)
                        Result.failure(error)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in response generation", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Process AI summarize content action
     */
    suspend fun processSummarizeContent(action: MultiUserAction.AISummarizeContent, context: WorkflowExecutionContext): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (!modelManager.isModelLoaded.value) {
                    return@withContext Result.failure(Exception("No AI model loaded. Please load a model first."))
                }
                
                val prompt = "Summarize the following content in ${action.maxLength} words or less. Be concise and capture the key points:\n\n${action.content}"
                Log.d(TAG, "Processing AI content summarization")
                
                val result = generateResponse(prompt)
                result.fold(
                    onSuccess = { response ->
                        context.variables[action.outputVariable] = response
                        Log.d(TAG, "Content summarization completed successfully")
                        Result.success(response)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Content summarization failed", error)
                        Result.failure(error)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in content summarization", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Process AI translate text action
     */
    suspend fun processTranslateText(action: MultiUserAction.AITranslateText, context: WorkflowExecutionContext): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (!modelManager.isModelLoaded.value) {
                    return@withContext Result.failure(Exception("No AI model loaded. Please load a model first."))
                }
                
                val prompt = "Translate the following text to ${action.targetLanguage}. Provide only the translation without any additional text:\n\n${action.text}"
                Log.d(TAG, "Processing AI text translation to ${action.targetLanguage}")
                
                val result = generateResponse(prompt)
                result.fold(
                    onSuccess = { response ->
                        context.variables[action.outputVariable] = response.trim()
                        Log.d(TAG, "Text translation completed successfully")
                        Result.success(response.trim())
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Text translation failed", error)
                        Result.failure(error)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in text translation", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Process AI extract keywords action
     */
    suspend fun processExtractKeywords(action: MultiUserAction.AIExtractKeywords, context: WorkflowExecutionContext): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (!modelManager.isModelLoaded.value) {
                    return@withContext Result.failure(Exception("No AI model loaded. Please load a model first."))
                }
                
                val prompt = "Extract the ${action.count} most important keywords from the following text. Provide only the keywords separated by commas:\n\n${action.text}"
                Log.d(TAG, "Processing AI keyword extraction")
                
                val result = generateResponse(prompt)
                result.fold(
                    onSuccess = { response ->
                        val keywords = response.trim()
                        context.variables[action.outputVariable] = keywords
                        Log.d(TAG, "Keyword extraction completed successfully")
                        Result.success(keywords)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Keyword extraction failed", error)
                        Result.failure(error)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in keyword extraction", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Process AI sentiment analysis action
     */
    suspend fun processSentimentAnalysis(action: MultiUserAction.AISentimentAnalysis, context: WorkflowExecutionContext): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (!modelManager.isModelLoaded.value) {
                    return@withContext Result.failure(Exception("No AI model loaded. Please load a model first."))
                }
                
                val prompt = "Analyze the sentiment of the following text. Respond with only one word: 'positive', 'negative', or 'neutral':\n\n${action.text}"
                Log.d(TAG, "Processing AI sentiment analysis")
                
                val result = generateResponse(prompt)
                result.fold(
                    onSuccess = { response ->
                        val sentiment = response.trim().lowercase()
                        val validSentiment = when {
                            sentiment.contains("positive") -> "positive"
                            sentiment.contains("negative") -> "negative"
                            else -> "neutral"
                        }
                        context.variables[action.outputVariable] = validSentiment
                        Log.d(TAG, "Sentiment analysis completed: $validSentiment")
                        Result.success(validSentiment)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Sentiment analysis failed", error)
                        Result.failure(error)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in sentiment analysis", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Process AI smart reply action
     */
    suspend fun processSmartReply(action: MultiUserAction.AISmartReply, context: WorkflowExecutionContext): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (!modelManager.isModelLoaded.value) {
                    return@withContext Result.failure(Exception("No AI model loaded. Please load a model first."))
                }
                
                val contextText = if (action.context.isNullOrBlank()) "" else "\n\nAdditional context: ${action.context}"
                val prompt = "Generate a ${action.tone} reply to the following message. Keep it concise and appropriate:$contextText\n\nOriginal message: ${action.originalMessage}"
                
                Log.d(TAG, "Processing AI smart reply with tone: ${action.tone}")
                
                val result = generateResponse(prompt)
                result.fold(
                    onSuccess = { response ->
                        val reply = response.trim()
                        context.variables[action.outputVariable] = reply
                        Log.d(TAG, "Smart reply generated successfully")
                        Result.success(reply)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Smart reply generation failed", error)
                        Result.failure(error)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in smart reply generation", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Generate email categorization
     */
    suspend fun categorizeEmail(emailContent: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (!modelManager.isModelLoaded.value) {
                    return@withContext Result.failure(Exception("No AI model loaded. Please load a model first."))
                }
                
                val prompt = "Categorize this email as one of: urgent, important, normal, or spam. Respond with only the category:\n\n$emailContent"
                Log.d(TAG, "Processing email categorization")
                
                val result = generateResponse(prompt)
                result.fold(
                    onSuccess = { response ->
                        val category = response.trim().lowercase()
                        val validCategory = when {
                            category.contains("urgent") -> "urgent"
                            category.contains("important") -> "important"
                            category.contains("spam") -> "spam"
                            else -> "normal"
                        }
                        Log.d(TAG, "Email categorized as: $validCategory")
                        Result.success(validCategory)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Email categorization failed", error)
                        Result.failure(error)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in email categorization", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Generate professional email reply
     */
    suspend fun generateEmailReply(originalEmail: String, context: String? = null): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (!modelManager.isModelLoaded.value) {
                    return@withContext Result.failure(Exception("No AI model loaded. Please load a model first."))
                }
                
                val contextText = if (context.isNullOrBlank()) "" else "\n\nContext: $context"
                val prompt = "Generate a professional email reply to the following email. Be polite and concise:$contextText\n\nOriginal email: $originalEmail"
                
                Log.d(TAG, "Processing email reply generation")
                
                val result = generateResponse(prompt)
                result.fold(
                    onSuccess = { response ->
                        Log.d(TAG, "Email reply generated successfully")
                        Result.success(response.trim())
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Email reply generation failed", error)
                        Result.failure(error)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in email reply generation", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Process conditional action based on AI analysis
     */
    suspend fun evaluateCondition(condition: String, context: WorkflowExecutionContext): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                // Simple condition evaluation based on variables
                // For more complex conditions, could use AI to evaluate
                val result = when {
                    condition.contains("==") -> {
                        val parts = condition.split("==").map { it.trim() }
                        if (parts.size == 2) {
                            val leftValue = context.variables[parts[0]] ?: parts[0]
                            val rightValue = parts[1].replace("\"", "").replace("'", "")
                            leftValue.equals(rightValue, ignoreCase = true)
                        } else false
                    }
                    condition.contains("!=") -> {
                        val parts = condition.split("!=").map { it.trim() }
                        if (parts.size == 2) {
                            val leftValue = context.variables[parts[0]] ?: parts[0]
                            val rightValue = parts[1].replace("\"", "").replace("'", "")
                            !leftValue.equals(rightValue, ignoreCase = true)
                        } else false
                    }
                    condition.contains("contains") -> {
                        val parts = condition.split("contains").map { it.trim() }
                        if (parts.size == 2) {
                            val leftValue = context.variables[parts[0]] ?: parts[0]
                            val rightValue = parts[1].replace("\"", "").replace("'", "")
                            leftValue.contains(rightValue, ignoreCase = true)
                        } else false
                    }
                    else -> false
                }
                
                Log.d(TAG, "Condition '$condition' evaluated to: $result")
                Result.success(result)
            } catch (e: Exception) {
                Log.e(TAG, "Error evaluating condition: $condition", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Generate response using the local LLM via ModelManager
     */
    private suspend fun generateResponse(prompt: String): Result<String> {
        return suspendCoroutine { continuation ->
            modelManager.generateResponse(
                prompt = prompt,
                onResult = { result ->
                    continuation.resume(result)
                }
            )
        }
    }
    
    /**
     * Check if AI model is available and loaded
     */
    fun isModelReady(): Boolean {
        return modelManager.isModelLoaded.value
    }
    
    /**
     * Get current model status
     */
    fun getModelStatus(): String {
        return when {
            modelManager.isModelLoading.value -> "Loading model..."
            modelManager.isModelLoaded.value -> "Model ready"
            modelManager.modelLoadError.value != null -> "Model error: ${modelManager.modelLoadError.value}"
            else -> "No model loaded"
        }
    }
}