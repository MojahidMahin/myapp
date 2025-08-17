package com.localllm.myapplication.service.ai

import android.content.Context
import android.util.Log
import com.localllm.myapplication.data.*
import com.localllm.myapplication.service.ai.WorkflowKeywordService
import com.localllm.myapplication.service.ai.SummarizationService
import com.localllm.myapplication.service.ai.SummarizationStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service that implements smart forwarding based on content analysis and keyword matching
 * Follows Strategy Pattern for different forwarding strategies
 */
interface SmartForwardingService {
    suspend fun processAndForward(
        content: String,
        rules: List<KeywordForwardingRule>,
        defaultDestination: ForwardingDestination?,
        context: Map<String, String> = emptyMap()
    ): Result<ForwardingResult>
}

/**
 * Concrete implementation using AI summarization and keyword extraction
 */
class AISmartForwardingService(
    private val summarizationService: SummarizationService,
    private val keywordService: WorkflowKeywordService,
    private val context: Context
) : SmartForwardingService {
    
    companion object {
        private const val TAG = "SmartForwardingService"
    }
    
    override suspend fun processAndForward(
        content: String,
        rules: List<KeywordForwardingRule>,
        defaultDestination: ForwardingDestination?,
        context: Map<String, String>
    ): Result<ForwardingResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Processing content for smart forwarding: ${content.take(100)}...")
            
            // Step 1: Summarize the content
            val summaryResult = summarizationService.summarizeText(
                text = content,
                maxLength = 150,
                style = SummarizationStyle.KEYWORDS_FOCUSED
            )
            
            if (summaryResult.isFailure) {
                return@withContext Result.failure(
                    Exception("Failed to summarize content: ${summaryResult.exceptionOrNull()?.message}")
                )
            }
            
            val summary = summaryResult.getOrThrow()
            Log.d(TAG, "Generated summary: $summary")
            
            // Step 2: Extract keywords from content and summary
            val keywordResult = keywordService.extractKeywords(
                text = "$content $summary",
                maxKeywords = 20
            )
            
            if (keywordResult.isFailure) {
                return@withContext Result.failure(
                    Exception("Failed to extract keywords: ${keywordResult.exceptionOrNull()?.message}")
                )
            }
            
            val extractedKeywords = keywordResult.getOrThrow()
            Log.d(TAG, "Extracted ${extractedKeywords.size} keywords")
            
            // Step 3: Find matching rules
            val matchingRule = findBestMatchingRule(content, summary, rules)
            
            // Step 4: Determine forwarding destination
            val destination = matchingRule?.destination ?: defaultDestination
            
            if (destination == null) {
                Log.w(TAG, "No forwarding destination determined")
                return@withContext Result.success(
                    ForwardingResult(
                        summary = summary,
                        extractedKeywords = extractedKeywords,
                        matchedRule = null,
                        forwardingDestination = null,
                        forwardingActions = emptyList(),
                        success = false,
                        message = "No forwarding destination matched or specified"
                    )
                )
            }
            
            // Step 5: Generate forwarding actions
            val forwardingActions = generateForwardingActions(
                destination = destination,
                summary = summary,
                originalContent = content,
                context = context,
                extractedKeywords = extractedKeywords
            )
            
            Log.d(TAG, "Generated ${forwardingActions.size} forwarding actions")
            
            Result.success(
                ForwardingResult(
                    summary = summary,
                    extractedKeywords = extractedKeywords,
                    matchedRule = matchingRule,
                    forwardingDestination = destination,
                    forwardingActions = forwardingActions,
                    success = true,
                    message = "Smart forwarding processed successfully"
                )
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in processAndForward", e)
            Result.failure(e)
        }
    }
    
    private suspend fun findBestMatchingRule(
        content: String,
        summary: String,
        rules: List<KeywordForwardingRule>
    ): KeywordForwardingRule? {
        val sortedRules = rules.sortedByDescending { it.priority }
        val fullText = "$content $summary"
        
        for (rule in sortedRules) {
            Log.d(TAG, "Checking rule: ${rule.description}")
            
            val fuzzyMatch = rule.matchingStrategy != "exact"
            var matchCount = 0
            
            for (keyword in rule.keywords) {
                val matchResult = keywordService.findKeywordMatches(
                    text = fullText,
                    targetKeywords = listOf(keyword),
                    fuzzyMatch = fuzzyMatch
                )
                
                if (matchResult.isSuccess && matchResult.getOrThrow().isNotEmpty()) {
                    matchCount++
                    Log.d(TAG, "Keyword '$keyword' matched in content")
                }
            }
            
            if (matchCount >= rule.minimumMatches) {
                Log.d(TAG, "Rule matched: ${rule.description} (${matchCount}/${rule.keywords.size} keywords)")
                return rule
            }
        }
        
        Log.d(TAG, "No rules matched")
        return null
    }
    
    private fun generateForwardingActions(
        destination: ForwardingDestination,
        summary: String,
        originalContent: String,
        context: Map<String, String>,
        extractedKeywords: List<String>
    ): List<MultiUserAction> {
        return when (destination) {
            is ForwardingDestination.EmailDestination -> {
                listOf(
                    MultiUserAction.SendToUserGmail(
                        targetUserId = "external_email",
                        to = destination.email,
                        subject = replaceTemplateVariables(
                            destination.subject,
                            summary,
                            originalContent,
                            context
                        ),
                        body = replaceTemplateVariables(
                            destination.bodyTemplate,
                            summary,
                            originalContent,
                            context
                        )
                    )
                )
            }
            
            is ForwardingDestination.TelegramDestination -> {
                listOf(
                    MultiUserAction.SendToUserTelegram(
                        targetUserId = "external_telegram",
                        chatId = destination.chatId,
                        text = replaceTemplateVariables(
                            destination.messageTemplate,
                            summary,
                            originalContent,
                            context
                        )
                    )
                )
            }
            
            is ForwardingDestination.UserGmailDestination -> {
                listOf(
                    MultiUserAction.SendToUserGmail(
                        targetUserId = destination.targetUserId,
                        subject = replaceTemplateVariables(
                            destination.subject,
                            summary,
                            originalContent,
                            context
                        ),
                        body = replaceTemplateVariables(
                            destination.bodyTemplate,
                            summary,
                            originalContent,
                            context
                        )
                    )
                )
            }
            
            is ForwardingDestination.UserTelegramDestination -> {
                listOf(
                    MultiUserAction.SendToUserTelegram(
                        targetUserId = destination.targetUserId,
                        text = replaceTemplateVariables(
                            destination.messageTemplate,
                            summary,
                            originalContent,
                            context
                        )
                    )
                )
            }
            
            is ForwardingDestination.MultipleDestinations -> {
                destination.destinations.flatMap { dest ->
                    generateForwardingActions(dest, summary, originalContent, context, extractedKeywords)
                }
            }
        }
    }
    
    private fun replaceTemplateVariables(
        template: String,
        summary: String,
        originalContent: String,
        context: Map<String, String>
    ): String {
        var result = template
        
        // Standard template variables
        result = result.replace("{{ai_summary}}", summary)
        result = result.replace("{{original_content}}", originalContent)
        result = result.replace("{{summary}}", summary) // Alternative name
        
        // Context variables from workflow execution
        for ((key, value) in context) {
            result = result.replace("{{$key}}", value)
        }
        
        // Email-specific variables
        result = result.replace("{{original_subject}}", context["email_subject"] ?: "No Subject")
        result = result.replace("{{email_from}}", context["email_from"] ?: "Unknown Sender")
        result = result.replace("{{email_subject}}", context["email_subject"] ?: "No Subject")
        result = result.replace("{{email_body}}", context["email_body"] ?: originalContent)
        
        // Telegram-specific variables
        result = result.replace("{{telegram_chat_id}}", context["telegram_chat_id"] ?: "")
        result = result.replace("{{telegram_user}}", context["telegram_user"] ?: "Unknown User")
        result = result.replace("{{telegram_message}}", context["telegram_message"] ?: originalContent)
        
        // Timestamp
        result = result.replace("{{timestamp}}", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date()))
        
        return result
    }
}

