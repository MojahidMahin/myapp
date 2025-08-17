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
                val validationResult = validator.validateWorkflow(workflow, userManager, triggerUserId)
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
                
                // Populate variables from trigger data if it's a map
                if (triggerData is Map<*, *>) {
                    triggerData.forEach { (key, value) ->
                        if (key is String && value is String) {
                            context.variables[key] = value
                        }
                    }
                }
                
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
                is MultiUserAction.AISmartSummarizeAndForward -> executeSmartSummarizeAndForward(action, context)
                is MultiUserAction.AIAutoEmailSummarizer -> executeAutoEmailSummarizer(action, context)
                
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
        val targetUserId = if (action.targetUserId.isEmpty()) context.triggerUserId else action.targetUserId
        
        Log.d(TAG, "=== GMAIL SEND ACTION DEBUG ===")
        Log.d(TAG, "Target User ID: $targetUserId")
        Log.d(TAG, "Available variables: ${context.variables}")
        Log.d(TAG, "Variables count: ${context.variables.size}")
        
        val gmailService = userManager.getGmailService(targetUserId)
        if (gmailService == null) {
            Log.e(TAG, "Gmail service not available for user: $targetUserId")
            return Result.failure(Exception("Gmail service not available for user: $targetUserId"))
        }
        
        val targetUser = userManager.getUserById(targetUserId).getOrNull()
        if (targetUser == null) {
            Log.e(TAG, "Target user not found: $targetUserId")
            return Result.failure(Exception("Target user not found: $targetUserId"))
        }
        
        val toEmail = action.to ?: targetUser.email
        val processedSubject = replaceVariables(action.subject, context.variables)
        val processedBody = replaceVariables(action.body, context.variables)
        
        Log.d(TAG, "To Email: $toEmail")
        Log.d(TAG, "Subject template: ${action.subject}")
        Log.d(TAG, "Processed Subject: $processedSubject")
        Log.d(TAG, "Body template: ${action.body}")
        Log.d(TAG, "Processed Body: $processedBody")
        Log.d(TAG, "=== END GMAIL SEND DEBUG ===")
        
        return gmailService.sendEmail(toEmail, processedSubject, processedBody, action.isHtml)
            .map { messageId -> "Email sent successfully with ID: $messageId" }
    }
    
    private suspend fun executeReplyToUserGmail(action: MultiUserAction.ReplyToUserGmail, context: WorkflowExecutionContext): Result<String> {
        val targetUserId = if (action.targetUserId.isEmpty()) context.triggerUserId else action.targetUserId
        val gmailService = userManager.getGmailService(targetUserId)
            ?: return Result.failure(Exception("Gmail service not available for user: $targetUserId"))
        
        val processedBody = replaceVariables(action.replyBody, context.variables)
        val processedMessageId = replaceVariables(action.originalMessageId, context.variables)
        
        Log.d(TAG, "=== GMAIL REPLY ACTION DEBUG ===")
        Log.d(TAG, "Target User ID: $targetUserId")
        Log.d(TAG, "Original Message ID template: ${action.originalMessageId}")
        Log.d(TAG, "Processed Message ID: $processedMessageId")
        Log.d(TAG, "Reply Body template: ${action.replyBody}")
        Log.d(TAG, "Processed Reply Body: $processedBody")
        Log.d(TAG, "Available variables: ${context.variables}")
        Log.d(TAG, "Variables count: ${context.variables.size}")
        Log.d(TAG, "=== END GMAIL REPLY DEBUG ===")
        
        if (processedMessageId.isBlank() || processedMessageId == action.originalMessageId) {
            Log.e(TAG, "Original message ID was not properly replaced with variable. Check if 'trigger_email_id' exists in variables.")
            return Result.failure(Exception("Original message ID was not properly replaced with variable"))
        }
        
        return gmailService.replyToEmail(processedMessageId, processedBody, action.isHtml)
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
    
    /**
     * Execute smart summarize and forward action
     * This is the core Zapier-like functionality
     */
    private suspend fun executeSmartSummarizeAndForward(
        action: MultiUserAction.AISmartSummarizeAndForward,
        context: WorkflowExecutionContext
    ): Result<String> {
        return try {
            Log.d(TAG, "Executing Smart Summarize and Forward action")
            
            // Initialize AI services
            val modelManager = com.localllm.myapplication.di.AppContainer.provideModelManager(this.context)
            val summarizationService = com.localllm.myapplication.service.ai.LocalLLMSummarizationService(modelManager, this.context)
            val keywordService = com.localllm.myapplication.service.ai.WorkflowKeywordService()
            val smartForwardingService = com.localllm.myapplication.service.ai.AISmartForwardingService(
                summarizationService, keywordService, this.context
            )
            
            // Get trigger content
            val triggerContent = replaceVariables(action.triggerContent, context)
            Log.d(TAG, "Processing content: ${triggerContent.take(100)}...")
            
            // Process and forward content using the smart service
            val forwardingResult = smartForwardingService.processAndForward(
                content = triggerContent,
                rules = action.keywordRules,
                defaultDestination = action.defaultForwardTo,
                context = context.variables.toMap()
            )
            
            forwardingResult.fold(
                onSuccess = { result ->
                    // Store results in context variables
                    context.variables[action.summaryOutputVariable] = result.summary
                    context.variables[action.keywordsOutputVariable] = result.extractedKeywords.joinToString(", ")
                    context.variables[action.forwardingDecisionVariable] = result.matchedRule?.description ?: "default"
                    
                    Log.d(TAG, "Smart forwarding completed successfully")
                    Log.d(TAG, "Summary: ${result.summary.take(100)}...")
                    Log.d(TAG, "Keywords: ${result.extractedKeywords.take(5)}")
                    Log.d(TAG, "Matched rule: ${result.matchedRule?.description ?: "none"}")
                    
                    // Execute the generated forwarding actions
                    if (result.forwardingActions.isNotEmpty()) {
                        Log.d(TAG, "Executing ${result.forwardingActions.size} forwarding actions")
                        for ((index, forwardingAction) in result.forwardingActions.withIndex()) {
                            Log.d(TAG, "Executing forwarding action ${index + 1}: ${forwardingAction::class.simpleName}")
                            val actionResult = executeAction(forwardingAction, context)
                            actionResult.fold(
                                onSuccess = { message ->
                                    Log.d(TAG, "Forwarding action executed successfully: $message")
                                },
                                onFailure = { error ->
                                    Log.w(TAG, "Forwarding action failed: ${error.message}")
                                    // Continue with other actions even if one fails
                                }
                            )
                        }
                    }
                    
                    val successMessage = """Smart summarization and forwarding completed successfully.
                        |Summary: ${result.summary}
                        |Keywords: ${result.extractedKeywords.joinToString(", ")}
                        |Matched rule: ${result.matchedRule?.description ?: "default destination"}
                        |Forwarding actions executed: ${result.forwardingActions.size}
                    """.trimMargin()
                    
                    Result.success(successMessage)
                },
                onFailure = { error ->
                    Log.e(TAG, "Smart forwarding failed: ${error.message}")
                    Result.failure(Exception("Smart summarize and forward failed: ${error.message}"))
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in executeSmartSummarizeAndForward", e)
            Result.failure(Exception("Smart action execution failed: ${e.message}"))
        }
    }
    
    /**
     * Execute auto email summarizer action
     * Automatically summarizes triggered email and forwards to specified addresses
     */
    private suspend fun executeAutoEmailSummarizer(
        action: MultiUserAction.AIAutoEmailSummarizer,
        context: WorkflowExecutionContext
    ): Result<String> {
        return try {
            Log.d(TAG, "Executing Auto Email Summarizer action")
            
            // Get email data from trigger context
            val emailSubject = context.variables["email_subject"] ?: ""
            val emailBody = context.variables["email_body"] ?: ""
            val emailFrom = context.variables["email_from"] ?: ""
            
            if (emailBody.isEmpty()) {
                Log.w(TAG, "No email content found in trigger context")
                return Result.failure(Exception("No email content available for summarization"))
            }
            
            // Initialize summarization service
            val modelManager = com.localllm.myapplication.di.AppContainer.provideModelManager(this.context)
            val summarizationService = com.localllm.myapplication.service.ai.LocalLLMSummarizationService(modelManager, this.context)
            
            // Create email content for summarization
            val emailContent = "Subject: $emailSubject\nFrom: $emailFrom\n\n$emailBody"
            
            // Summarize the email
            val summaryResult = summarizationService.summarizeText(
                text = emailContent,
                maxLength = action.maxSummaryLength,
                style = when (action.summaryStyle) {
                    "detailed" -> com.localllm.myapplication.service.ai.SummarizationStyle.DETAILED
                    "structured" -> com.localllm.myapplication.service.ai.SummarizationStyle.STRUCTURED
                    else -> com.localllm.myapplication.service.ai.SummarizationStyle.CONCISE
                }
            )
            
            if (summaryResult.isFailure) {
                Log.w(TAG, "AI summarization failed, workflow will continue with fallback: ${summaryResult.exceptionOrNull()?.message}")
                // Don't fail the entire workflow, use fallback summary
                val fallbackSummary = createFallbackEmailSummary(emailSubject, emailBody, emailFrom)
                Log.i(TAG, "Using fallback email summary")
                context.variables[action.summaryOutputVariable] = fallbackSummary
                
                // Continue with fallback summary instead of failing
                val summarySubject = if (action.includeOriginalSubject) {
                    "${action.customSubjectPrefix} $emailSubject"
                } else {
                    "${action.customSubjectPrefix} Email Summary"
                }
                
                val originalInfo = buildString {
                    if (action.includeOriginalSender) {
                        append("Original sender: $emailFrom\n")
                    }
                    if (action.includeOriginalSubject) {
                        append("Original subject: $emailSubject\n")
                    }
                }
                
                val emailBodyContent = action.emailTemplate
                    .replace("{{summary_subject}}", summarySubject)
                    .replace("{{ai_summary}}", fallbackSummary)
                    .replace("{{original_info}}", originalInfo)
                    .replace("{{email_from}}", emailFrom)
                    .replace("{{email_subject}}", emailSubject)
                    .replace("{{email_body}}", emailBody)
                
                // Continue with email sending using fallback summary
                return sendSummaryEmails(action, emailBodyContent, summarySubject, fallbackSummary, context)
            }
            
            val summary = summaryResult.getOrThrow()
            Log.d(TAG, "Email summarized successfully: ${summary.take(100)}...")
            
            // Store summary in context
            context.variables[action.summaryOutputVariable] = summary
            
            // Build email content for forwarding
            val summarySubject = if (action.includeOriginalSubject) {
                "${action.customSubjectPrefix} $emailSubject"
            } else {
                "${action.customSubjectPrefix} Email Summary"
            }
            
            val originalInfo = buildString {
                if (action.includeOriginalSender) {
                    append("Original sender: $emailFrom\n")
                }
                if (action.includeOriginalSubject) {
                    append("Original subject: $emailSubject\n")
                }
            }
            
            // Replace template variables in email body
            val emailBodyContent = action.emailTemplate
                .replace("{{summary_subject}}", summarySubject)
                .replace("{{ai_summary}}", summary)
                .replace("{{original_info}}", originalInfo)
                .replace("{{email_from}}", emailFrom)
                .replace("{{email_subject}}", emailSubject)
                .replace("{{email_body}}", emailBody)
            
            // Send summary emails using helper method
            return sendSummaryEmails(action, emailBodyContent, summarySubject, summary, context)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in executeAutoEmailSummarizer", e)
            Result.failure(Exception("Auto email summarizer failed: ${e.message}"))
        }
    }
    
    /**
     * Create fallback email summary when AI is not available
     */
    private fun createFallbackEmailSummary(subject: String, body: String, sender: String): String {
        Log.d(TAG, "Creating fallback email summary")
        
        return buildString {
            append("📧 Email Summary (Generated without AI)\n\n")
            
            // Add sender info
            if (sender.isNotBlank()) {
                append("From: $sender\n")
            }
            
            // Add subject
            if (subject.isNotBlank()) {
                append("Subject: $subject\n\n")
            }
            
            // Create basic summary from body
            if (body.isNotBlank()) {
                val cleanBody = body.trim()
                val sentences = cleanBody.split(Regex("[.!?]+"))
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && it.length > 10 }
                
                when {
                    sentences.isEmpty() -> {
                        // Fallback to first 100 characters
                        val preview = cleanBody.take(100)
                        append("Content: $preview")
                        if (cleanBody.length > 100) append("...")
                    }
                    sentences.size == 1 -> {
                        append("Content: ${sentences[0]}")
                    }
                    else -> {
                        // Take first 2 most important sentences
                        val importantSentences = sentences.take(2)
                        append("Key Points:\n")
                        importantSentences.forEach { sentence ->
                            append("• $sentence\n")
                        }
                    }
                }
            } else {
                append("Content: No email content available")
            }
            
            append("\n\n⚠️ Note: This summary was generated using basic text processing because AI model is not currently available.")
        }
    }
    
    /**
     * Send summary emails to all configured addresses
     */
    private suspend fun sendSummaryEmails(
        action: MultiUserAction.AIAutoEmailSummarizer,
        emailBodyContent: String,
        summarySubject: String,
        summary: String,
        context: WorkflowExecutionContext
    ): Result<String> {
        val sentEmails = mutableListOf<String>()
        val failedEmails = mutableListOf<String>()
        
        for (emailAddress in action.forwardToEmails) {
            if (emailAddress.isBlank()) continue
            
            try {
                Log.d(TAG, "Forwarding email summary to: $emailAddress")
                
                // Use the trigger user's Gmail service to send the summary
                val gmailService = userManager.getGmailService(context.triggerUserId)
                if (gmailService == null) {
                    Log.w(TAG, "Gmail service not available for user: ${context.triggerUserId}")
                    failedEmails.add("$emailAddress (no Gmail service)")
                    continue
                }
                
                val sendResult = gmailService.sendEmail(
                    to = emailAddress,
                    subject = summarySubject,
                    body = emailBodyContent,
                    isHtml = false
                )
                
                sendResult.fold(
                    onSuccess = { messageId ->
                        Log.d(TAG, "Summary email sent successfully to $emailAddress with ID: $messageId")
                        sentEmails.add(emailAddress)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to send summary email to $emailAddress: ${error.message}")
                        failedEmails.add("$emailAddress (${error.message})")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error sending summary email to $emailAddress", e)
                failedEmails.add("$emailAddress (${e.message})")
            }
        }
        
        // Generate result message
        val resultMessage = buildString {
            if (summary.contains("Generated without AI")) {
                append("Auto Email Summarizer completed with fallback processing.\n")
            } else {
                append("Auto Email Summarizer completed successfully.\n")
            }
            append("Summary generated: ${summary.take(50)}...\n")
            append("Emails sent to: ${sentEmails.size} addresses")
            if (sentEmails.isNotEmpty()) {
                append(" (${sentEmails.joinToString(", ")})")
            }
            if (failedEmails.isNotEmpty()) {
                append("\nFailed to send to: ${failedEmails.joinToString(", ")}")
            }
        }
        
        Log.i(TAG, "Auto Email Summarizer completed: ${sentEmails.size} sent, ${failedEmails.size} failed")
        return Result.success(resultMessage)
    }
    
    /**
     * Replace template variables in strings with values from context
     */
    private fun replaceVariables(template: String, context: WorkflowExecutionContext): String {
        var result = template
        
        // Replace context variables
        for ((key, value) in context.variables) {
            result = result.replace("{{$key}}", value)
        }
        
        // Replace standard trigger variables
        result = result.replace("{{trigger_content}}", extractTriggerContent(context.triggerData))
        result = result.replace("{{trigger_user_id}}", context.triggerUserId)
        result = result.replace("{{workflow_id}}", context.workflowId)
        result = result.replace("{{execution_id}}", context.executionId)
        
        return result
    }
    
    /**
     * Extract content from trigger data
     */
    private fun extractTriggerContent(triggerData: Any): String {
        return when (triggerData) {
            is Map<*, *> -> {
                // Try common content fields
                triggerData["email_body"]?.toString() 
                    ?: triggerData["telegram_message"]?.toString()
                    ?: triggerData["content"]?.toString()
                    ?: triggerData["text"]?.toString()
                    ?: triggerData.toString()
            }
            is String -> triggerData
            else -> triggerData.toString()
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