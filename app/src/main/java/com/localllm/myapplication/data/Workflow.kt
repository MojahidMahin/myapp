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
        val targetUserId: String,
        val chatId: Long? = null,  // If null, uses user's private chat
        val text: String,
        val parseMode: String? = null
    ) : MultiUserAction()
    
    data class ReplyToUserTelegram(
        val targetUserId: String,
        val chatId: Long,
        val replyToMessageId: Long,
        val text: String,
        val parseMode: String? = null
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