/**
 * Result of smart forwarding operation
 */
data class ForwardingResult(
    val summary: String,
    val extractedKeywords: List<String>,
    val matchedRule: KeywordForwardingRule?,
    val forwardingDestination: ForwardingDestination?,
    val forwardingActions: List<MultiUserAction>,
    val success: Boolean,
    val message: String
)

/**
 * Command pattern implementation for forwarding operations
 */
sealed class ForwardingCommand {
    abstract suspend fun execute(): Result<String>
    
    data class SummarizeCommand(
        private val summarizationService: SummarizationService,
        private val content: String,
        private val style: SummarizationStyle = SummarizationStyle.CONCISE
    ) : ForwardingCommand() {
        override suspend fun execute(): Result<String> {
            return summarizationService.summarizeText(content, style = style)
        }
    }
    
    data class ExtractKeywordsCommand(
        private val keywordService: WorkflowKeywordService,
        private val content: String,
        private val maxKeywords: Int = 10
    ) : ForwardingCommand() {
        override suspend fun execute(): Result<String> {
            return keywordService.extractKeywords(content, maxKeywords)
                .map { keywords -> keywords.joinToString(", ") }
        }
    }
    
    data class ForwardContentCommand(
        private val smartForwardingService: SmartForwardingService,
        private val content: String,
        private val rules: List<KeywordForwardingRule>,
        private val defaultDestination: ForwardingDestination?
    ) : ForwardingCommand() {
        override suspend fun execute(): Result<String> {
            return smartForwardingService.processAndForward(content, rules, defaultDestination)
                .map { result -> result.message }
        }
    }
}