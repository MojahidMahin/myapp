package com.localllm.myapplication.data

import com.localllm.myapplication.service.integration.GmailIntegrationService
import com.localllm.myapplication.service.integration.TelegramBotService

/**
 * Multi-user workflow interface
 */
interface Workflow {
    val id: String
    val name: String
    val description: String
    val isEnabled: Boolean
    val createdAt: Long
    val updatedAt: Long
    val createdBy: String
    val workflowType: WorkflowType
}

/**
 * Workflow types for multi-user support
 */
enum class WorkflowType {
    PERSONAL,           // Single user workflows
    TEAM,              // Shared between team members
    CROSS_USER,        // Between different users
    PUBLIC             // Public templates
}

/**
 * User data for workflow system
 */
data class WorkflowUser(
    val id: String,
    val email: String,
    val displayName: String,
    val telegramUserId: Long? = null,
    val telegramUsername: String? = null,
    val gmailConnected: Boolean = false,
    val telegramConnected: Boolean = false,
    val permissions: Set<Permission> = setOf(Permission.CREATE_WORKFLOW, Permission.EXECUTE_WORKFLOW),
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Permission system for workflows
 */
enum class Permission {
    CREATE_WORKFLOW,
    EDIT_WORKFLOW,
    DELETE_WORKFLOW,
    EXECUTE_WORKFLOW,
    SHARE_WORKFLOW,
    VIEW_WORKFLOW,
    ADMIN_ALL_WORKFLOWS
}

/**
 * Workflow permissions for sharing and collaboration
 */
data class WorkflowPermissions(
    val canTrigger: List<String> = emptyList(),      // User IDs who can trigger
    val canModify: List<String> = emptyList(),       // User IDs who can edit
    val canView: List<String> = emptyList(),         // User IDs who can view
    val requiresApproval: Boolean = false,           // Needs approval before execution
    val approvers: List<String> = emptyList()        // User IDs who can approve
)

/**
 * Multi-user workflow implementation
 */
data class MultiUserWorkflow(
    override val id: String,
    override val name: String,
    override val description: String = "",
    override val isEnabled: Boolean = true,
    override val createdAt: Long = System.currentTimeMillis(),
    override val updatedAt: Long = System.currentTimeMillis(),
    override val createdBy: String,
    override val workflowType: WorkflowType = WorkflowType.PERSONAL,
    val sharedWith: List<String> = emptyList(),       // User IDs
    val isPublic: Boolean = false,
    val permissions: WorkflowPermissions = WorkflowPermissions(),
    val triggers: List<MultiUserTrigger> = emptyList(),
    val actions: List<MultiUserAction> = emptyList(),
    val runInBackground: Boolean = true,
    val variables: Map<String, String> = emptyMap()   // Workflow variables like {{email_content}}
) : Workflow

/**
 * Multi-user workflow triggers
 */
sealed class MultiUserTrigger {
    // Gmail Triggers
    data class UserGmailNewEmail(
        val userId: String,
        val condition: GmailIntegrationService.EmailCondition
    ) : MultiUserTrigger()
    
    data class UserGmailEmailReceived(
        val userId: String,
        val fromFilter: String? = null,
        val subjectFilter: String? = null,
        val bodyFilter: String? = null
    ) : MultiUserTrigger()
    
    // Telegram Triggers
    data class UserTelegramMessage(
        val userId: String,
        val condition: TelegramBotService.TelegramCondition
    ) : MultiUserTrigger()
    
    data class UserTelegramCommand(
        val userId: String,
        val command: String,
        val chatFilter: Long? = null
    ) : MultiUserTrigger()
    
    // Multi-user triggers
    data class AnyUserGmailTrigger(
        val userIds: List<String>,
        val condition: GmailIntegrationService.EmailCondition
    ) : MultiUserTrigger()
    
    data class AnyUserTelegramTrigger(
        val userIds: List<String>,
        val condition: TelegramBotService.TelegramCondition
    ) : MultiUserTrigger()
    
    // Time-based
    data class ScheduledTrigger(
        val cronExpression: String,
        val triggerUserId: String? = null
    ) : MultiUserTrigger()
    
    data class ManualTrigger(
        val name: String,
        val allowedUsers: List<String> = emptyList()
    ) : MultiUserTrigger()
    
    // Geofencing Triggers
    data class GeofenceEnterTrigger(
        val userId: String,
        val geofenceId: String,
        val locationName: String,
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Float,
        val placeId: String? = null // Google Places API place ID
    ) : MultiUserTrigger()
    
    data class GeofenceExitTrigger(
        val userId: String,
        val geofenceId: String,
        val locationName: String,
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Float,
        val placeId: String? = null
    ) : MultiUserTrigger()
    
    data class GeofenceDwellTrigger(
        val userId: String,
        val geofenceId: String,
        val locationName: String,
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Float,
        val dwellTimeMillis: Long = 300000, // 5 minutes default
        val placeId: String? = null
    ) : MultiUserTrigger()
    
    // Image Analysis Triggers
    data class ImageAnalysisTrigger(
        val userId: String,
        val triggerName: String,
        val imageAttachments: List<ImageAttachment> = emptyList(), // Optional image attachments
        val analysisQuestions: List<String> = emptyList(), // Questions to ask about images
        val analysisKeywords: List<String> = emptyList(), // Keywords to look for in analysis
        val analysisType: ImageAnalysisType = ImageAnalysisType.COMPREHENSIVE,
        val triggerOnKeywordMatch: Boolean = false, // If true, trigger only when keywords match
        val minimumConfidence: Float = 0.5f, // Minimum confidence threshold for analysis
        val retriggerDelay: Long = 300000, // 5 minutes between retriggers for same image
        val enableOCR: Boolean = true, // Enable text extraction
        val enableObjectDetection: Boolean = true, // Enable object detection
        val enablePeopleDetection: Boolean = true // Enable people counting
    ) : MultiUserTrigger()
    
    data class AutoImageAnalysisTrigger(
        val userId: String,
        val triggerName: String,
        val sourceDirectory: String? = null, // Monitor directory for new images (optional)
        val analysisType: ImageAnalysisType = ImageAnalysisType.COMPREHENSIVE,
        val fileExtensions: List<String> = listOf("jpg", "jpeg", "png", "webp"), // Supported image formats
        val maxFileSize: Long = 10 * 1024 * 1024, // 10MB limit
        val analysisQuestions: List<String> = emptyList(),
        val analysisKeywords: List<String> = emptyList(),
        val triggerOnKeywordMatch: Boolean = false,
        val minimumConfidence: Float = 0.5f,
        val processingInterval: Long = 60000, // Check for new images every minute
        val enableOCR: Boolean = true,
        val enableObjectDetection: Boolean = true,
        val enablePeopleDetection: Boolean = true
    ) : MultiUserTrigger()
}

/**
 * Multi-user workflow actions
 */
sealed class MultiUserAction {
    // Gmail Actions
    data class SendToUserGmail(
        val targetUserId: String,
        val to: String? = null,  // If null, uses target user's email
        val subject: String,
        val body: String,
        val isHtml: Boolean = false
    ) : MultiUserAction()
    
    data class ReplyToUserGmail(
        val targetUserId: String,
        val originalMessageId: String,
        val replyBody: String,
        val isHtml: Boolean = false
    ) : MultiUserAction()
    
    data class ForwardUserGmail(
        val sourceUserId: String,
        val targetUserId: String,
        val messageId: String
    ) : MultiUserAction()
    
    // Telegram Actions
    data class SendToUserTelegram(
        val targetUserId: String = "",  // If empty, uses trigger user
        val chatId: Long? = null,  // If null, uses user's private chat
        val text: String,
        val parseMode: String? = null
    ) : MultiUserAction()
    
    data class ForwardGmailToTelegram(
        val targetUserId: String,
        val chatId: Long? = null,  // If null, uses user's private chat
        val includeSubject: Boolean = true,
        val includeFrom: Boolean = true,
        val includeBody: Boolean = true,
        val summarize: Boolean = false,  // Optional AI summarization
        val messageTemplate: String? = null  // Custom message template
    ) : MultiUserAction()
    
    data class ReplyToUserTelegram(
        val targetUserId: String,
        val chatId: Long = 0L,  // Default 0L, will be resolved from context if not provided
        val replyToMessageId: Long = 0L,  // Default 0L, will be resolved from context if not provided
        val text: String,
        val parseMode: String? = null
    ) : MultiUserAction()
    
    data class AutoReplyTelegram(
        val autoReplyText: String  // The automatic reply message
    ) : MultiUserAction()
    
    data class ForwardUserTelegram(
        val sourceUserId: String,
        val targetUserId: String,
        val fromChatId: Long,
        val toChatId: Long,
        val messageId: Long
    ) : MultiUserAction()
    
    // Multi-user actions
    data class SendToMultipleUsers(
        val targetUserIds: List<String>,
        val platform: Platform,
        val content: String,
        val subject: String? = null  // For email
    ) : MultiUserAction()
    
    data class BroadcastMessage(
        val targetUserIds: List<String>,
        val platforms: List<Platform>,  // Can send to both Gmail and Telegram
        val content: String,
        val subject: String? = null
    ) : MultiUserAction()
    
    // AI Actions using in-app LLM
    data class AIAnalyzeText(
        val inputText: String,
        val analysisPrompt: String,
        val outputVariable: String = "ai_analysis"
    ) : MultiUserAction()
    
    data class AIGenerateResponse(
        val context: String,
        val prompt: String,
        val outputVariable: String = "ai_response"
    ) : MultiUserAction()
    
    data class AISummarizeContent(
        val content: String,
        val maxLength: Int = 100,
        val outputVariable: String = "ai_summary"
    ) : MultiUserAction()
    
    data class AITranslateText(
        val text: String,
        val targetLanguage: String,
        val outputVariable: String = "ai_translation"
    ) : MultiUserAction()
    
    data class AIExtractKeywords(
        val text: String,
        val count: Int = 5,
        val outputVariable: String = "ai_keywords"
    ) : MultiUserAction()
    
    data class AISentimentAnalysis(
        val text: String,
        val outputVariable: String = "ai_sentiment"
    ) : MultiUserAction()
    
    data class AISmartReply(
        val originalMessage: String,
        val context: String? = null,
        val tone: String = "professional",
        val outputVariable: String = "ai_reply"
    ) : MultiUserAction()
    
    // Image Analysis Actions
    data class AIImageAnalysisAction(
        val imageSource: ImageSource, // Where to get the image from
        val analysisType: ImageAnalysisType = ImageAnalysisType.COMPREHENSIVE,
        val analysisQuestions: List<String> = emptyList(), // Specific questions to ask about the image
        val enableOCR: Boolean = true,
        val enableObjectDetection: Boolean = true,
        val enablePeopleDetection: Boolean = true,
        val outputVariable: String = "image_analysis_result",
        val saveAnalysisToFile: Boolean = false, // Save detailed analysis to file
        val includeVisualDescription: Boolean = true
    ) : MultiUserAction()
    
    data class AIBatchImageAnalysisAction(
        val imageSources: List<ImageSource>, // Multiple images to analyze
        val analysisType: ImageAnalysisType = ImageAnalysisType.COMPREHENSIVE,
        val analysisQuestions: List<String> = emptyList(),
        val enableOCR: Boolean = true,
        val enableObjectDetection: Boolean = true,
        val enablePeopleDetection: Boolean = true,
        val outputVariable: String = "batch_analysis_results",
        val combineResults: Boolean = true, // Combine all results into a summary
        val saveIndividualAnalyses: Boolean = false,
        val parallelProcessing: Boolean = true // Process images in parallel
    ) : MultiUserAction()
    
    data class AIImageComparisonAction(
        val primaryImageSource: ImageSource,
        val comparisonImageSources: List<ImageSource>,
        val comparisonType: ImageComparisonType = ImageComparisonType.VISUAL_SIMILARITY,
        val outputVariable: String = "image_comparison_result",
        val includeDetailedDifferences: Boolean = true
    ) : MultiUserAction()
    
    /**
     * Gmail AI Summary to Telegram - Simplified version using existing Telegram setup
     * Uses bot token from Telegram tab and contact picker for chat selection
     */
    data class GmailAISummaryToTelegram(
        val selectedContactId: Long, // Telegram user ID from saved contacts
        val maxSummaryWords: Int = 100, // Only configurable field - word limit for summary
        val outputSummaryVariable: String = "gmail_ai_summary"
    ) : MultiUserAction()
    
    /**
     * Smart action that summarizes trigger content and forwards based on keywords
     * This implements the Zapier-like functionality requested
     */
    data class AISmartSummarizeAndForward(
        val triggerContent: String = "{{trigger_content}}", // Content from trigger (email body, telegram message, etc.)
        val summarizationStyle: String = "concise", // "concise", "detailed", "structured", "keywords_focused"
        val maxSummaryLength: Int = 150,
        val keywordRules: List<KeywordForwardingRule> = emptyList(), // Rules for keyword-based forwarding
        val defaultForwardTo: ForwardingDestination? = null, // Default destination if no keywords match
        val includeOriginalContent: Boolean = false, // Whether to include original content with summary
        val summaryOutputVariable: String = "ai_summary",
        val keywordsOutputVariable: String = "extracted_keywords",
        val forwardingDecisionVariable: String = "forwarding_decision"
    ) : MultiUserAction()
    
    /**
     * Auto Email Summarizer - Simpler action for automatically summarizing triggered emails
     * and forwarding the summary to specified email addresses
     */
    data class AIAutoEmailSummarizer(
        val forwardToEmails: List<String>, // List of email addresses to forward summaries to
        val summaryStyle: String = "concise", // "concise", "detailed", "structured"
        val maxSummaryLength: Int = 200,
        val includeOriginalSubject: Boolean = true,
        val includeOriginalSender: Boolean = true,
        val customSubjectPrefix: String = "[Summary]",
        val emailTemplate: String = """
            {{summary_subject}}
            
            Summary:
            {{ai_summary}}
            
            {{original_info}}
        """.trimIndent(),
        val summaryOutputVariable: String = "email_summary"
    ) : MultiUserAction()
    
    // Approval and workflow control
    data class RequireApproval(
        val approverUserId: String,
        val pendingAction: MultiUserAction,
        val timeoutMinutes: Int = 60
    ) : MultiUserAction()
    
    data class ConditionalAction(
        val condition: String,  // Simple condition like "ai_sentiment == positive"
        val trueAction: MultiUserAction,
        val falseAction: MultiUserAction? = null
    ) : MultiUserAction()
    
    // Utility actions
    data class DelayAction(
        val delayMinutes: Int
    ) : MultiUserAction()
    
    data class LogAction(
        val message: String,
        val level: String = "INFO"
    ) : MultiUserAction()
    
    data class NotificationAction(
        val targetUserId: String,
        val title: String,
        val message: String
    ) : MultiUserAction()
}

/**
 * Platform enumeration for cross-platform actions
 */
enum class Platform {
    GMAIL, TELEGRAM, BOTH
}

/**
 * Workflow execution context for variable management
 */
data class WorkflowExecutionContext(
    val workflowId: String,
    val triggerUserId: String,
    val triggerData: Any,
    val variables: MutableMap<String, String> = mutableMapOf(),
    val executionId: String = java.util.UUID.randomUUID().toString(),
    val startTime: Long = System.currentTimeMillis()
)

/**
 * Workflow execution result
 */
data class WorkflowExecutionResult(
    val executionId: String,
    val workflowId: String,
    val success: Boolean,
    val message: String,
    val executedActions: List<String> = emptyList(),
    val variables: Map<String, String> = emptyMap(),
    val duration: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Supporting data classes for AISmartSummarizeAndForward action
 */

/**
 * Rule for keyword-based forwarding decisions
 */
data class KeywordForwardingRule(
    val keywords: List<String>, // Keywords to match
    val matchingStrategy: String = "fuzzy", // "exact", "fuzzy", "semantic", "pattern"
    val minimumMatches: Int = 1, // Minimum number of keywords that must match
    val destination: ForwardingDestination, // Where to forward if rule matches
    val priority: Int = 0, // Higher priority rules are checked first
    val description: String = "" // Human-readable description of the rule
)

/**
 * Destination for forwarding content
 */
sealed class ForwardingDestination {
    data class EmailDestination(
        val email: String,
        val subject: String = "Forwarded: {{original_subject}}",
        val bodyTemplate: String = "{{ai_summary}}\n\n---\nOriginal content:\n{{original_content}}"
    ) : ForwardingDestination()
    
    data class TelegramDestination(
        val chatId: Long,
        val messageTemplate: String = "📧 Summary: {{ai_summary}}"
    ) : ForwardingDestination()
    
    data class UserGmailDestination(
        val targetUserId: String,
        val subject: String = "Forwarded: {{original_subject}}",
        val bodyTemplate: String = "{{ai_summary}}\n\n---\nOriginal content:\n{{original_content}}"
    ) : ForwardingDestination()
    
    data class UserTelegramDestination(
        val targetUserId: String,
        val messageTemplate: String = "📧 Summary: {{ai_summary}}"
    ) : ForwardingDestination()
    
    data class MultipleDestinations(
        val destinations: List<ForwardingDestination>
    ) : ForwardingDestination()
}

/**
 * Image attachment data for workflow triggers
 */
data class ImageAttachment(
    val id: String = java.util.UUID.randomUUID().toString(),
    val fileName: String,
    val filePath: String? = null, // Local file path
    val uri: String? = null, // Content URI
    val fileSize: Long = 0L,
    val mimeType: String = "image/jpeg",
    val uploadedAt: Long = System.currentTimeMillis(),
    val analysisQuestions: List<String> = emptyList(), // Specific questions for this image
    val metadata: Map<String, String> = emptyMap() // Additional metadata
)

/**
 * Types of image analysis to perform
 */
enum class ImageAnalysisType {
    COMPREHENSIVE, // Full analysis including OCR, objects, people, etc.
    OCR_ONLY, // Only text extraction
    OBJECT_DETECTION, // Only object detection
    PEOPLE_DETECTION, // Only people counting
    QUICK_SCAN, // Fast analysis with basic information
    CUSTOM // Custom analysis based on specific parameters
}

/**
 * Image analysis result data for workflow execution context
 */
data class ImageAnalysisWorkflowResult(
    val attachmentId: String,
    val fileName: String,
    val success: Boolean,
    val analysisType: ImageAnalysisType,
    val description: String,
    val ocrText: String = "",
    val peopleCount: Int = 0,
    val detectedObjects: List<String> = emptyList(),
    val dominantColors: List<String> = emptyList(),
    val confidence: Float = 0f,
    val keywords: List<String> = emptyList(), // Extracted keywords
    val keywordMatches: List<String> = emptyList(), // Matched keywords from trigger
    val visualElements: Map<String, Any> = emptyMap(), // Visual analysis results
    val analysisTime: Long = System.currentTimeMillis(),
    val error: String? = null
)

/**
 * Image source specification for workflow actions
 */
sealed class ImageSource {
    data class AttachmentSource(
        val attachmentId: String
    ) : ImageSource()
    
    data class FilePathSource(
        val filePath: String
    ) : ImageSource()
    
    data class UriSource(
        val uri: String
    ) : ImageSource()
    
    data class TriggerImageSource(
        val index: Int = 0 // Which image from trigger (if multiple)
    ) : ImageSource()
    
    data class VariableSource(
        val variableName: String // Image path/URI stored in workflow variable
    ) : ImageSource()
    
    data class EmailAttachmentSource(
        val emailId: String,
        val attachmentIndex: Int = 0
    ) : ImageSource()
    
    data class TelegramPhotoSource(
        val messageId: String,
        val photoIndex: Int = 0
    ) : ImageSource()
}

/**
 * Types of image comparison analysis
 */
enum class ImageComparisonType {
    VISUAL_SIMILARITY, // Compare visual similarity
    OBJECT_DIFFERENCES, // Compare detected objects
    TEXT_DIFFERENCES, // Compare OCR text
    COLOR_DIFFERENCES, // Compare color schemes
    STRUCTURAL_DIFFERENCES, // Compare composition and structure
    COMPREHENSIVE // All comparison types
}