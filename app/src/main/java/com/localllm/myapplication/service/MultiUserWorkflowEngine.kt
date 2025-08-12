package com.localllm.myapplication.service

import android.content.Context
import android.util.Log
import com.localllm.myapplication.data.*
import com.localllm.myapplication.service.integration.GmailIntegrationService
import com.localllm.myapplication.service.integration.TelegramBotService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Multi-user workflow execution engine
 * Handles execution of workflows across different users and platforms
 */
class MultiUserWorkflowEngine(
    private val context: Context,
    private val userManager: UserManager,
    private val workflowRepository: WorkflowRepository,
    val executionRepository: WorkflowExecutionRepository,
    private val aiProcessor: AIWorkflowProcessor
) {
    
    private val validator = WorkflowValidator(context)
    
    companion object {
        private const val TAG = "MultiUserWorkflowEngine"
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Execution state management
    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()
    
    private val _currentExecution = MutableStateFlow<WorkflowExecutionContext?>(null)
    val currentExecution: StateFlow<WorkflowExecutionContext?> = _currentExecution.asStateFlow()
    
    // Pending approvals management
    private val pendingApprovals = mutableMapOf<String, PendingApproval>()
    
    /**
     * Execute a multi-user workflow
     */
    suspend fun executeWorkflow(
        workflowId: String,
        triggerUserId: String,
        triggerData: Any
    ): Result<WorkflowExecutionResult> {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "=== Starting workflow execution ===")
                Log.i(TAG, "Workflow ID: $workflowId")
                Log.i(TAG, "Trigger User ID: $triggerUserId")
                Log.i(TAG, "Trigger Data: $triggerData")
                
                // Get workflow
                val workflow = workflowRepository.getWorkflowById(workflowId).getOrNull() as? MultiUserWorkflow
                if (workflow == null) {
                    Log.e(TAG, "Workflow not found: $workflowId")
                    return@withContext Result.failure(Exception("Workflow not found or invalid type"))
                }
                
                Log.d(TAG, "Found workflow: ${workflow.name} (${workflow.actions.size} actions)")
                
                // Validate workflow before execution
                val validationResult = validator.validateWorkflow(workflow, userManager)
                if (!validationResult.isValid) {
                    val validationSummary = validator.getValidationSummary(validationResult)
                    Log.e(TAG, "Workflow validation failed:\n$validationSummary")
                    return@withContext Result.failure(Exception("Workflow validation failed: ${validationResult.errors.joinToString("; ") { it.message }}"))
                }
                
                if (validationResult.warnings.isNotEmpty()) {
                    Log.w(TAG, "Workflow has warnings: ${validationResult.warnings.joinToString("; ") { it.message }}")
                }
                
                // Check permissions
                val hasPermission = workflowRepository.hasPermission(triggerUserId, workflowId, Permission.EXECUTE_WORKFLOW).getOrNull() ?: false
                if (!hasPermission) {
                    Log.w(TAG, "Permission denied for user $triggerUserId to execute workflow $workflowId")
                    return@withContext Result.failure(Exception("User does not have permission to execute this workflow"))
                }
                
                Log.d(TAG, "Permission check passed for user: $triggerUserId")
                
                // Create execution context
                val context = WorkflowExecutionContext(
                    workflowId = workflowId,
                    triggerUserId = triggerUserId,
                    triggerData = triggerData
                )
                
                _currentExecution.value = context
                _isExecuting.value = true
                
                try {
                    // Execute workflow actions
                    val result = executeWorkflowActions(workflow, context)
                    
                    // Create execution result
                    val executionResult = WorkflowExecutionResult(
                        executionId = context.executionId,
                        workflowId = workflowId,
                        success = result.isSuccess,
                        message = result.getOrNull() ?: result.exceptionOrNull()?.message ?: "Unknown error",
                        variables = context.variables.toMap(),
                        duration = System.currentTimeMillis() - context.startTime
                    )
                    
                    // Save execution history
                    val saveResult = executionRepository.saveExecution(executionResult)
                    saveResult.onFailure { error ->
                        Log.w(TAG, "Failed to save execution result: ${error.message}")
                    }
                    
                    Log.i(TAG, "=== Workflow execution completed ===")
                    Log.i(TAG, "Success: ${executionResult.success}")
                    Log.i(TAG, "Duration: ${executionResult.duration}ms")
                    Log.i(TAG, "Message: ${executionResult.message}")
                    Result.success(executionResult)
                    
                } finally {
                    _isExecuting.value = false
                    _currentExecution.value = null
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error executing workflow: $workflowId", e)
                _isExecuting.value = false
                _currentExecution.value = null
                
                val executionResult = WorkflowExecutionResult(
                    executionId = UUID.randomUUID().toString(),
                    workflowId = workflowId,
                    success = false,
                    message = "Execution failed: ${e.message}",
                    duration = 0
                )
                
                Result.success(executionResult)
            }
        }
    }
    
    /**
     * Execute workflow actions sequentially
     */
    private suspend fun executeWorkflowActions(
        workflow: MultiUserWorkflow,
        context: WorkflowExecutionContext
    ): Result<String> {
        val executedActions = mutableListOf<String>()
        
        try {
            for ((index, action) in workflow.actions.withIndex()) {
                Log.d(TAG, "Executing action ${index + 1}/${workflow.actions.size}: ${action::class.simpleName}")
                
                val actionResult = executeAction(action, context)
                actionResult.fold(
                    onSuccess = { message ->
                        executedActions.add("${action::class.simpleName}: $message")
                        Log.d(TAG, "Action executed successfully: ${action::class.simpleName}")
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Action failed: ${action::class.simpleName}", error)
                        return Result.failure(Exception("Action ${index + 1} failed: ${error.message}"))
                    }
                )
                
                // Add small delay between actions
                delay(100)
            }
            
            val message = "Successfully executed ${executedActions.size} actions"
            Log.d(TAG, message)
            return Result.success(message)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in workflow execution", e)
            return Result.failure(e)
        }
    }
    
    /**
     * Execute individual workflow action
     */
    private suspend fun executeAction(action: MultiUserAction, context: WorkflowExecutionContext): Result<String> {
        return try {
            Log.d(TAG, "Executing action: ${action::class.simpleName}")
            val startTime = System.currentTimeMillis()
            
            val result = when (action) {
                // Gmail Actions
                is MultiUserAction.SendToUserGmail -> executeSendToUserGmail(action, context)
                is MultiUserAction.ReplyToUserGmail -> executeReplyToUserGmail(action, context)
                is MultiUserAction.ForwardUserGmail -> executeForwardUserGmail(action, context)
                
                // Telegram Actions
                is MultiUserAction.SendToUserTelegram -> executeSendToUserTelegram(action, context)
                is MultiUserAction.ReplyToUserTelegram -> executeReplyToUserTelegram(action, context)
                is MultiUserAction.ForwardUserTelegram -> executeForwardUserTelegram(action, context)
                
                // Multi-user Actions
                is MultiUserAction.SendToMultipleUsers -> executeSendToMultipleUsers(action, context)
                is MultiUserAction.BroadcastMessage -> executeBroadcastMessage(action, context)
                
                // AI Actions
                is MultiUserAction.AIAnalyzeText -> aiProcessor.processAnalyzeText(action, context)
                is MultiUserAction.AIGenerateResponse -> aiProcessor.processGenerateResponse(action, context)
                is MultiUserAction.AISummarizeContent -> aiProcessor.processSummarizeContent(action, context)
                is MultiUserAction.AITranslateText -> aiProcessor.processTranslateText(action, context)
                is MultiUserAction.AIExtractKeywords -> aiProcessor.processExtractKeywords(action, context)
                is MultiUserAction.AISentimentAnalysis -> aiProcessor.processSentimentAnalysis(action, context)
                is MultiUserAction.AISmartReply -> aiProcessor.processSmartReply(action, context)
                
                // Control Actions
                is MultiUserAction.RequireApproval -> executeRequireApproval(action, context)
                is MultiUserAction.ConditionalAction -> executeConditionalAction(action, context)
                is MultiUserAction.DelayAction -> executeDelay(action)
                is MultiUserAction.LogAction -> executeLog(action)
                is MultiUserAction.NotificationAction -> executeNotification(action, context)
            }
            
            val duration = System.currentTimeMillis() - startTime
            result.fold(
                onSuccess = { message ->
                    Log.d(TAG, "Action ${action::class.simpleName} completed successfully in ${duration}ms: $message")
                },
                onFailure = { error ->
                    Log.e(TAG, "Action ${action::class.simpleName} failed after ${duration}ms: ${error.message}")
                }
            )
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error executing action: ${action::class.simpleName}", e)
            Result.failure(e)
        }
    }
    
    // Gmail Action Implementations
    private suspend fun executeSendToUserGmail(action: MultiUserAction.SendToUserGmail, context: WorkflowExecutionContext): Result<String> {
        Log.d(TAG, "Sending Gmail to user: ${action.targetUserId}")
        
        val gmailService = userManager.getGmailService(action.targetUserId)
        if (gmailService == null) {
            Log.e(TAG, "Gmail service not available for user: ${action.targetUserId}")
            return Result.failure(Exception("Gmail service not available for user"))
        }
        
        val targetUser = userManager.getUserById(action.targetUserId).getOrNull()
        if (targetUser == null) {
            Log.e(TAG, "Target user not found: ${action.targetUserId}")
            return Result.failure(Exception("Target user not found"))
        }
        
        val toEmail = action.to ?: targetUser.email
        val processedSubject = replaceVariables(action.subject, context.variables)
        val processedBody = replaceVariables(action.body, context.variables)
        
        Log.d(TAG, "Sending email to: $toEmail, Subject: $processedSubject")
        
        return gmailService.sendEmail(toEmail, processedSubject, processedBody, action.isHtml)
            .map { messageId -> "Email sent successfully with ID: $messageId" }
    }
    
    private suspend fun executeReplyToUserGmail(action: MultiUserAction.ReplyToUserGmail, context: WorkflowExecutionContext): Result<String> {
        val gmailService = userManager.getGmailService(action.targetUserId)
            ?: return Result.failure(Exception("Gmail service not available for user"))
        
        val processedBody = replaceVariables(action.replyBody, context.variables)
        
        return gmailService.replyToEmail(action.originalMessageId, processedBody, action.isHtml)
    }
    
    private suspend fun executeForwardUserGmail(action: MultiUserAction.ForwardUserGmail, context: WorkflowExecutionContext): Result<String> {
        // Implementation would require extending Gmail service to support forwarding
        return Result.success("Gmail forwarding not yet implemented")
    }
    
    // Telegram Action Implementations
    private suspend fun executeSendToUserTelegram(action: MultiUserAction.SendToUserTelegram, context: WorkflowExecutionContext): Result<String> {
        val telegramService = userManager.getTelegramService(action.targetUserId)
            ?: return Result.failure(Exception("Telegram service not available for user"))
        
        val targetUser = userManager.getUserById(action.targetUserId).getOrNull()
            ?: return Result.failure(Exception("Target user not found"))
        
        val chatId = action.chatId ?: targetUser.telegramUserId
            ?: return Result.failure(Exception("No Telegram chat ID available for user"))
        
        val processedText = replaceVariables(action.text, context.variables)
        
        return telegramService.sendMessage(chatId, processedText, action.parseMode).map { messageId ->
            "Message sent to Telegram: $messageId"
        }
    }
    
    private suspend fun executeReplyToUserTelegram(action: MultiUserAction.ReplyToUserTelegram, context: WorkflowExecutionContext): Result<String> {
        val telegramService = userManager.getTelegramService(action.targetUserId)
            ?: return Result.failure(Exception("Telegram service not available for user"))
        
        val processedText = replaceVariables(action.text, context.variables)
        
        return telegramService.replyToMessage(action.chatId, action.replyToMessageId, processedText, action.parseMode).map { messageId ->
            "Reply sent to Telegram: $messageId"
        }
    }
    
    private suspend fun executeForwardUserTelegram(action: MultiUserAction.ForwardUserTelegram, context: WorkflowExecutionContext): Result<String> {
        val telegramService = userManager.getTelegramService(action.targetUserId)
            ?: return Result.failure(Exception("Telegram service not available for user"))
        
        return telegramService.forwardMessage(action.fromChatId, action.toChatId, action.messageId).map { messageId ->
            "Message forwarded in Telegram: $messageId"
        }
    }
    
    // Multi-user Action Implementations
    private suspend fun executeSendToMultipleUsers(action: MultiUserAction.SendToMultipleUsers, context: WorkflowExecutionContext): Result<String> {
        val results = mutableListOf<String>()
        val processedContent = replaceVariables(action.content, context.variables)
        
        for (userId in action.targetUserIds) {
            try {
                when (action.platform) {
                    Platform.GMAIL -> {
                        val gmailService = userManager.getGmailService(userId)
                        val user = userManager.getUserById(userId).getOrNull()
                        if (gmailService != null && user != null) {
                            val subject = action.subject ?: "Workflow Notification"
                            gmailService.sendEmail(user.email, subject, processedContent)
                            results.add("Gmail sent to $userId")
                        }
                    }
                    Platform.TELEGRAM -> {
                        val telegramService = userManager.getTelegramService(userId)
                        val user = userManager.getUserById(userId).getOrNull()
                        if (telegramService != null && user != null && user.telegramUserId != null) {
                            telegramService.sendMessage(user.telegramUserId!!, processedContent)
                            results.add("Telegram sent to $userId")
                        }
                    }
                    Platform.BOTH -> {
                        // Send to both platforms
                        // Implementation for both platforms
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send to user $userId", e)
                results.add("Failed to send to $userId: ${e.message}")
            }
        }
        
        return Result.success("Sent to ${results.size} users: ${results.joinToString(", ")}")
    }
    
    private suspend fun executeBroadcastMessage(action: MultiUserAction.BroadcastMessage, context: WorkflowExecutionContext): Result<String> {
        val results = mutableListOf<String>()
        val processedContent = replaceVariables(action.content, context.variables)
        
        for (userId in action.targetUserIds) {
            for (platform in action.platforms) {
                try {
                    when (platform) {
                        Platform.GMAIL -> {
                            val gmailService = userManager.getGmailService(userId)
                            val user = userManager.getUserById(userId).getOrNull()
                            if (gmailService != null && user != null) {
                                val subject = action.subject ?: "Broadcast Message"
                                gmailService.sendEmail(user.email, subject, processedContent)
                                results.add("Gmail broadcast to $userId")
                            }
                        }
                        Platform.TELEGRAM -> {
                            val telegramService = userManager.getTelegramService(userId)
                            val user = userManager.getUserById(userId).getOrNull()
                            if (telegramService != null && user != null && user.telegramUserId != null) {
                                telegramService.sendMessage(user.telegramUserId!!, processedContent)
                                results.add("Telegram broadcast to $userId")
                            }
                        }
                        Platform.BOTH -> {
                            // Both platforms handled separately above
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to broadcast to user $userId on $platform", e)
                }
            }
        }
        
        return Result.success("Broadcast completed: ${results.size} messages sent")
    }
    
    // Control Action Implementations
    private suspend fun executeRequireApproval(action: MultiUserAction.RequireApproval, context: WorkflowExecutionContext): Result<String> {
        val approvalId = UUID.randomUUID().toString()
        val pendingApproval = PendingApproval(
            id = approvalId,
            workflowId = context.workflowId,
            executionId = context.executionId,
            approverUserId = action.approverUserId,
            pendingAction = action.pendingAction,
            createdAt = System.currentTimeMillis(),
            timeoutAt = System.currentTimeMillis() + (action.timeoutMinutes * 60 * 1000)
        )
        
        pendingApprovals[approvalId] = pendingApproval
        
        // Send notification to approver
        notifyApprover(action.approverUserId, pendingApproval)
        
        return Result.success("Approval request sent to user ${action.approverUserId}")
    }
    
    private suspend fun executeConditionalAction(action: MultiUserAction.ConditionalAction, context: WorkflowExecutionContext): Result<String> {
        val conditionResult = aiProcessor.evaluateCondition(action.condition, context).getOrNull() ?: false
        
        val actionToExecute = if (conditionResult) {
            action.trueAction
        } else {
            action.falseAction ?: return Result.success("Condition false, no alternative action")
        }
        
        return executeAction(actionToExecute, context)
    }
    
    private suspend fun executeDelay(action: MultiUserAction.DelayAction): Result<String> {
        delay(action.delayMinutes * 60 * 1000L)
        return Result.success("Delayed for ${action.delayMinutes} minutes")
    }
    
    private suspend fun executeLog(action: MultiUserAction.LogAction): Result<String> {
        when (action.level.uppercase()) {
            "DEBUG" -> Log.d(TAG, "Workflow Log: ${action.message}")
            "INFO" -> Log.i(TAG, "Workflow Log: ${action.message}")
            "WARN" -> Log.w(TAG, "Workflow Log: ${action.message}")
            "ERROR" -> Log.e(TAG, "Workflow Log: ${action.message}")
            else -> Log.i(TAG, "Workflow Log: ${action.message}")
        }
        return Result.success("Logged: ${action.message}")
    }
    
    private suspend fun executeNotification(action: MultiUserAction.NotificationAction, context: WorkflowExecutionContext): Result<String> {
        // Implementation would depend on notification system
        val processedTitle = replaceVariables(action.title, context.variables)
        val processedMessage = replaceVariables(action.message, context.variables)
        
        Log.i(TAG, "Notification to ${action.targetUserId}: $processedTitle - $processedMessage")
        return Result.success("Notification sent to ${action.targetUserId}")
    }
    
    /**
     * Replace variables in text with actual values
     */
    private fun replaceVariables(text: String, variables: Map<String, String>): String {
        var processedText = text
        for ((key, value) in variables) {
            processedText = processedText.replace("{{$key}}", value)
        }
        return processedText
    }
    
    /**
     * Notify approver about pending approval
     */
    private suspend fun notifyApprover(approverUserId: String, approval: PendingApproval) {
        try {
            // Send notification via available platforms
            val telegramService = userManager.getTelegramService(approverUserId)
            val user = userManager.getUserById(approverUserId).getOrNull()
            
            if (telegramService != null && user?.telegramUserId != null) {
                val message = "⏳ Workflow approval required\nWorkflow: ${approval.workflowId}\nApproval ID: ${approval.id}\n\nReply with: /approve ${approval.id} or /reject ${approval.id}"
                telegramService.sendMessage(user.telegramUserId!!, message)
            }
            
            val gmailService = userManager.getGmailService(approverUserId)
            if (gmailService != null && user != null) {
                val subject = "Workflow Approval Required"
                val body = "A workflow requires your approval.\n\nWorkflow ID: ${approval.workflowId}\nApproval ID: ${approval.id}\n\nPlease review and approve or reject this request."
                gmailService.sendEmail(user.email, subject, body)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to notify approver", e)
        }
    }
    
    /**
     * Process approval decision
     */
    suspend fun processApproval(approvalId: String, approved: Boolean, approverId: String): Result<String> {
        val approval = pendingApprovals[approvalId]
            ?: return Result.failure(Exception("Approval request not found"))
        
        if (approval.approverUserId != approverId) {
            return Result.failure(Exception("Not authorized to approve this request"))
        }
        
        if (System.currentTimeMillis() > approval.timeoutAt) {
            pendingApprovals.remove(approvalId)
            return Result.failure(Exception("Approval request has expired"))
        }
        
        pendingApprovals.remove(approvalId)
        
        return if (approved) {
            // Execute the pending action
            val context = WorkflowExecutionContext(
                workflowId = approval.workflowId,
                triggerUserId = approverId,
                triggerData = "approval_granted"
            )
            executeAction(approval.pendingAction, context)
        } else {
            Result.success("Action rejected by approver")
        }
    }
    
    /**
     * Get pending approvals for user
     */
    fun getPendingApprovals(userId: String): List<PendingApproval> {
        return pendingApprovals.values.filter { it.approverUserId == userId }
    }
    
    /**
     * Clean up expired approvals
     */
    fun cleanupExpiredApprovals() {
        val currentTime = System.currentTimeMillis()
        val expired = pendingApprovals.values.filter { it.timeoutAt < currentTime }
        expired.forEach { pendingApprovals.remove(it.id) }
        
        if (expired.isNotEmpty()) {
            Log.d(TAG, "Cleaned up ${expired.size} expired approvals")
        }
    }
}

/**
 * Pending approval data class
 */
data class PendingApproval(
    val id: String,
    val workflowId: String,
    val executionId: String,
    val approverUserId: String,
    val pendingAction: MultiUserAction,
    val createdAt: Long,
    val timeoutAt: Long
)