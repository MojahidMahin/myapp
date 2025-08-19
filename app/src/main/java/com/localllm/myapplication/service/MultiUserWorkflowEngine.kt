package com.localllm.myapplication.service

import android.content.Context
import android.util.Log
import com.localllm.myapplication.data.*
import com.localllm.myapplication.service.integration.GmailIntegrationService
import com.localllm.myapplication.service.integration.TelegramBotService
import com.localllm.myapplication.service.ai.AIProcessingFacade
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
                is MultiUserAction.ForwardGmailToTelegram -> executeForwardGmailToTelegram(action, context)
                is MultiUserAction.GmailAISummaryToTelegram -> executeGmailAISummaryToTelegram(action, context)
                is MultiUserAction.ReplyToUserTelegram -> executeReplyToUserTelegram(action, context)
                is MultiUserAction.AutoReplyTelegram -> executeAutoReplyTelegram(action, context)
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
                is MultiUserAction.AI24HourGalleryAnalysis -> execute24HourGalleryAnalysis(action, context)
                is MultiUserAction.AIImageWorkflowOrchestrator -> executeImageWorkflowOrchestrator(action, context)
                
                // Image Analysis Actions
                is MultiUserAction.AIImageAnalysisAction -> executeImageAnalysisAction(action, context)
                is MultiUserAction.AIBatchImageAnalysisAction -> executeBatchImageAnalysisAction(action, context)
                is MultiUserAction.AIImageComparisonAction -> executeImageComparisonAction(action, context)
                
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
        Log.i(TAG, "📤 === EXECUTING SEND TO USER TELEGRAM ===")
        Log.i(TAG, "🎯 Target User ID: ${action.targetUserId}")
        Log.d(TAG, "💬 Specified Chat ID: ${action.chatId}")
        Log.d(TAG, "🎨 Parse Mode: ${action.parseMode}")
        
        // STEP 1: Resolve target user ID (handle empty targetUserId by using trigger user)
        val resolvedTargetUserId = if (action.targetUserId.isBlank()) {
            Log.w(TAG, "⚠️ Empty targetUserId, using trigger user: ${context.triggerUserId}")
            context.triggerUserId
        } else {
            action.targetUserId
        }
        
        Log.i(TAG, "🔧 SPECIAL HANDLING: For Telegram replies, prioritizing trigger chat over user profile")
        // If this is a Telegram-triggered workflow, the chat ID should come from the trigger
        val hasTelegramTriggerData = context.variables.containsKey("telegram_chat_id") || context.variables.containsKey("telegram_user_id")
        if (hasTelegramTriggerData) {
            Log.i(TAG, "📱 Telegram trigger detected - will use trigger chat data for reply")
        }
        
        Log.d(TAG, "🔎 STEP W1: Getting Telegram service for user $resolvedTargetUserId")
        val telegramService = userManager.getTelegramService(resolvedTargetUserId)
        if (telegramService == null) {
            Log.e(TAG, "❌ STEP W1: Telegram service not available for user $resolvedTargetUserId")
            return Result.failure(Exception(
                "Telegram service not configured for user $resolvedTargetUserId. " +
                "Please ensure the Telegram bot is properly connected in the Workflow Manager."
            ))
        }
        Log.i(TAG, "✅ STEP W1: Telegram service obtained")
        
        Log.d(TAG, "🔎 STEP W2: Getting target user details")
        val targetUser = userManager.getUserById(resolvedTargetUserId).getOrNull()
        if (targetUser == null) {
            Log.e(TAG, "❌ STEP W2: Target user not found: $resolvedTargetUserId")
            return Result.failure(Exception("Target user not found: $resolvedTargetUserId"))
        }
        
        Log.i(TAG, "✅ STEP W2: Target user found")
        Log.d(TAG, "👤 User Name: ${targetUser.displayName}")
        Log.d(TAG, "📧 User Email: ${targetUser.email}")
        Log.d(TAG, "🆔 User Telegram ID: ${targetUser.telegramUserId}")
        Log.d(TAG, "🔗 Telegram Connected: ${targetUser.telegramConnected}")
        Log.d(TAG, "👤 Telegram Username: ${targetUser.telegramUsername}")
        
        // STEP 3: Determine target chat ID using real bot data
        Log.d(TAG, "🎯 STEP W3: Determining target chat ID...")
        Log.d(TAG, "📋 Available context variables: ${context.variables.keys}")
        
        val chatId = determineChatId(action, targetUser, context)
        if (chatId == null) {
            Log.e(TAG, "❌ STEP W3: Could not determine valid chat ID for real Telegram bot")
            return Result.failure(Exception(
                "No valid Telegram chat ID found. Possible solutions:\n" +
                "1. Make sure users have started a conversation with your bot\n" +
                "2. Check the Telegram tab in Workflow Manager for saved users\n" +
                "3. Send a test message to your bot first to establish chat"
            ))
        }
        
        Log.i(TAG, "✅ STEP W3: Using chat ID: $chatId")
        
        // STEP 4: Process message template
        Log.d(TAG, "🔄 STEP W4: Processing message template...")
        Log.d(TAG, "📝 Original template: '${action.text}'")
        val processedText = replaceVariables(action.text, context.variables)
        Log.i(TAG, "✅ STEP W4: Template processed")
        Log.d(TAG, "📝 Final message: '$processedText'")
        
        // STEP 5: Send message via real Telegram bot
        Log.i(TAG, "🚀 STEP W5: Sending message to Telegram...")
        Log.i(TAG, "🎯 Target Chat ID: $chatId")
        Log.i(TAG, "📝 Message Length: ${processedText.length} characters")
        
        return telegramService.sendMessage(chatId, processedText, action.parseMode).fold(
            onSuccess = { messageId ->
                Log.i(TAG, "🎉 SUCCESS: Message sent to Telegram!")
                Log.i(TAG, "🆔 Telegram Message ID: $messageId")
                Log.i(TAG, "🎯 Sent to Chat: $chatId")
                Log.i(TAG, "👤 Target User: ${targetUser.displayName}")
                Result.success("Message sent to Telegram chat $chatId: Message ID $messageId")
            },
            onFailure = { error ->
                Log.e(TAG, "❌ FAILED: Could not send Telegram message")
                Log.e(TAG, "💥 Error type: ${error.javaClass.simpleName}")
                Log.e(TAG, "💬 Error message: ${error.message}")
                Log.e(TAG, "🎯 Failed Chat ID: $chatId")
                Log.e(TAG, "👤 Target User: ${targetUser.displayName}")
                
                // Provide specific error guidance
                val errorMessage = when {
                    error.message?.contains("chat not found") == true -> 
                        "Chat $chatId not found. The user needs to start a conversation with your bot first. Go to Telegram and send any message to your bot."
                    error.message?.contains("bot was blocked") == true -> 
                        "Bot was blocked by user in chat $chatId. The user needs to unblock your bot."
                    error.message?.contains("not enough rights") == true -> 
                        "Bot doesn't have permission to send messages in chat $chatId. Check bot permissions."
                    error.message?.contains("Bad Request") == true ->
                        "Invalid request. The chat ID $chatId may not exist or the bot doesn't have access to it."
                    else -> "Failed to send message to chat $chatId: ${error.message}"
                }
                
                Result.failure(Exception(errorMessage))
            }
        )
    }
    
    /**
     * Determine chat ID for real Telegram bot usage
     */
    private suspend fun determineChatId(action: MultiUserAction.SendToUserTelegram, targetUser: WorkflowUser, context: WorkflowExecutionContext): Long? {
        Log.d(TAG, "🔍 Determining chat ID with priority order:")
        Log.d(TAG, "   1. Specified chat ID: ${action.chatId}")
        Log.d(TAG, "   2. Trigger chat ID: ${context.variables["telegram_chat_id"]}")
        Log.d(TAG, "   3. Trigger user ID: ${context.variables["telegram_user_id"]}")
        Log.d(TAG, "   4. Target user Telegram ID: ${targetUser.telegramUserId}")
        
        return when {
            // 1. Use explicitly specified chat ID
            action.chatId != null -> {
                Log.i(TAG, "✅ Using specified chat ID: ${action.chatId}")
                action.chatId
            }
            // 2. Use chat ID from trigger context (HIGHEST PRIORITY for replies)
            context.variables["telegram_chat_id"]?.toLongOrNull() != null -> {
                val chatId = context.variables["telegram_chat_id"]!!.toLong()
                Log.i(TAG, "✅ Using trigger chat ID (reply to same chat): $chatId")
                chatId
            }
            // 3. Use user ID from trigger context (for private messages)
            context.variables["telegram_user_id"]?.toLongOrNull() != null -> {
                val userId = context.variables["telegram_user_id"]!!.toLong()
                Log.i(TAG, "✅ Using trigger user ID as chat ID: $userId")
                userId
            }
            // 4. Use target user's Telegram ID (if they have connected Telegram)
            targetUser.telegramUserId != null -> {
                Log.i(TAG, "✅ Using target user's Telegram ID: ${targetUser.telegramUserId}")
                targetUser.telegramUserId
            }
            // 5. Try to get chat ID from saved Telegram users
            else -> {
                Log.w(TAG, "⚠️ No direct chat ID found, checking saved Telegram users...")
                val savedUsers = getSavedTelegramUsers()
                if (savedUsers.isNotEmpty()) {
                    val firstUser = savedUsers.values.first()
                    Log.i(TAG, "✅ Using saved Telegram user chat ID: ${firstUser.id}")
                    firstUser.id
                } else {
                    Log.w(TAG, "⚠️ No saved Telegram users found")
                    Log.w(TAG, "💡 To fix this: Go to Telegram → send any message to your bot → try workflow again")
                    null
                }
            }
        }
    }
    
    /**
     * Get saved Telegram users from preferences
     */
    private fun getSavedTelegramUsers(): Map<Long, TelegramUser> {
        return try {
            val telegramPrefs = TelegramPreferences(context)
            telegramPrefs.getSavedUsers()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get saved Telegram users", e)
            emptyMap()
        }
    }
    
    /**
     * Resolve chat ID for Telegram reply - can use trigger context or specified value
     */
    private suspend fun resolveReplyContext(action: MultiUserAction.ReplyToUserTelegram, context: WorkflowExecutionContext): Long? {
        Log.d(TAG, "🔍 Resolving reply chat ID:")
        Log.d(TAG, "   1. Action chat ID: ${action.chatId}")
        Log.d(TAG, "   2. Trigger chat ID: ${context.variables["telegram_chat_id"]}")
        
        return when {
            // 1. Use explicitly specified chat ID (highest priority)
            action.chatId != 0L -> {
                Log.i(TAG, "✅ Using action chat ID: ${action.chatId}")
                action.chatId
            }
            // 2. Use chat ID from trigger context (for auto-replies)
            context.variables["telegram_chat_id"]?.toLongOrNull() != null -> {
                val chatId = context.variables["telegram_chat_id"]!!.toLong()
                Log.i(TAG, "✅ Using trigger chat ID: $chatId")
                chatId
            }
            // 3. Use user ID from trigger context as chat ID
            context.variables["telegram_user_id"]?.toLongOrNull() != null -> {
                val userId = context.variables["telegram_user_id"]!!.toLong()
                Log.i(TAG, "✅ Using trigger user ID as chat: $userId")
                userId
            }
            // 4. No valid chat ID found
            else -> {
                Log.w(TAG, "⚠️ No valid chat ID found for reply")
                null
            }
        }
    }
    
    /**
     * Resolve message ID to reply to - can use trigger context or specified value
     */
    private suspend fun resolveReplyMessageId(action: MultiUserAction.ReplyToUserTelegram, context: WorkflowExecutionContext): Long? {
        Log.d(TAG, "🔍 Resolving reply message ID:")
        Log.d(TAG, "   1. Action message ID: ${action.replyToMessageId}")
        Log.d(TAG, "   2. Trigger message ID: ${context.variables["telegram_message_id"]}")
        
        return when {
            // 1. Use explicitly specified message ID (highest priority)
            action.replyToMessageId != 0L -> {
                Log.i(TAG, "✅ Using action message ID: ${action.replyToMessageId}")
                action.replyToMessageId
            }
            // 2. Use message ID from trigger context (for auto-replies)
            context.variables["telegram_message_id"]?.toLongOrNull() != null -> {
                val messageId = context.variables["telegram_message_id"]!!.toLong()
                Log.i(TAG, "✅ Using trigger message ID: $messageId")
                messageId
            }
            // 3. No valid message ID found
            else -> {
                Log.w(TAG, "⚠️ No valid message ID found for reply")
                null
            }
        }
    }
    
    /**
     * Determine chat ID for Gmail forwarding (different from Telegram replies)
     */
    private suspend fun determineGmailForwardChatId(action: MultiUserAction.ForwardGmailToTelegram, targetUser: WorkflowUser, context: WorkflowExecutionContext): Long? {
        Log.d(TAG, "🔍 Determining chat ID for Gmail forwarding:")
        Log.d(TAG, "   1. Specified chat ID: ${action.chatId}")
        Log.d(TAG, "   2. Target user Telegram ID: ${targetUser.telegramUserId}")
        Log.d(TAG, "   3. Target user connected: ${targetUser.telegramConnected}")
        
        return when {
            // 1. Use explicitly specified chat ID
            action.chatId != null -> {
                Log.i(TAG, "✅ Using specified chat ID: ${action.chatId}")
                action.chatId
            }
            // 2. Use target user's Telegram ID (if they have connected)
            targetUser.telegramUserId != null -> {
                Log.i(TAG, "✅ Using target user's Telegram ID: ${targetUser.telegramUserId}")
                targetUser.telegramUserId
            }
            // 3. Use first available saved Telegram user (most common case for Gmail forwarding)
            else -> {
                Log.w(TAG, "⚠️ No specific target, using saved Telegram users from bot interactions...")
                val savedUsers = getSavedTelegramUsers()
                Log.d(TAG, "📊 Found ${savedUsers.size} saved Telegram users")
                
                if (savedUsers.isNotEmpty()) {
                    val firstUser = savedUsers.values.first()
                    Log.i(TAG, "✅ Using first saved Telegram user: ${firstUser.firstName} ${firstUser.lastName} (ID: ${firstUser.id})")
                    Log.i(TAG, "   👤 Username: @${firstUser.username ?: "N/A"}")
                    firstUser.id
                } else {
                    Log.w(TAG, "⚠️ No saved Telegram users found")
                    Log.w(TAG, "💡 To fix: Go to your Telegram bot → send any message → check Workflow Manager > Telegram tab")
                    null
                }
            }
        }
    }
    
    
    private suspend fun executeForwardGmailToTelegram(action: MultiUserAction.ForwardGmailToTelegram, context: WorkflowExecutionContext): Result<String> {
        Log.i(TAG, "📧➡️✈️ === EXECUTING FORWARD GMAIL TO TELEGRAM ===")
        Log.i(TAG, "🎯 Target User ID: ${action.targetUserId}")
        
        val telegramService = userManager.getTelegramService(action.targetUserId)
        if (telegramService == null) {
            return Result.failure(Exception(
                "Telegram service not available for user ${action.targetUserId}. " +
                "Please ensure the Telegram bot is properly connected in the Workflow Manager."
            ))
        }
        
        val targetUser = userManager.getUserById(action.targetUserId).getOrNull()
            ?: return Result.failure(Exception("Target user not found"))
        
        // Determine chat ID for Gmail forwarding (different logic than Telegram replies)
        val chatId = determineGmailForwardChatId(action, targetUser, context)
        if (chatId == null) {
            return Result.failure(Exception(
                "No Telegram chat ID available for Gmail forwarding. Please ensure:\n" +
                "1. Someone has sent a message to your bot (check Telegram tab in Workflow Manager)\n" +
                "2. Or specify a target user who has connected their Telegram account\n" +
                "3. Or set a specific chat ID in the workflow action"
            ))
        }
        
        Log.i(TAG, "✅ Using chat ID for Gmail forward: $chatId")
        
        // Build the message content from Gmail data
        val messageBuilder = StringBuilder()
        
        // Use custom template if provided
        if (!action.messageTemplate.isNullOrBlank()) {
            val processedTemplate = replaceVariables(action.messageTemplate, context.variables)
            messageBuilder.append(processedTemplate)
        } else {
            // Default formatting
            messageBuilder.append("📧 *Gmail Message Forwarded*\n\n")
            
            if (action.includeFrom && context.variables.containsKey("email_from")) {
                messageBuilder.append("👤 *From:* ${context.variables["email_from"]}\n")
            }
            
            if (action.includeSubject && context.variables.containsKey("email_subject")) {
                messageBuilder.append("📝 *Subject:* ${context.variables["email_subject"]}\n")
            }
            
            if (action.includeBody && context.variables.containsKey("email_body")) {
                var emailBody = context.variables["email_body"] ?: ""
                
                // Summarize if requested
                if (action.summarize && emailBody.length > 100) {
                    try {
                        val summarizeAction = MultiUserAction.AISummarizeContent(
                            content = emailBody,
                            maxLength = 150,
                            outputVariable = "summarized_content"
                        )
                        val summarizeResult = aiProcessor.processSummarizeContent(summarizeAction, context)
                        summarizeResult.fold(
                            onSuccess = { 
                                emailBody = context.variables["summarized_content"] ?: emailBody
                                messageBuilder.append("\n🤖 *AI Summary:*\n")
                            },
                            onFailure = { 
                                Log.w(TAG, "Failed to summarize email content: ${it.message}")
                                messageBuilder.append("\n📄 *Content:*\n")
                            }
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Error during summarization: ${e.message}")
                        messageBuilder.append("\n📄 *Content:*\n")
                    }
                } else {
                    messageBuilder.append("\n📄 *Content:*\n")
                }
                
                // Truncate if too long
                if (emailBody.length > 2000) {
                    emailBody = emailBody.take(2000) + "..."
                }
                
                messageBuilder.append(emailBody)
            }
        }
        
        val finalMessage = messageBuilder.toString().trim()
        
        Log.i(TAG, "📝 Gmail forward message preview: ${finalMessage.take(100)}...")
        Log.i(TAG, "📊 Message length: ${finalMessage.length} characters")
        
        // Send to Telegram with Markdown formatting
        return telegramService.sendMessage(chatId, finalMessage, "Markdown").fold(
            onSuccess = { messageId ->
                Log.i(TAG, "🎉 SUCCESS: Gmail forwarded to Telegram!")
                Log.i(TAG, "🆔 Telegram Message ID: $messageId")
                Log.i(TAG, "🎯 Sent to Chat: $chatId")
                Log.i(TAG, "📧 Original email from: ${context.variables["email_from"]}")
                Log.i(TAG, "📝 Subject: ${context.variables["email_subject"]}")
                Result.success("Gmail content forwarded to Telegram chat $chatId: Message ID $messageId")
            },
            onFailure = { error ->
                Log.e(TAG, "❌ FAILED: Could not forward Gmail to Telegram")
                Log.e(TAG, "💥 Error type: ${error.javaClass.simpleName}")
                Log.e(TAG, "💬 Error message: ${error.message}")
                Log.e(TAG, "🎯 Failed Chat ID: $chatId")
                Log.e(TAG, "📧 Email from: ${context.variables["email_from"]}")
                
                // Provide specific error guidance
                val errorMessage = when {
                    error.message?.contains("chat not found") == true -> 
                        "Chat $chatId not found. The user needs to start a conversation with your bot first."
                    error.message?.contains("bot was blocked") == true -> 
                        "Bot was blocked by user in chat $chatId."
                    error.message?.contains("not enough rights") == true -> 
                        "Bot doesn't have permission to send messages in chat $chatId."
                    error.message?.contains("Bad Request") == true ->
                        "Invalid request. The chat ID $chatId may not exist or be accessible."
                    else -> "Failed to forward Gmail to chat $chatId: ${error.message}"
                }
                
                Result.failure(Exception(errorMessage))
            }
        )
    }
    
    /**
     * Execute Gmail AI Summary to Telegram action using saved Telegram preferences
     * Uses bot token from Telegram tab and selected contact from saved users
     */
    private suspend fun executeGmailAISummaryToTelegram(action: MultiUserAction.GmailAISummaryToTelegram, context: WorkflowExecutionContext): Result<String> {
        Log.i(TAG, "📧🤖✈️ === EXECUTING SIMPLIFIED GMAIL AI SUMMARY TO TELEGRAM ===")
        Log.i(TAG, "💬 Selected Contact ID: ${action.selectedContactId}")
        Log.i(TAG, "📏 Max Words: ${action.maxSummaryWords}")
        
        try {
            // Step 1: Get bot token and contact info from TelegramPreferences
            val telegramPrefs = com.localllm.myapplication.data.TelegramPreferences(this.context)
            val botToken = telegramPrefs.getBotToken()
            
            if (botToken.isNullOrEmpty()) {
                return Result.failure(Exception("No Telegram bot token found. Please set up Telegram bot in the Telegram tab first."))
            }
            
            // Get selected contact from saved users
            val savedUsers = telegramPrefs.getSavedUsers()
            val selectedContact = savedUsers[action.selectedContactId]
            
            if (selectedContact == null) {
                return Result.failure(Exception("Selected Telegram contact not found. Contact ID: ${action.selectedContactId}"))
            }
            
            Log.i(TAG, "🤖 Using bot token: ${botToken.take(8)}:***...")
            Log.i(TAG, "👤 Sending to: ${selectedContact.displayName} (ID: ${selectedContact.id})")
            
            // Step 2: Create Telegram service with existing bot token
            val telegramService = TelegramBotService(this.context)
            val initResult = telegramService.initializeBot(botToken)
            initResult.fold(
                onSuccess = { username ->
                    Log.i(TAG, "✅ Telegram bot initialized: @$username")
                },
                onFailure = { error ->
                    Log.e(TAG, "❌ Failed to initialize bot: ${error.message}")
                    return Result.failure(Exception("Failed to initialize Telegram bot: ${error.message}"))
                }
            )
            
            // Step 3: Extract email content from context variables
            val emailFrom = context.variables["email_from"] ?: "Unknown Sender"
            val emailSubject = context.variables["email_subject"] ?: "No Subject"
            val emailBody = context.variables["email_body"] ?: ""
            
            if (emailBody.isEmpty()) {
                return Result.failure(Exception("No email content available for summarization"))
            }
            
            Log.i(TAG, "📧 Processing email from: $emailFrom")
            Log.i(TAG, "📝 Subject: $emailSubject")
            Log.i(TAG, "📄 Body length: ${emailBody.length} characters")
            
            // Step 4: Generate AI summary
            val emailContent = buildString {
                append("Subject: $emailSubject\n")
                append("From: $emailFrom\n")
                append("Email Content: $emailBody")
            }
            
            val summaryAction = MultiUserAction.AISummarizeContent(
                content = emailContent,
                maxLength = action.maxSummaryWords,
                outputVariable = action.outputSummaryVariable
            )
            
            Log.i(TAG, "🧠 Generating AI summary (max ${action.maxSummaryWords} words)...")
            val summaryResult = aiProcessor.processSummarizeContent(summaryAction, context)
            
            val aiSummary = summaryResult.fold(
                onSuccess = {
                    val summary = context.variables[action.outputSummaryVariable] ?: "Summary not available"
                    Log.i(TAG, "✅ AI summary generated: ${summary.take(100)}...")
                    summary
                },
                onFailure = { error ->
                    Log.w(TAG, "❌ AI summarization failed: ${error.message}")
                    return Result.failure(Exception("AI summarization failed: ${error.message}"))
                }
            )
            
            // Step 5: Build simple Telegram message with fixed template
            val telegramMessage = buildString {
                appendLine("📧 *Gmail Summary*")
                appendLine()
                appendLine("*From:* $emailFrom")
                appendLine("*Subject:* $emailSubject")
                appendLine()
                appendLine("*Summary:*")
                appendLine(aiSummary)
                appendLine()
                append("_Sent via AI Workflow Automation_")
            }
            
            Log.i(TAG, "📱 Final message preview: ${telegramMessage.take(150)}...")
            Log.i(TAG, "📊 Message length: ${telegramMessage.length} characters")
            
            // Step 6: Send to Telegram
            Log.i(TAG, "📤 Sending AI summary to ${selectedContact.displayName}...")
            val sendResult = telegramService.sendMessage(
                chatId = action.selectedContactId,
                text = telegramMessage,
                parseMode = "Markdown"
            )
            
            return sendResult.fold(
                onSuccess = { messageId ->
                    Log.i(TAG, "🎉 SUCCESS: Gmail AI Summary sent to Telegram!")
                    Log.i(TAG, "🆔 Telegram Message ID: $messageId")
                    Log.i(TAG, "👤 Sent to: ${selectedContact.displayName}")
                    Log.i(TAG, "💬 Chat ID: ${action.selectedContactId}")
                    Log.i(TAG, "📧 Original email from: $emailFrom")
                    Log.i(TAG, "📝 Subject: $emailSubject")
                    Log.i(TAG, "🤖 Summary words: ~${aiSummary.split(" ").size}")
                    
                    // Store summary in context for potential further use
                    context.variables[action.outputSummaryVariable] = aiSummary
                    
                    Result.success("AI-powered Gmail summary sent to ${selectedContact.displayName}: Message ID $messageId")
                },
                onFailure = { error ->
                    Log.e(TAG, "❌ FAILED: Could not send AI summary to Telegram")
                    Log.e(TAG, "💥 Error: ${error.message}")
                    Log.e(TAG, "👤 Target: ${selectedContact.displayName}")
                    Log.e(TAG, "💬 Chat ID: ${action.selectedContactId}")
                    
                    val errorMessage = when {
                        error.message?.contains("chat not found") == true -> 
                            "Chat with ${selectedContact.displayName} not found. They may need to start a conversation with your bot first."
                        error.message?.contains("bot was blocked") == true -> 
                            "${selectedContact.displayName} has blocked your bot. Please ask them to unblock it."
                        error.message?.contains("Forbidden") == true || error.message?.contains("401") == true ->
                            "Bot doesn't have permission to send messages to ${selectedContact.displayName}."
                        error.message?.contains("Bad Request") == true ->
                            "Invalid message format or chat ID for ${selectedContact.displayName}."
                        else -> "Failed to send AI summary to ${selectedContact.displayName}: ${error.message}"
                    }
                    
                    Result.failure(Exception(errorMessage))
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 Unexpected error in Gmail AI Summary to Telegram", e)
            return Result.failure(Exception("Unexpected error: ${e.message}"))
        }
    }
    
    private suspend fun executeReplyToUserTelegram(action: MultiUserAction.ReplyToUserTelegram, context: WorkflowExecutionContext): Result<String> {
        Log.i(TAG, "💬 === EXECUTING REPLY TO TELEGRAM MESSAGE ===")
        Log.i(TAG, "🎯 Target User ID: ${action.targetUserId}")
        Log.i(TAG, "🎯 Chat ID: ${action.chatId}")
        Log.i(TAG, "📝 Reply to Message ID: ${action.replyToMessageId}")
        
        // STEP 1: Resolve target user ID if empty
        val resolvedTargetUserId = if (action.targetUserId.isBlank()) {
            Log.w(TAG, "⚠️ Empty targetUserId, using trigger user: ${context.triggerUserId}")
            context.triggerUserId
        } else {
            action.targetUserId
        }
        
        // STEP 2: Get Telegram service
        val telegramService = userManager.getTelegramService(resolvedTargetUserId)
        if (telegramService == null) {
            return Result.failure(Exception(
                "Telegram service not available for user $resolvedTargetUserId. " +
                "Please ensure the Telegram bot is properly connected in the Workflow Manager."
            ))
        }
        
        // STEP 3: Resolve chat ID and message ID from context if needed
        val effectiveChatId = resolveReplyContext(action, context)
        val effectiveMessageId = resolveReplyMessageId(action, context)
        
        if (effectiveChatId == null) {
            return Result.failure(Exception(
                "No valid chat ID for Telegram reply. Please ensure:\n" +
                "1. The chat ID is specified in the action, or\n" +
                "2. This workflow is triggered by a Telegram message, or\n" +
                "3. A target user has connected their Telegram account"
            ))
        }
        
        if (effectiveMessageId == null) {
            return Result.failure(Exception(
                "No valid message ID to reply to. Please ensure:\n" +
                "1. The message ID is specified in the action, or\n" +
                "2. This workflow is triggered by a Telegram message"
            ))
        }
        
        Log.i(TAG, "✅ Using effective chat ID: $effectiveChatId")
        Log.i(TAG, "✅ Using effective message ID: $effectiveMessageId")
        
        // STEP 4: Process message text
        val processedText = replaceVariables(action.text, context.variables)
        Log.d(TAG, "📝 Reply text: '$processedText'")
        Log.d(TAG, "📊 Text length: ${processedText.length} characters")
        
        // STEP 5: Send reply to Telegram
        Log.i(TAG, "🚀 Sending reply to Telegram...")
        return telegramService.replyToMessage(effectiveChatId, effectiveMessageId, processedText, action.parseMode).fold(
            onSuccess = { messageId ->
                Log.i(TAG, "🎉 SUCCESS: Reply sent to Telegram!")
                Log.i(TAG, "🆔 New Message ID: $messageId")
                Log.i(TAG, "🎯 Replied in Chat: $effectiveChatId")
                Log.i(TAG, "💬 Replied to Message: $effectiveMessageId")
                Result.success("Reply sent to Telegram chat $effectiveChatId: Message ID $messageId")
            },
            onFailure = { error ->
                Log.e(TAG, "❌ FAILED: Could not send Telegram reply")
                Log.e(TAG, "💥 Error type: ${error.javaClass.simpleName}")
                Log.e(TAG, "💬 Error message: ${error.message}")
                Log.e(TAG, "🎯 Failed Chat ID: $effectiveChatId")
                Log.e(TAG, "💬 Failed Message ID: $effectiveMessageId")
                
                // Provide specific error guidance
                val errorMessage = when {
                    error.message?.contains("chat not found") == true -> 
                        "Chat $effectiveChatId not found. The user needs to start a conversation with your bot first."
                    error.message?.contains("message to reply not found") == true ->
                        "Message $effectiveMessageId not found in chat $effectiveChatId. The message may have been deleted."
                    error.message?.contains("bot was blocked") == true -> 
                        "Bot was blocked by user in chat $effectiveChatId."
                    error.message?.contains("not enough rights") == true -> 
                        "Bot doesn't have permission to send messages in chat $effectiveChatId."
                    error.message?.contains("Bad Request") == true ->
                        "Invalid request. Check chat ID ($effectiveChatId) and message ID ($effectiveMessageId)."
                    else -> "Failed to send reply to chat $effectiveChatId: ${error.message}"
                }
                
                Result.failure(Exception(errorMessage))
            }
        )
    }
    
    private suspend fun executeAutoReplyTelegram(action: MultiUserAction.AutoReplyTelegram, context: WorkflowExecutionContext): Result<String> {
        Log.i(TAG, "🤖 === EXECUTING AUTO-REPLY TELEGRAM ACTION ===")
        Log.d(TAG, "💬 Auto-reply Text: '${action.autoReplyText}'")
        
        // STEP 1: Get Telegram service for auto-reply (use the bot that received the message)
        val botUserId = context.triggerUserId
        val telegramService = userManager.getTelegramService(botUserId)
        if (telegramService == null) {
            Log.e(TAG, "❌ No Telegram service available for bot user: $botUserId")
            return Result.failure(Exception("No Telegram service available for bot user $botUserId"))
        }
        Log.i(TAG, "✅ Telegram service found for bot user: $botUserId")
        
        // STEP 2: Extract trigger information for filtering and auto-reply target
        val triggerMessageText = context.variables["message_text"] ?: context.variables["trigger_content"] ?: ""
        val triggerChatId = context.variables["telegram_chat_id"]?.toLongOrNull() ?: 0L
        val triggerMessageId = context.variables["telegram_message_id"]?.toLongOrNull() ?: 0L
        val triggerChatType = context.variables["chat_type"] ?: "private"
        
        Log.d(TAG, "📨 Trigger Message Text: '$triggerMessageText'")
        Log.d(TAG, "💬 Trigger Chat ID (auto-reply target): $triggerChatId")
        Log.d(TAG, "🆔 Trigger Message ID: $triggerMessageId")
        Log.d(TAG, "🏷️ Trigger Chat Type: '$triggerChatType'")
        Log.i(TAG, "🎯 Auto-reply will be sent to the message sender in chat: $triggerChatId")
        
        // STEP 3: Process auto-reply text
        val processedText = replaceVariables(action.autoReplyText, context.variables)
        Log.d(TAG, "📝 Processed auto-reply text: '$processedText'")
        
        // STEP 4: Send auto-reply to the message sender (always as reply if possible)
        return if (triggerMessageId > 0) {
            // Reply to the original message
            Log.i(TAG, "↩️ Sending auto-reply as reply to message $triggerMessageId")
            telegramService.replyToMessage(triggerChatId, triggerMessageId, processedText, null).fold(
                onSuccess = { messageId ->
                    Log.i(TAG, "🎉 SUCCESS: Auto-reply sent as reply!")
                    Log.i(TAG, "🆔 New Message ID: $messageId")
                    Result.success("Auto-reply sent as reply to message $triggerMessageId in chat $triggerChatId")
                },
                onFailure = { error ->
                    Log.e(TAG, "❌ FAILED: Could not send auto-reply as reply")
                    Log.e(TAG, "💥 Error: ${error.message}")
                    Result.failure(Exception("Failed to send auto-reply as reply: ${error.message}"))
                }
            )
        } else {
            // Send as new message if no message ID available
            Log.i(TAG, "📤 Sending auto-reply as new message")
            telegramService.sendMessage(triggerChatId, processedText, null).fold(
                onSuccess = { messageId ->
                    Log.i(TAG, "🎉 SUCCESS: Auto-reply sent as new message!")
                    Log.i(TAG, "🆔 New Message ID: $messageId")
                    Result.success("Auto-reply sent as new message to chat $triggerChatId")
                },
                onFailure = { error ->
                    Log.e(TAG, "❌ FAILED: Could not send auto-reply as new message")
                    Log.e(TAG, "💥 Error: ${error.message}")
                    Result.failure(Exception("Failed to send auto-reply as new message: ${error.message}"))
                }
            )
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
            
            // Get email data from trigger context with detailed debugging
            Log.d(TAG, "=== EXTRACTING EMAIL DATA FROM CONTEXT ===")
            Log.d(TAG, "Available context variables: ${context.variables.keys}")
            
            val emailSubject = context.variables["email_subject"] ?: ""
            val emailBody = context.variables["email_body"] ?: ""
            val emailFrom = context.variables["email_from"] ?: ""
            
            Log.d(TAG, "Extracted email_subject: '$emailSubject'")
            Log.d(TAG, "Extracted email_body length: ${emailBody.length} characters")
            Log.d(TAG, "Extracted email_body preview: '${emailBody.take(200)}${if (emailBody.length > 200) "..." else ""}'")
            Log.d(TAG, "Extracted email_from: '$emailFrom'")
            
            if (emailBody.isEmpty()) {
                Log.e(TAG, "❌ EMAIL BODY IS EMPTY!")
                Log.e(TAG, "Context variables dump: ${context.variables}")
                Log.w(TAG, "No email content found in trigger context")
                return Result.failure(Exception("No email content available for summarization"))
            } else {
                Log.d(TAG, "✅ Email body extracted successfully (${emailBody.length} chars)")
            }
            
            // Initialize summarization service
            val modelManager = com.localllm.myapplication.di.AppContainer.provideModelManager(this.context)
            val summarizationService = com.localllm.myapplication.service.ai.LocalLLMSummarizationService(modelManager, this.context)
            
            // Create concise email content for LLM understanding
            val emailContent = buildString {
                appendLine("FROM: $emailFrom")
                appendLine("SUBJECT: $emailSubject")
                appendLine("BODY: ${emailBody.trim()}")
            }
            
            Log.d(TAG, "=== STARTING EMAIL SUMMARIZATION ===")
            Log.d(TAG, "Email content length: ${emailContent.length} characters")
            Log.d(TAG, "Email body length: ${emailBody.length} characters")
            Log.d(TAG, "Subject: '$emailSubject'")
            Log.d(TAG, "Sender: '$emailFrom'")
            Log.d(TAG, "Max summary length: ${action.maxSummaryLength}")
            Log.d(TAG, "Summary style: ${action.summaryStyle}")
            Log.d(TAG, "Email body preview: '${emailBody.take(200)}${if (emailBody.length > 200) "..." else ""}'")
            
            Log.d(TAG, "=== FULL EMAIL CONTENT BEING SENT TO LLM ===")
            Log.d(TAG, "Complete emailContent:")
            Log.d(TAG, emailContent)
            Log.d(TAG, "=== END OF EMAIL CONTENT ===")
            
            // Summarize the email using local LLM
            val summaryResult = summarizationService.summarizeText(
                text = emailContent,
                maxLength = action.maxSummaryLength,
                style = when (action.summaryStyle) {
                    "detailed" -> com.localllm.myapplication.service.ai.SummarizationStyle.DETAILED
                    "structured" -> com.localllm.myapplication.service.ai.SummarizationStyle.STRUCTURED
                    else -> com.localllm.myapplication.service.ai.SummarizationStyle.CONCISE
                }
            )
            
            Log.d(TAG, "Summarization result: ${if (summaryResult.isSuccess) "SUCCESS" else "FAILED"}")
            
            if (summaryResult.isFailure) {
                Log.e(TAG, "AI summarization failed: ${summaryResult.exceptionOrNull()?.message}", summaryResult.exceptionOrNull())
                Log.w(TAG, "Workflow will continue with fallback summarization")
                
                // Use fallback summary when AI fails
                val fallbackSummary = createFallbackEmailSummary(emailSubject, emailBody, emailFrom)
                Log.i(TAG, "Generated fallback summary: ${fallbackSummary.take(100)}...")
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
        Log.d(TAG, "Creating intelligent fallback email summary")
        Log.d(TAG, "Fallback input - Subject: '$subject', Body length: ${body.length}, Sender: '$sender'")
        
        return buildString {
            // Start with a more natural summary approach
            append("📧 Email Summary\n\n")
            
            // Analyze the email content more intelligently
            val cleanBody = body.trim()
            
            // Extract meaningful content
            if (cleanBody.isNotBlank()) {
                // Look for key patterns in email content
                val sentences = cleanBody.split(Regex("[.!?]+"))
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && it.length > 15 }
                
                // Analyze content for important information
                val keywordPatterns = listOf(
                    "request", "please", "need", "want", "would like", "asking",
                    "meeting", "call", "schedule", "time", "date",
                    "project", "work", "task", "assignment",
                    "urgent", "important", "asap", "immediately",
                    "question", "help", "support", "issue", "problem"
                )
                
                val importantSentences = sentences.take(3).filter { sentence ->
                    // Prioritize sentences with keywords or questions
                    keywordPatterns.any { keyword -> 
                        sentence.lowercase().contains(keyword.lowercase()) 
                    } || sentence.contains("?") || sentence.length > 30
                }
                
                when {
                    importantSentences.isNotEmpty() -> {
                        append("📝 Main Content:\n")
                        importantSentences.forEach { sentence ->
                            append("• $sentence.\n")
                        }
                    }
                    sentences.isNotEmpty() -> {
                        append("📝 Content Preview:\n")
                        append("• ${sentences.first()}.\n")
                        if (sentences.size > 1) {
                            append("• ${sentences[1]}.\n")
                        }
                    }
                    else -> {
                        // Extract first meaningful paragraph
                        val preview = cleanBody.take(150)
                        append("📝 Content: $preview")
                        if (cleanBody.length > 150) append("...")
                        append("\n")
                    }
                }
                
                // Add metadata
                append("\n📋 Details:\n")
                append("• From: $sender\n")
                append("• Subject: $subject\n")
                append("• Content Length: ${cleanBody.length} characters\n")
                
            } else {
                append("📝 Content: Email appears to be empty or contains only formatting.\n")
                append("\n📋 Details:\n")
                append("• From: $sender\n")
                append("• Subject: $subject\n")
            }
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
    
    /**
     * Execute single image analysis action
     */
    private suspend fun executeImageAnalysisAction(
        action: MultiUserAction.AIImageAnalysisAction, 
        context: WorkflowExecutionContext
    ): Result<String> {
        return try {
            Log.i(TAG, "🖼️ === EXECUTING IMAGE ANALYSIS ACTION ===")
            Log.d(TAG, "Analysis type: ${action.analysisType}")
            Log.d(TAG, "Image source: ${action.imageSource}")
            Log.d(TAG, "Analysis questions: ${action.analysisQuestions}")
            
            // Get image analysis service
            val imageAnalysisService = ImageAnalysisService()
            
            // Load image from source
            val bitmap = loadImageFromSource(action.imageSource, context)
            if (bitmap == null) {
                Log.e(TAG, "❌ Failed to load image from source: ${action.imageSource}")
                return Result.failure(Exception("Failed to load image from source"))
            }
            
            Log.d(TAG, "✅ Image loaded successfully: ${bitmap.width}x${bitmap.height}")
            
            // Prepare analysis questions
            val combinedQuestions = action.analysisQuestions.joinToString("; ")
            
            // Perform image analysis
            val analysisResult = imageAnalysisService.analyzeImage(
                bitmap = bitmap,
                userQuestion = combinedQuestions
            )
            
            if (!analysisResult.success) {
                Log.e(TAG, "❌ Image analysis failed")
                return Result.failure(Exception("Image analysis failed: ${analysisResult.description}"))
            }
            
            Log.i(TAG, "✅ Image analysis completed successfully")
            Log.d(TAG, "   Confidence: ${analysisResult.confidence}")
            Log.d(TAG, "   OCR Text: ${analysisResult.ocrText.take(100)}...")
            Log.d(TAG, "   People Count: ${analysisResult.objectsDetected.peopleCount}")
            Log.d(TAG, "   Objects: ${analysisResult.objectsDetected.detectedObjects.take(5)}")
            
            // Store results in context variables
            context.variables[action.outputVariable] = analysisResult.description
            context.variables["${action.outputVariable}_ocr"] = analysisResult.ocrText
            context.variables["${action.outputVariable}_people_count"] = analysisResult.objectsDetected.peopleCount.toString()
            context.variables["${action.outputVariable}_objects"] = analysisResult.objectsDetected.detectedObjects.joinToString(", ")
            context.variables["${action.outputVariable}_colors"] = analysisResult.visualElements.dominantColors.joinToString(", ")
            context.variables["${action.outputVariable}_confidence"] = analysisResult.confidence.toString()
            
            // Save analysis to file if requested
            if (action.saveAnalysisToFile) {
                try {
                    val analysisFile = java.io.File(this.context.filesDir, "image_analysis_${context.executionId}.txt")
                    analysisFile.writeText(analysisResult.description)
                    context.variables["${action.outputVariable}_file_path"] = analysisFile.absolutePath
                    Log.d(TAG, "Analysis saved to file: ${analysisFile.absolutePath}")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to save analysis to file", e)
                }
            }
            
            Result.success("Image analysis completed with confidence ${analysisResult.confidence}")
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 ERROR in image analysis action", e)
            Result.failure(Exception("Image analysis action failed: ${e.message}"))
        }
    }
    
    /**
     * Execute batch image analysis action
     */
    private suspend fun executeBatchImageAnalysisAction(
        action: MultiUserAction.AIBatchImageAnalysisAction,
        context: WorkflowExecutionContext
    ): Result<String> {
        return try {
            Log.i(TAG, "📷 === EXECUTING BATCH IMAGE ANALYSIS ACTION ===")
            Log.d(TAG, "Analyzing ${action.imageSources.size} images")
            Log.d(TAG, "Analysis type: ${action.analysisType}")
            Log.d(TAG, "Parallel processing: ${action.parallelProcessing}")
            
            val imageAnalysisService = ImageAnalysisService()
            val results = mutableListOf<ImageAnalysisWorkflowResult>()
            
            // Process images in parallel or sequentially
            if (action.parallelProcessing && action.imageSources.size > 1) {
                Log.d(TAG, "🔄 Processing images in parallel")
                
                val deferredResults = action.imageSources.mapIndexed { index, imageSource ->
                    scope.async {
                        analyzeImageFromSource(imageSource, action, context, imageAnalysisService, index)
                    }
                }
                
                results.addAll(deferredResults.awaitAll().filterNotNull())
            } else {
                Log.d(TAG, "🔄 Processing images sequentially")
                
                action.imageSources.forEachIndexed { index, imageSource ->
                    val result = analyzeImageFromSource(imageSource, action, context, imageAnalysisService, index)
                    if (result != null) {
                        results.add(result)
                    }
                }
            }
            
            Log.i(TAG, "✅ Batch analysis completed: ${results.size}/${action.imageSources.size} successful")
            
            // Store individual results if requested
            if (action.saveIndividualAnalyses) {
                results.forEachIndexed { index, result ->
                    context.variables["${action.outputVariable}_${index}_description"] = result.description
                    context.variables["${action.outputVariable}_${index}_ocr"] = result.ocrText
                    context.variables["${action.outputVariable}_${index}_people"] = result.peopleCount.toString()
                    context.variables["${action.outputVariable}_${index}_objects"] = result.detectedObjects.joinToString(", ")
                }
            }
            
            // Combine results if requested
            if (action.combineResults) {
                val combinedDescription = buildString {
                    appendLine("📊 BATCH IMAGE ANALYSIS SUMMARY")
                    appendLine("Total images analyzed: ${results.size}")
                    appendLine("Successful analyses: ${results.count { it.success }}")
                    appendLine()
                    
                    results.forEachIndexed { index, result ->
                        appendLine("IMAGE ${index + 1}:")
                        appendLine("File: ${result.fileName}")
                        appendLine("Success: ${result.success}")
                        if (result.success) {
                            appendLine("People Count: ${result.peopleCount}")
                            appendLine("Objects: ${result.detectedObjects.joinToString(", ")}")
                            appendLine("OCR Text: ${result.ocrText.take(100)}${if (result.ocrText.length > 100) "..." else ""}")
                            appendLine("Description: ${result.description.take(200)}${if (result.description.length > 200) "..." else ""}")
                        } else {
                            appendLine("Error: ${result.error}")
                        }
                        appendLine()
                    }
                }
                
                context.variables[action.outputVariable] = combinedDescription
            } else {
                // Store as separate results
                context.variables[action.outputVariable] = results.joinToString("\n\n") { result ->
                    "File: ${result.fileName}\nSuccess: ${result.success}\nDescription: ${result.description}"
                }
            }
            
            // Summary statistics
            context.variables["${action.outputVariable}_total_count"] = results.size.toString()
            context.variables["${action.outputVariable}_success_count"] = results.count { it.success }.toString()
            context.variables["${action.outputVariable}_total_people"] = results.sumOf { it.peopleCount }.toString()
            
            Result.success("Batch analysis completed: ${results.count { it.success }}/${results.size} successful")
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 ERROR in batch image analysis action", e)
            Result.failure(Exception("Batch image analysis action failed: ${e.message}"))
        }
    }
    
    /**
     * Execute image comparison action
     */
    private suspend fun executeImageComparisonAction(
        action: MultiUserAction.AIImageComparisonAction,
        context: WorkflowExecutionContext
    ): Result<String> {
        return try {
            Log.i(TAG, "🔍 === EXECUTING IMAGE COMPARISON ACTION ===")
            Log.d(TAG, "Comparison type: ${action.comparisonType}")
            Log.d(TAG, "Comparing ${action.comparisonImageSources.size} images with primary image")
            
            val imageAnalysisService = ImageAnalysisService()
            
            // Load primary image
            val primaryBitmap = loadImageFromSource(action.primaryImageSource, context)
            if (primaryBitmap == null) {
                return Result.failure(Exception("Failed to load primary image"))
            }
            
            // Analyze primary image
            val primaryAnalysis = imageAnalysisService.analyzeImage(primaryBitmap, "Analyze this image comprehensively")
            if (!primaryAnalysis.success) {
                return Result.failure(Exception("Failed to analyze primary image"))
            }
            
            Log.d(TAG, "✅ Primary image analyzed successfully")
            
            // Analyze comparison images
            val comparisonResults = mutableListOf<Pair<String, com.localllm.myapplication.service.ImageAnalysisResult>>()
            
            for ((index, comparisonSource) in action.comparisonImageSources.withIndex()) {
                val comparisonBitmap = loadImageFromSource(comparisonSource, context)
                if (comparisonBitmap != null) {
                    val comparisonAnalysis = imageAnalysisService.analyzeImage(comparisonBitmap, "Analyze this image comprehensively")
                    if (comparisonAnalysis.success) {
                        comparisonResults.add("Image_${index + 1}" to comparisonAnalysis)
                        Log.d(TAG, "✅ Comparison image ${index + 1} analyzed successfully")
                    } else {
                        Log.w(TAG, "⚠️ Failed to analyze comparison image ${index + 1}")
                    }
                } else {
                    Log.w(TAG, "⚠️ Failed to load comparison image ${index + 1}")
                }
            }
            
            // Perform comparison based on type
            val comparisonReport = generateComparisonReport(
                primaryAnalysis,
                comparisonResults,
                action.comparisonType,
                action.includeDetailedDifferences
            )
            
            // Store results
            context.variables[action.outputVariable] = comparisonReport
            context.variables["${action.outputVariable}_comparisons_count"] = comparisonResults.size.toString()
            
            Log.i(TAG, "✅ Image comparison completed: ${comparisonResults.size} successful comparisons")
            
            Result.success("Image comparison completed with ${comparisonResults.size} successful comparisons")
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 ERROR in image comparison action", e)
            Result.failure(Exception("Image comparison action failed: ${e.message}"))
        }
    }
    
    /**
     * Analyze image from source (helper for batch processing)
     */
    private suspend fun analyzeImageFromSource(
        imageSource: ImageSource,
        action: MultiUserAction.AIBatchImageAnalysisAction,
        context: WorkflowExecutionContext,
        imageAnalysisService: ImageAnalysisService,
        index: Int
    ): ImageAnalysisWorkflowResult? {
        return try {
            val bitmap = loadImageFromSource(imageSource, context)
            if (bitmap == null) {
                Log.w(TAG, "❌ Failed to load image ${index + 1}")
                return ImageAnalysisWorkflowResult(
                    attachmentId = "batch_$index",
                    fileName = "Image_${index + 1}",
                    success = false,
                    analysisType = action.analysisType,
                    description = "Failed to load image",
                    error = "Image loading failed"
                )
            }
            
            val combinedQuestions = action.analysisQuestions.joinToString("; ")
            val analysisResult = imageAnalysisService.analyzeImage(bitmap, combinedQuestions)
            
            if (analysisResult.success) {
                Log.d(TAG, "✅ Image ${index + 1} analyzed successfully")
                ImageAnalysisWorkflowResult(
                    attachmentId = "batch_$index",
                    fileName = "Image_${index + 1}",
                    success = true,
                    analysisType = action.analysisType,
                    description = analysisResult.description,
                    ocrText = analysisResult.ocrText,
                    peopleCount = analysisResult.objectsDetected.peopleCount,
                    detectedObjects = analysisResult.objectsDetected.detectedObjects,
                    dominantColors = analysisResult.visualElements.dominantColors,
                    confidence = analysisResult.confidence,
                    visualElements = mapOf(
                        "brightness" to analysisResult.visualElements.brightness,
                        "contrast" to analysisResult.visualElements.contrast,
                        "composition" to analysisResult.visualElements.composition,
                        "clarity" to analysisResult.visualElements.clarity
                    )
                )
            } else {
                Log.w(TAG, "❌ Analysis failed for image ${index + 1}")
                ImageAnalysisWorkflowResult(
                    attachmentId = "batch_$index",
                    fileName = "Image_${index + 1}",
                    success = false,
                    analysisType = action.analysisType,
                    description = "Analysis failed",
                    error = "Image analysis failed"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 Exception analyzing image ${index + 1}", e)
            ImageAnalysisWorkflowResult(
                attachmentId = "batch_$index",
                fileName = "Image_${index + 1}",
                success = false,
                analysisType = action.analysisType,
                description = "Analysis failed with exception",
                error = e.message
            )
        }
    }
    
    /**
     * Load image from various sources
     */
    private fun loadImageFromSource(imageSource: ImageSource, context: WorkflowExecutionContext): android.graphics.Bitmap? {
        return try {
            when (imageSource) {
                is ImageSource.FilePathSource -> {
                    val file = java.io.File(imageSource.filePath)
                    if (file.exists()) {
                        android.graphics.BitmapFactory.decodeFile(imageSource.filePath)
                    } else {
                        Log.w(TAG, "Image file not found: ${imageSource.filePath}")
                        null
                    }
                }
                is ImageSource.UriSource -> {
                    val uri = android.net.Uri.parse(imageSource.uri)
                    val inputStream = this.context.contentResolver.openInputStream(uri)
                    android.graphics.BitmapFactory.decodeStream(inputStream)
                }
                is ImageSource.TriggerImageSource -> {
                    // Extract image from trigger data
                    val triggerData = context.triggerData
                    if (triggerData is Map<*, *>) {
                        val imagePath = triggerData["image_path_${imageSource.index}"]?.toString()
                            ?: triggerData["image_path"]?.toString()
                        if (imagePath != null) {
                            val file = java.io.File(imagePath)
                            if (file.exists()) {
                                android.graphics.BitmapFactory.decodeFile(imagePath)
                            } else {
                                null
                            }
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }
                is ImageSource.VariableSource -> {
                    val imagePath = context.variables[imageSource.variableName]
                    if (imagePath != null) {
                        val file = java.io.File(imagePath)
                        if (file.exists()) {
                            android.graphics.BitmapFactory.decodeFile(imagePath)
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }
                is ImageSource.AttachmentSource -> {
                    // This would require access to workflow attachments storage
                    Log.w(TAG, "Attachment source not yet implemented: ${imageSource.attachmentId}")
                    null
                }
                is ImageSource.EmailAttachmentSource -> {
                    // This would require Gmail integration for attachment access
                    Log.w(TAG, "Email attachment source not yet implemented: ${imageSource.emailId}")
                    null
                }
                is ImageSource.TelegramPhotoSource -> {
                    // This would require Telegram integration for photo access
                    Log.w(TAG, "Telegram photo source not yet implemented: ${imageSource.messageId}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading image from source: $imageSource", e)
            null
        }
    }
    
    /**
     * Generate comparison report between primary and comparison images
     */
    private fun generateComparisonReport(
        primaryAnalysis: com.localllm.myapplication.service.ImageAnalysisResult,
        comparisonResults: List<Pair<String, com.localllm.myapplication.service.ImageAnalysisResult>>,
        comparisonType: ImageComparisonType,
        includeDetailedDifferences: Boolean
    ): String {
        return buildString {
            appendLine("🔍 IMAGE COMPARISON REPORT")
            appendLine("Comparison Type: $comparisonType")
            appendLine("Primary Image Analysis:")
            appendLine("  People Count: ${primaryAnalysis.objectsDetected.peopleCount}")
            appendLine("  Objects: ${primaryAnalysis.objectsDetected.detectedObjects.joinToString(", ")}")
            appendLine("  Colors: ${primaryAnalysis.visualElements.dominantColors.joinToString(", ")}")
            appendLine("  OCR Text: ${primaryAnalysis.ocrText.take(100)}${if (primaryAnalysis.ocrText.length > 100) "..." else ""}")
            appendLine()
            
            appendLine("COMPARISON RESULTS:")
            comparisonResults.forEach { (name, analysis) ->
                appendLine("$name:")
                
                when (comparisonType) {
                    ImageComparisonType.VISUAL_SIMILARITY -> {
                        val colorSimilarity = calculateColorSimilarity(primaryAnalysis.visualElements.dominantColors, analysis.visualElements.dominantColors)
                        appendLine("  Color Similarity: ${String.format("%.1f", colorSimilarity * 100)}%")
                        
                        val brightnessDiff = kotlin.math.abs(primaryAnalysis.visualElements.brightness - analysis.visualElements.brightness)
                        appendLine("  Brightness Difference: ${String.format("%.2f", brightnessDiff)}")
                    }
                    
                    ImageComparisonType.OBJECT_DIFFERENCES -> {
                        val commonObjects = primaryAnalysis.objectsDetected.detectedObjects.intersect(analysis.objectsDetected.detectedObjects.toSet())
                        val uniqueToPrimary = primaryAnalysis.objectsDetected.detectedObjects.subtract(analysis.objectsDetected.detectedObjects.toSet())
                        val uniqueToComparison = analysis.objectsDetected.detectedObjects.subtract(primaryAnalysis.objectsDetected.detectedObjects.toSet())
                        
                        appendLine("  Common Objects: ${commonObjects.joinToString(", ")}")
                        appendLine("  Unique to Primary: ${uniqueToPrimary.joinToString(", ")}")
                        appendLine("  Unique to Comparison: ${uniqueToComparison.joinToString(", ")}")
                    }
                    
                    ImageComparisonType.TEXT_DIFFERENCES -> {
                        val primaryWords = primaryAnalysis.ocrText.split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()
                        val comparisonWords = analysis.ocrText.split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()
                        
                        val commonWords = primaryWords.intersect(comparisonWords)
                        val textSimilarity = if (primaryWords.isNotEmpty() || comparisonWords.isNotEmpty()) {
                            commonWords.size.toFloat() / maxOf(primaryWords.size, comparisonWords.size)
                        } else {
                            1.0f
                        }
                        
                        appendLine("  Text Similarity: ${String.format("%.1f", textSimilarity * 100)}%")
                        appendLine("  Common Words: ${commonWords.take(10).joinToString(", ")}")
                    }
                    
                    ImageComparisonType.COLOR_DIFFERENCES -> {
                        val colorSimilarity = calculateColorSimilarity(primaryAnalysis.visualElements.dominantColors, analysis.visualElements.dominantColors)
                        appendLine("  Color Similarity: ${String.format("%.1f", colorSimilarity * 100)}%")
                        appendLine("  Primary Colors: ${primaryAnalysis.visualElements.dominantColors.joinToString(", ")}")
                        appendLine("  Comparison Colors: ${analysis.visualElements.dominantColors.joinToString(", ")}")
                    }
                    
                    ImageComparisonType.STRUCTURAL_DIFFERENCES -> {
                        appendLine("  Primary Composition: ${primaryAnalysis.visualElements.composition}")
                        appendLine("  Comparison Composition: ${analysis.visualElements.composition}")
                        appendLine("  Primary Clarity: ${primaryAnalysis.visualElements.clarity}")
                        appendLine("  Comparison Clarity: ${analysis.visualElements.clarity}")
                    }
                    
                    ImageComparisonType.COMPREHENSIVE -> {
                        // Include all comparison types
                        appendLine("  People Count Difference: ${kotlin.math.abs(primaryAnalysis.objectsDetected.peopleCount - analysis.objectsDetected.peopleCount)}")
                        
                        val colorSimilarity = calculateColorSimilarity(primaryAnalysis.visualElements.dominantColors, analysis.visualElements.dominantColors)
                        appendLine("  Color Similarity: ${String.format("%.1f", colorSimilarity * 100)}%")
                        
                        val commonObjects = primaryAnalysis.objectsDetected.detectedObjects.intersect(analysis.objectsDetected.detectedObjects.toSet())
                        appendLine("  Common Objects: ${commonObjects.size}/${maxOf(primaryAnalysis.objectsDetected.detectedObjects.size, analysis.objectsDetected.detectedObjects.size)}")
                        
                        if (includeDetailedDifferences) {
                            appendLine("  Detailed Analysis:")
                            appendLine("    Primary: ${primaryAnalysis.description.take(150)}...")
                            appendLine("    Comparison: ${analysis.description.take(150)}...")
                        }
                    }
                }
                appendLine()
            }
        }
    }
    
    /**
     * Calculate color similarity between two color lists
     */
    private fun calculateColorSimilarity(colors1: List<String>, colors2: List<String>): Float {
        if (colors1.isEmpty() && colors2.isEmpty()) return 1.0f
        if (colors1.isEmpty() || colors2.isEmpty()) return 0.0f
        
        val commonColors = colors1.intersect(colors2.toSet())
        return commonColors.size.toFloat() / maxOf(colors1.size, colors2.size)
    }
    
    /**
     * Execute 24-Hour Gallery Analysis Action
     */
    private suspend fun execute24HourGalleryAnalysis(
        action: MultiUserAction.AI24HourGalleryAnalysis,
        context: WorkflowExecutionContext
    ): Result<String> {
        return try {
            Log.i(TAG, "📸 === EXECUTING 24-HOUR GALLERY ANALYSIS ===")
            Log.d(TAG, "Search keyword: '${action.searchKeyword}'")
            Log.d(TAG, "Delivery method: ${action.deliveryMethod}")
            Log.d(TAG, "Max images: ${action.maxImages}")
            
            // Create services
            val gmailService = GmailIntegrationService(this.context)
            val telegramService = TelegramBotService(this.context)
            val imageAnalysisService = ImageAnalysisService()
            
            // Create gallery analysis service  
            val aiProcessingFacade = AIProcessingFacade(this.context)
            val galleryAnalysisService = GalleryAnalysisService(
                context = this.context,
                aiProcessingFacade = aiProcessingFacade,
                gmailService = gmailService,
                telegramService = telegramService,
                imageAnalysisService = imageAnalysisService
            )
            
            // Execute the analysis
            val result = galleryAnalysisService.execute24HourGalleryAnalysis(action)
            
            // Store results in context
            context.variables[action.outputVariable] = result.outputData[action.outputVariable] ?: ""
            result.outputData.forEach { (key, value) ->
                context.variables[key] = value
            }
            
            if (result.success) {
                Log.i(TAG, "✅ 24-hour gallery analysis completed successfully")
                Result.success(result.message)
            } else {
                Log.e(TAG, "❌ 24-hour gallery analysis failed: ${result.message}")
                Result.failure(Exception(result.message))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error in 24-hour gallery analysis execution", e)
            Result.failure(e)
        }
    }
    
    /**
     * Execute Image Workflow Orchestrator action
     */
    private suspend fun executeImageWorkflowOrchestrator(
        action: MultiUserAction.AIImageWorkflowOrchestrator,
        context: WorkflowExecutionContext
    ): Result<String> {
        return try {
            Log.i(TAG, "🎬 === EXECUTING IMAGE WORKFLOW ORCHESTRATOR ===")
            Log.d(TAG, "Instruction: '${action.instruction}'")
            Log.d(TAG, "Attached image URI: ${action.attachedImageUri}")
            Log.d(TAG, "Output format: ${action.outputFormat}")
            Log.d(TAG, "Delivery method: ${action.deliveryMethod}")
            
            // Create the orchestrator
            val orchestrator = ImageWorkflowOrchestrator(this.context)
            
            // Process variables in instruction
            val processedInstruction = replaceVariables(action.instruction, context.variables)
            Log.d(TAG, "Processed instruction: '$processedInstruction'")
            
            // Parse attached image URI if provided
            val referenceImageUri = action.attachedImageUri?.let { uri ->
                if (uri.startsWith("{{") && uri.endsWith("}}")) {
                    val variableName = uri.substring(2, uri.length - 2)
                    context.variables[variableName]?.let { android.net.Uri.parse(it) }
                } else {
                    android.net.Uri.parse(uri)
                }
            }
            
            // Execute the workflow instruction
            val workflowResult = orchestrator.processWorkflowInstruction(
                instruction = processedInstruction,
                referenceImageUri = referenceImageUri
            )
            
            workflowResult.fold(
                onSuccess = { result ->
                    Log.i(TAG, "✅ Image workflow orchestrator completed successfully")
                    Log.d(TAG, "Images checked: ${result.imagesChecked}")
                    Log.d(TAG, "Images matched: ${result.imagesMatched}")
                    Log.d(TAG, "Action: ${result.action}")
                    
                    // Store result in context variables using default variable name
                    val defaultVarName = "workflow_result"
                    when (action.outputFormat.lowercase()) {
                        "json" -> {
                            context.variables[defaultVarName] = orchestrator.formatResultAsJson(result)
                        }
                        "text" -> {
                            context.variables[defaultVarName] = orchestrator.formatResultAsHumanReadable(result)
                        }
                        "summary" -> {
                            context.variables[defaultVarName] = orchestrator.formatResultAsHumanReadable(result)
                        }
                        else -> {
                            context.variables[defaultVarName] = orchestrator.formatResultAsJson(result)
                        }
                    }
                    
                    // Store individual result components as variables
                    context.variables["${defaultVarName}_images_checked"] = result.imagesChecked.toString()
                    context.variables["${defaultVarName}_images_matched"] = result.imagesMatched.toString()
                    context.variables["${defaultVarName}_action"] = result.action
                    context.variables["${defaultVarName}_time_start"] = result.timeRange.start
                    context.variables["${defaultVarName}_time_end"] = result.timeRange.end
                    
                    // Handle delivery if specified
                    if (!action.deliveryMethod.isNullOrBlank()) {
                        handleWorkflowResultDelivery(action, result, orchestrator, context)
                    }
                    
                    Result.success("Workflow orchestrator processed '${result.action}' action: Found ${result.imagesMatched} matching images out of ${result.imagesChecked} checked")
                },
                onFailure = { error ->
                    Log.e(TAG, "❌ Image workflow orchestrator failed: ${error.message}")
                    context.variables["workflow_result_error"] = error.message ?: "Unknown error"
                    Result.failure(error)
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error in image workflow orchestrator execution", e)
            Result.failure(e)
        }
    }
    
    /**
     * Handle delivery of workflow results
     */
    private suspend fun handleWorkflowResultDelivery(
        action: MultiUserAction.AIImageWorkflowOrchestrator,
        result: WorkflowResult,
        orchestrator: ImageWorkflowOrchestrator,
        context: WorkflowExecutionContext
    ) {
        try {
            val resultContent = when (action.outputFormat.lowercase()) {
                "json" -> orchestrator.formatResultAsJson(result)
                "text" -> orchestrator.formatResultAsHumanReadable(result)
                "summary" -> orchestrator.formatResultAsHumanReadable(result)
                else -> orchestrator.formatResultAsHumanReadable(result)
            }
            
            when (action.deliveryMethod?.lowercase()) {
                "email" -> {
                    if (action.recipientEmails.isNotBlank()) {
                        val gmailService = userManager.getGmailService(context.triggerUserId)
                        gmailService?.sendEmail(
                            to = action.recipientEmails,
                            subject = "Image Workflow Result: ${result.action}",
                            body = resultContent,
                            isHtml = false
                        )?.fold(
                            onSuccess = { Log.d(TAG, "Workflow result sent via email") },
                            onFailure = { Log.w(TAG, "Failed to send workflow result via email: ${it.message}") }
                        )
                    }
                }
                "telegram" -> {
                    if (action.telegramChatId.isNotBlank()) {
                        val telegramService = userManager.getTelegramService(context.triggerUserId)
                        telegramService?.sendMessage(
                            chatId = action.telegramChatId.toLongOrNull() ?: 0L,
                            text = resultContent
                        )?.fold(
                            onSuccess = { Log.d(TAG, "Workflow result sent via Telegram") },
                            onFailure = { Log.w(TAG, "Failed to send workflow result via Telegram: ${it.message}") }
                        )
                    }
                }
                "notification" -> {
                    val notificationService = NotificationService(this.context)
                    notificationService.showWorkflowNotification(
                        title = action.notificationTitle.ifBlank { "Image Workflow Result" },
                        message = resultContent.take(200) + if (resultContent.length > 200) "..." else ""
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to deliver workflow result: ${e.message}")
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