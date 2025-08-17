package com.localllm.myapplication.service

import android.content.Context
import android.util.Log
import androidx.work.*
import com.localllm.myapplication.data.*
import com.localllm.myapplication.service.integration.GmailIntegrationService
import com.localllm.myapplication.service.integration.TelegramBotService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.TimeUnit

/**
 * Manages workflow triggers and automated execution
 */
class WorkflowTriggerManager(
    private val context: Context,
    private val workflowEngine: MultiUserWorkflowEngine,
    private val workflowRepository: WorkflowRepository,
    private val userManager: UserManager
) {
    
    companion object {
        private const val TAG = "WorkflowTriggerManager"
        private const val TRIGGER_CHECK_WORK_TAG = "workflow_trigger_check"
        private const val TRIGGER_CHECK_INTERVAL = 15L // minutes
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _activeTriggers = MutableStateFlow<List<ActiveTrigger>>(emptyList())
    val activeTriggers: StateFlow<List<ActiveTrigger>> = _activeTriggers.asStateFlow()
    
    private val workManager = WorkManager.getInstance(context)
    private val emailDeduplicationService = EmailDeduplicationService(context)
    private val telegramDeduplicationService = TelegramDeduplicationService(context)
    
    // Rate limiting for email checks
    private val lastEmailCheckTimes = mutableMapOf<String, Long>()
    private val minEmailCheckInterval = 60000L // 1 minute between email checks per workflow
    
    data class ActiveTrigger(
        val workflowId: String,
        val triggerId: String,
        val triggerType: String,
        val isActive: Boolean,
        val lastTriggered: Long? = null,
        val triggerCount: Int = 0
    )
    
    /**
     * Start the trigger manager and begin monitoring
     */
    fun start() {
        Log.i(TAG, "Starting workflow trigger manager")
        
        // Start periodic trigger checking
        startPeriodicTriggerCheck()
        
        // Monitor for workflow changes
        scope.launch {
            // This would ideally be a Flow from the repository
            // For now, we'll check periodically
            while (isActive) {
                Log.d(TAG, "=== TRIGGER CYCLE START ===")
                Log.d(TAG, "Checking triggers every 30 seconds...")
                
                refreshActiveTriggers()
                
                // Actually check triggers for new emails/messages
                val result = checkTriggers()
                result.fold(
                    onSuccess = { results ->
                        val triggeredCount = results.count { it.triggered }
                        Log.i(TAG, "=== TRIGGER CYCLE RESULTS ===")
                        Log.i(TAG, "Total workflows checked: ${results.size}")
                        Log.i(TAG, "Workflows triggered: $triggeredCount")
                        
                        if (triggeredCount > 0) {
                            Log.i(TAG, "Triggered workflows:")
                            results.filter { it.triggered }.forEach { result ->
                                Log.i(TAG, "  - ${result.workflowId} (${result.triggerType}): ${result.message}")
                            }
                        }
                        
                        // Log failed checks
                        val failedCount = results.count { !it.triggered && it.message?.contains("Error") == true }
                        if (failedCount > 0) {
                            Log.w(TAG, "Failed trigger checks: $failedCount")
                            results.filter { !it.triggered && it.message?.contains("Error") == true }.forEach { result ->
                                Log.w(TAG, "  - ${result.workflowId}: ${result.message}")
                            }
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "=== TRIGGER CYCLE ERROR ===")
                        Log.e(TAG, "Error checking triggers: ${error.message}", error)
                    }
                )
                
                // Cleanup old processed emails and messages periodically (every hour)
                if (System.currentTimeMillis() % (60 * 60 * 1000) < 30000) {
                    Log.d(TAG, "Running periodic cleanup of old processed emails and messages")
                    emailDeduplicationService.cleanupOldRecords()
                    telegramDeduplicationService.cleanupOldRecords()
                }
                
                Log.d(TAG, "=== TRIGGER CYCLE END - Next check in 30 seconds ===")
                delay(30000) // Check every 30 seconds
            }
        }
        
        Log.i(TAG, "Workflow trigger manager started")
    }
    
    /**
     * Stop the trigger manager
     */
    fun stop() {
        Log.i(TAG, "Stopping workflow trigger manager")
        
        scope.cancel()
        workManager.cancelAllWorkByTag(TRIGGER_CHECK_WORK_TAG)
        
        Log.i(TAG, "Workflow trigger manager stopped")
    }
    
    /**
     * Start periodic work to check triggers
     */
    private fun startPeriodicTriggerCheck() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        
        val triggerCheckRequest = PeriodicWorkRequestBuilder<TriggerCheckWorker>(
            TRIGGER_CHECK_INTERVAL, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .addTag(TRIGGER_CHECK_WORK_TAG)
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            "workflow_trigger_check",
            ExistingPeriodicWorkPolicy.KEEP,
            triggerCheckRequest
        )
        
        Log.d(TAG, "Periodic trigger check work scheduled")
    }
    
    /**
     * Refresh the list of active triggers
     */
    private suspend fun refreshActiveTriggers() {
        try {
            val workflows = workflowRepository.getAllWorkflows().getOrNull() ?: emptyList()
            val triggers = mutableListOf<ActiveTrigger>()
            
            workflows.forEach { workflow ->
                if (workflow is MultiUserWorkflow && workflow.isEnabled) {
                    workflow.triggers.forEachIndexed { index, trigger ->
                        triggers.add(
                            ActiveTrigger(
                                workflowId = workflow.id,
                                triggerId = "${workflow.id}_$index",
                                triggerType = trigger::class.simpleName ?: "Unknown",
                                isActive = true
                            )
                        )
                    }
                }
            }
            
            _activeTriggers.value = triggers
            Log.d(TAG, "Refreshed active triggers: ${triggers.size} found")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing active triggers", e)
        }
    }
    
    /**
     * Check and process all triggers
     */
    suspend fun checkTriggers(): Result<List<TriggerExecutionResult>> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Checking all workflow triggers")
                val results = mutableListOf<TriggerExecutionResult>()
                
                val workflows = workflowRepository.getAllWorkflows().getOrNull() ?: emptyList()
                
                workflows.forEach { workflow ->
                    if (workflow is MultiUserWorkflow && workflow.isEnabled) {
                        workflow.triggers.forEach { trigger ->
                            val result = checkTrigger(workflow, trigger)
                            results.add(result)
                        }
                    }
                }
                
                Log.i(TAG, "Trigger check completed: ${results.size} triggers checked, ${results.count { it.triggered }} triggered")
                Result.success(results)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during trigger check", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Check a specific trigger
     */
    private suspend fun checkTrigger(workflow: MultiUserWorkflow, trigger: MultiUserTrigger): TriggerExecutionResult {
        return try {
            Log.d(TAG, "Checking trigger for workflow: ${workflow.name}")
            
            when (trigger) {
                is MultiUserTrigger.ScheduledTrigger -> checkScheduledTrigger(workflow, trigger)
                is MultiUserTrigger.UserGmailNewEmail -> checkGmailTrigger(workflow, trigger)
                is MultiUserTrigger.UserGmailEmailReceived -> checkGmailEmailReceivedTrigger(workflow, trigger)
                is MultiUserTrigger.UserTelegramMessage -> checkTelegramTrigger(workflow, trigger)
                is MultiUserTrigger.ManualTrigger -> {
                    // Manual triggers are not checked automatically
                    TriggerExecutionResult(workflow.id, trigger::class.simpleName ?: "Manual", false)
                }
                // Geofencing triggers are handled by GeofencingService, not checked periodically
                is MultiUserTrigger.GeofenceEnterTrigger,
                is MultiUserTrigger.GeofenceExitTrigger,
                is MultiUserTrigger.GeofenceDwellTrigger -> {
                    TriggerExecutionResult(workflow.id, trigger::class.simpleName ?: "Geofence", false, "Geofence triggers are event-based")
                }
                else -> TriggerExecutionResult(workflow.id, trigger::class.simpleName ?: "Unknown", false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking trigger for workflow ${workflow.id}", e)
            TriggerExecutionResult(workflow.id, trigger::class.simpleName ?: "Error", false, e.message)
        }
    }
    
    /**
     * Check scheduled trigger
     */
    private suspend fun checkScheduledTrigger(
        workflow: MultiUserWorkflow, 
        trigger: MultiUserTrigger.ScheduledTrigger
    ): TriggerExecutionResult {
        // This is a simplified implementation
        // In a real implementation, you'd use a proper cron parser
        
        val currentTime = System.currentTimeMillis()
        val lastExecution = getLastExecutionTime(workflow.id) ?: 0
        val timeSinceLastExecution = currentTime - lastExecution
        
        // Simple check: execute if more than 1 hour has passed
        val shouldExecute = timeSinceLastExecution > 3600000 // 1 hour in ms
        
        return if (shouldExecute) {
            val triggerUserId = trigger.triggerUserId ?: workflow.createdBy
            executeWorkflow(workflow, triggerUserId, "Scheduled trigger")
        } else {
            TriggerExecutionResult(workflow.id, "ScheduledTrigger", false, "Not yet time to execute")
        }
    }
    
    /**
     * Check Gmail trigger with proper deduplication and rate limiting
     */
    private suspend fun checkGmailTrigger(
        workflow: MultiUserWorkflow, 
        trigger: MultiUserTrigger.UserGmailNewEmail
    ): TriggerExecutionResult {
        return try {
            Log.d(TAG, "=== GMAIL TRIGGER CHECK START ===")
            Log.d(TAG, "Workflow: ${workflow.name} (ID: ${workflow.id})")
            Log.d(TAG, "User: ${trigger.userId}")
            Log.d(TAG, "Trigger condition: isUnreadOnly=${trigger.condition.isUnreadOnly}, fromFilter=${trigger.condition.fromFilter}")
            
            // Check rate limiting
            val workflowKey = "${workflow.id}_${trigger.userId}"
            val lastCheck = lastEmailCheckTimes[workflowKey] ?: 0
            val currentTime = System.currentTimeMillis()
            val timeSinceLastCheck = currentTime - lastCheck
            
            if (timeSinceLastCheck < minEmailCheckInterval) {
                val remainingTime = (minEmailCheckInterval - timeSinceLastCheck) / 1000
                Log.d(TAG, "Rate limiting: Last check was ${timeSinceLastCheck}ms ago, waiting ${remainingTime}s more")
                return TriggerExecutionResult(
                    workflow.id, 
                    "UserGmailNewEmail", 
                    false, 
                    "Rate limited: checked ${remainingTime}s ago"
                )
            }
            
            // Update last check time
            lastEmailCheckTimes[workflowKey] = currentTime
            
            val gmailService = userManager.getGmailService(trigger.userId)
            if (gmailService == null) {
                Log.w(TAG, "Gmail service not available for user: ${trigger.userId}")
                return TriggerExecutionResult(
                    workflow.id, 
                    "UserGmailNewEmail", 
                    false, 
                    "Gmail service not available for user"
                )
            }
            
            Log.d(TAG, "Gmail service available, checking for new emails...")
            
            // Check for new emails based on condition with enhanced filtering
            val enhancedCondition = trigger.condition.copy(
                maxAgeHours = 2, // Only look at emails from last 2 hours
                newerThan = lastCheck // Only get emails newer than last check
            )
            
            val emailsResult = gmailService.checkForNewEmails(enhancedCondition, 5) // Limit to 5 emails
            emailsResult.fold(
                onSuccess = { emails ->
                    Log.d(TAG, "Gmail API returned ${emails.size} emails")
                    
                    // Use deduplication service to filter new emails
                    val newEmails = emailDeduplicationService.filterNewEmails(emails, workflow.id)
                    
                    Log.i(TAG, "Found ${newEmails.size} NEW unprocessed emails (${emails.size - newEmails.size} already processed or too recent)")
                    
                    if (newEmails.isNotEmpty()) {
                        val latestEmail = newEmails.first()
                        Log.i(TAG, "PROCESSING NEW EMAIL:")
                        Log.i(TAG, "  ID: ${latestEmail.id}")
                        Log.i(TAG, "  From: ${latestEmail.from}")
                        Log.i(TAG, "  Subject: ${latestEmail.subject}")
                        Log.i(TAG, "  Timestamp: ${latestEmail.timestamp}")
                        Log.i(TAG, "  Is Read: ${latestEmail.isRead}")
                        
                        // Mark this email as processed BEFORE executing workflow
                        val marked = emailDeduplicationService.markEmailAsProcessed(
                            emailId = latestEmail.id,
                            workflowId = workflow.id,
                            userId = trigger.userId,
                            emailFrom = latestEmail.from,
                            emailSubject = latestEmail.subject,
                            emailTimestamp = latestEmail.timestamp
                        )
                        
                        if (!marked) {
                            Log.e(TAG, "Failed to mark email as processed, skipping workflow execution")
                            return TriggerExecutionResult(workflow.id, "UserGmailNewEmail", false, "Failed to mark email as processed")
                        }
                        
                        Log.d(TAG, "Email ${latestEmail.id} marked as processed")
                        
                        // Create trigger data with email details
                        val triggerData = mapOf(
                            "source" to "gmail",
                            "email_from" to latestEmail.from,
                            "email_subject" to latestEmail.subject,
                            "email_body" to latestEmail.body,
                            "trigger_email_id" to latestEmail.id,
                            "type" to "new_email"
                        )
                        
                        Log.i(TAG, "EXECUTING WORKFLOW for email ${latestEmail.id}")
                        Log.d(TAG, "=== TRIGGER DATA DEBUG ===")
                        Log.d(TAG, "email_subject: '${triggerData["email_subject"]}'")
                        Log.d(TAG, "email_body length: ${(triggerData["email_body"] as? String)?.length ?: 0} characters")
                        Log.d(TAG, "email_body preview: '${(triggerData["email_body"] as? String)?.take(200) ?: "null"}${if ((triggerData["email_body"] as? String)?.length ?: 0 > 200) "..." else ""}'")
                        Log.d(TAG, "email_from: '${triggerData["email_from"]}'")
                        Log.d(TAG, "=== END TRIGGER DATA DEBUG ===")
                        
                        executeWorkflowWithData(workflow, trigger.userId, triggerData)
                    } else {
                        Log.d(TAG, "No new unprocessed emails found for workflow: ${workflow.name}")
                        TriggerExecutionResult(workflow.id, "UserGmailNewEmail", false, "No new emails (all already processed or too recent)")
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "FAILED to check emails: ${error.message}")
                    TriggerExecutionResult(workflow.id, "UserGmailNewEmail", false, "Failed to check emails: ${error.message}")
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "ERROR in Gmail trigger check", e)
            TriggerExecutionResult(workflow.id, "UserGmailNewEmail", false, "Error: ${e.message}")
        } finally {
            Log.d(TAG, "=== GMAIL TRIGGER CHECK END ===")
        }
    }
    
    /**
     * Check Gmail Email Received trigger (with sender/subject/body filters)
     */
    private suspend fun checkGmailEmailReceivedTrigger(
        workflow: MultiUserWorkflow, 
        trigger: MultiUserTrigger.UserGmailEmailReceived
    ): TriggerExecutionResult {
        return try {
            Log.i(TAG, "📧 === GMAIL EMAIL RECEIVED TRIGGER CHECK START ===")
            Log.i(TAG, "📋 Workflow: ${workflow.name} (ID: ${workflow.id})")
            Log.i(TAG, "👤 User: ${trigger.userId}")
            Log.i(TAG, "🔍 Email Filters:")
            Log.i(TAG, "  👤 From Filter: '${trigger.fromFilter ?: "None"}'")
            Log.i(TAG, "  📝 Subject Filter: '${trigger.subjectFilter ?: "None"}'")
            Log.i(TAG, "  💬 Body Filter: '${trigger.bodyFilter ?: "None"}'")
            
            // Validate filters
            val hasFilters = !trigger.fromFilter.isNullOrBlank() || 
                           !trigger.subjectFilter.isNullOrBlank() || 
                           !trigger.bodyFilter.isNullOrBlank()
            
            if (!hasFilters) {
                Log.w(TAG, "⚠️ No filters specified - this will match ALL emails!")
            }
            
            // Check rate limiting
            val workflowKey = "${workflow.id}_${trigger.userId}"
            val lastCheck = lastEmailCheckTimes[workflowKey] ?: 0
            val currentTime = System.currentTimeMillis()
            val timeSinceLastCheck = currentTime - lastCheck
            
            if (timeSinceLastCheck < minEmailCheckInterval) {
                val remainingTime = (minEmailCheckInterval - timeSinceLastCheck) / 1000
                Log.d(TAG, "Rate limiting: Last check was ${timeSinceLastCheck}ms ago, waiting ${remainingTime}s more")
                return TriggerExecutionResult(
                    workflow.id, 
                    "UserGmailEmailReceived", 
                    false, 
                    "Rate limited: checked ${remainingTime}s ago"
                )
            }
            
            // Update last check time
            lastEmailCheckTimes[workflowKey] = currentTime
            
            val gmailService = userManager.getGmailService(trigger.userId)
            if (gmailService == null) {
                Log.w(TAG, "Gmail service not available for user: ${trigger.userId}")
                return TriggerExecutionResult(
                    workflow.id, 
                    "UserGmailEmailReceived", 
                    false, 
                    "Gmail service not available for user"
                )
            }
            
            Log.d(TAG, "Gmail service available, checking for new emails...")
            
            // Create email condition based on filters
            Log.d(TAG, "🔧 Creating email condition...")
            val condition = GmailIntegrationService.EmailCondition(
                isUnreadOnly = true,
                fromFilter = trigger.fromFilter?.takeIf { it.isNotBlank() },
                subjectFilter = trigger.subjectFilter?.takeIf { it.isNotBlank() },
                bodyFilter = trigger.bodyFilter?.takeIf { it.isNotBlank() },
                maxAgeHours = 2, // Only look at emails from last 2 hours
                newerThan = lastCheck // Only get emails newer than last check
            )
            
            Log.i(TAG, "✅ Email condition created:")
            Log.d(TAG, "  📖 Unread Only: ${condition.isUnreadOnly}")
            Log.d(TAG, "  👤 From Filter: '${condition.fromFilter ?: "None"}'")
            Log.d(TAG, "  📝 Subject Filter: '${condition.subjectFilter ?: "None"}'")
            Log.d(TAG, "  💬 Body Filter: '${condition.bodyFilter ?: "None"}'")
            Log.d(TAG, "  ⏰ Max Age: ${condition.maxAgeHours} hours")
            Log.d(TAG, "  📅 Newer Than: ${if (condition.newerThan != null) java.util.Date(condition.newerThan!!) else "None"}")
            
            val emailsResult = gmailService.checkForNewEmails(condition, 5) // Limit to 5 emails
            emailsResult.fold(
                onSuccess = { emails ->
                    Log.d(TAG, "Gmail API returned ${emails.size} emails")
                    
                    // Use deduplication service to filter new emails
                    val newEmails = emailDeduplicationService.filterNewEmails(emails, workflow.id)
                    
                    Log.i(TAG, "Found ${newEmails.size} NEW unprocessed emails (${emails.size - newEmails.size} already processed or too recent)")
                    
                    if (newEmails.isNotEmpty()) {
                        val latestEmail = newEmails.first()
                        Log.i(TAG, "PROCESSING NEW EMAIL:")
                        Log.i(TAG, "  ID: ${latestEmail.id}")
                        Log.i(TAG, "  From: ${latestEmail.from}")
                        Log.i(TAG, "  Subject: ${latestEmail.subject}")
                        Log.i(TAG, "  Timestamp: ${latestEmail.timestamp}")
                        Log.i(TAG, "  Is Read: ${latestEmail.isRead}")
                        
                        // Mark this email as processed BEFORE executing workflow
                        val marked = emailDeduplicationService.markEmailAsProcessed(
                            emailId = latestEmail.id,
                            workflowId = workflow.id,
                            userId = trigger.userId,
                            emailFrom = latestEmail.from,
                            emailSubject = latestEmail.subject,
                            emailTimestamp = latestEmail.timestamp
                        )
                        
                        if (!marked) {
                            Log.e(TAG, "Failed to mark email as processed, skipping workflow execution")
                            return TriggerExecutionResult(workflow.id, "UserGmailEmailReceived", false, "Failed to mark email as processed")
                        }
                        
                        Log.d(TAG, "Email ${latestEmail.id} marked as processed")
                        
                        // Create trigger data with email details
                        val triggerData = mapOf(
                            "source" to "gmail",
                            "email_from" to latestEmail.from,
                            "email_subject" to latestEmail.subject,
                            "email_body" to latestEmail.body,
                            "trigger_email_id" to latestEmail.id,
                            "type" to "email_received"
                        )
                        
                        Log.i(TAG, "EXECUTING WORKFLOW for email ${latestEmail.id}")
                        Log.d(TAG, "=== TRIGGER DATA DEBUG (EMAIL_RECEIVED) ===")
                        Log.d(TAG, "email_subject: '${triggerData["email_subject"]}'")
                        Log.d(TAG, "email_body length: ${(triggerData["email_body"] as? String)?.length ?: 0} characters")
                        Log.d(TAG, "email_body preview: '${(triggerData["email_body"] as? String)?.take(200) ?: "null"}${if ((triggerData["email_body"] as? String)?.length ?: 0 > 200) "..." else ""}'")
                        Log.d(TAG, "email_from: '${triggerData["email_from"]}'")
                        Log.d(TAG, "=== END TRIGGER DATA DEBUG ===")
                        
                        executeWorkflowWithData(workflow, trigger.userId, triggerData)
                    } else {
                        Log.d(TAG, "No new unprocessed emails found for workflow: ${workflow.name}")
                        TriggerExecutionResult(workflow.id, "UserGmailEmailReceived", false, "No new emails (all already processed or too recent)")
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "FAILED to check emails: ${error.message}")
                    TriggerExecutionResult(workflow.id, "UserGmailEmailReceived", false, "Failed to check emails: ${error.message}")
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "ERROR in Gmail Email Received trigger check", e)
            TriggerExecutionResult(workflow.id, "UserGmailEmailReceived", false, "Error: ${e.message}")
        } finally {
            Log.d(TAG, "=== GMAIL EMAIL RECEIVED TRIGGER CHECK END ===")
        }
    }
    
    /**
     * Check Telegram trigger
     */
    private suspend fun checkTelegramTrigger(
        workflow: MultiUserWorkflow, 
        trigger: MultiUserTrigger.UserTelegramMessage
    ): TriggerExecutionResult {
        return try {
            Log.i(TAG, "🚀 === TELEGRAM TRIGGER CHECK START ===")
            Log.i(TAG, "📋 Workflow: ${workflow.name} (ID: ${workflow.id})")
            Log.i(TAG, "👤 User: ${trigger.userId}")
            Log.i(TAG, "🔍 Trigger condition: ${trigger.condition}")
            
            Log.d(TAG, "🔎 STEP T1: Getting Telegram service for user ${trigger.userId}")
            val telegramService = userManager.getTelegramService(trigger.userId)
            if (telegramService == null) {
                Log.e(TAG, "❌ STEP T1: Telegram service not available for user: ${trigger.userId}")
                Log.e(TAG, "💡 Solution: User needs to connect Telegram account")
                return TriggerExecutionResult(
                    workflow.id, 
                    "UserTelegramMessage", 
                    false, 
                    "Telegram service not available for user"
                )
            }
            
            Log.i(TAG, "✅ STEP T1: Telegram service available")
            Log.d(TAG, "🤖 Bot status: ${if (telegramService.isInitialized()) "Connected" else "Not Connected"}")
            Log.d(TAG, "👤 Bot username: ${telegramService.getBotUsername()}")
            
            Log.d(TAG, "📞 STEP T2: Checking for new messages...")
            
            // Check for new messages based on condition
            val messagesResult = telegramService.checkForNewMessages(trigger.condition, 5)
            messagesResult.fold(
                onSuccess = { messages ->
                    Log.i(TAG, "✅ STEP T2: Telegram API returned ${messages.size} messages")
                    
                    // Use deduplication service to filter new messages
                    Log.d(TAG, "🔄 STEP T3: Filtering messages for duplicates...")
                    val newMessages = telegramDeduplicationService.filterNewTelegramMessages(messages, workflow.id)
                    
                    Log.i(TAG, "🎯 STEP T3: Found ${newMessages.size} NEW unprocessed messages (${messages.size - newMessages.size} already processed)")
                    
                    if (newMessages.isNotEmpty()) {
                        val latestMessage = newMessages.first()
                        Log.i(TAG, "🎉 STEP T4: PROCESSING NEW TELEGRAM MESSAGE:")
                        Log.i(TAG, "🆔 Message ID: ${latestMessage.messageId}")
                        Log.i(TAG, "👤 From: ${latestMessage.firstName} ${latestMessage.lastName ?: ""} (@${latestMessage.username ?: "no_username"})")
                        Log.i(TAG, "💬 Chat ID: ${latestMessage.chatId}")
                        Log.i(TAG, "📝 Text: '${latestMessage.text}'")
                        Log.i(TAG, "⏰ Timestamp: ${latestMessage.timestamp}")
                        Log.i(TAG, "🔢 User ID: ${latestMessage.userId}")
                        Log.i(TAG, "🤖 Is Bot: ${latestMessage.isBot}")
                        
                        // Mark this message as processed BEFORE executing workflow
                        Log.d(TAG, "💾 STEP T5: Marking message as processed...")
                        val marked = telegramDeduplicationService.markTelegramMessageAsProcessed(
                            telegramMessageId = latestMessage.messageId,
                            chatId = latestMessage.chatId,
                            workflowId = workflow.id,
                            userId = trigger.userId,
                            senderName = "${latestMessage.firstName} ${latestMessage.lastName ?: ""}".trim(),
                            senderUsername = latestMessage.username,
                            messageText = latestMessage.text,
                            messageTimestamp = latestMessage.timestamp
                        )
                        
                        if (!marked) {
                            Log.e(TAG, "❌ STEP T5: Failed to mark Telegram message as processed, skipping workflow execution")
                            return TriggerExecutionResult(workflow.id, "UserTelegramMessage", false, "Failed to mark message as processed")
                        }
                        
                        Log.i(TAG, "✅ STEP T5: Telegram message ${latestMessage.messageId} marked as processed")
                        
                        // Create trigger data with message details
                        Log.d(TAG, "🏗️ STEP T6: Building trigger data...")
                        val triggerData = mapOf(
                            "source" to "telegram",
                            "telegram_message" to latestMessage.text,
                            "telegram_sender_name" to "${latestMessage.firstName} ${latestMessage.lastName ?: ""}".trim(),
                            "telegram_username" to (latestMessage.username ?: ""),
                            "telegram_user_id" to latestMessage.userId.toString(),
                            "telegram_chat_id" to latestMessage.chatId.toString(),
                            "telegram_message_id" to latestMessage.messageId.toString(),
                            "telegram_timestamp" to java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(latestMessage.timestamp)),
                            "type" to "telegram_message"
                        )
                        
                        Log.i(TAG, "🚀 STEP T7: EXECUTING WORKFLOW for Telegram message ${latestMessage.messageId}")
                        Log.i(TAG, "📋 === TRIGGER DATA SUMMARY ===")
                        triggerData.forEach { (key, value) ->
                            Log.d(TAG, "📄 $key: '$value'")
                        }
                        Log.i(TAG, "📋 === END TRIGGER DATA SUMMARY ===")
                        
                        executeWorkflowWithData(workflow, trigger.userId, triggerData)
                    } else {
                        Log.w(TAG, "⚠️ STEP T4: No new unprocessed messages found for workflow: ${workflow.name}")
                        Log.d(TAG, "💡 All ${messages.size} messages were already processed previously")
                        TriggerExecutionResult(workflow.id, "UserTelegramMessage", false, "No new messages (all already processed)")
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "❌ STEP T2: FAILED to check Telegram messages")
                    Log.e(TAG, "💥 Error details: ${error.message}")
                    Log.e(TAG, "🔍 Error type: ${error.javaClass.simpleName}")
                    TriggerExecutionResult(workflow.id, "UserTelegramMessage", false, "Failed to check messages: ${error.message}")
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 STEP T1-T7: EXCEPTION in Telegram trigger check")
            Log.e(TAG, "🔍 Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "💬 Exception message: ${e.message}")
            Log.e(TAG, "📍 Stack trace: ${e.stackTraceToString()}")
            TriggerExecutionResult(workflow.id, "UserTelegramMessage", false, "Error: ${e.message}")
        } finally {
            Log.i(TAG, "🏁 === TELEGRAM TRIGGER CHECK END ===")
        }
    }
    
    /**
     * Execute workflow from trigger
     */
    private suspend fun executeWorkflowWithData(
        workflow: MultiUserWorkflow, 
        triggerUserId: String, 
        triggerData: Map<String, String>
    ): TriggerExecutionResult {
        return try {
            // Auto-save contacts from workflow trigger data
            try {
                val contactAutoSaveService = com.localllm.myapplication.di.AppContainer.provideContactAutoSaveService(context)
                contactAutoSaveService.autoSaveFromWorkflowData(triggerData)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to auto-save contacts from workflow data", e)
            }
            
            val result = workflowEngine.executeWorkflow(workflow.id, triggerUserId, triggerData)
            
            result.fold(
                onSuccess = { executionResult ->
                    Log.i(TAG, "Workflow ${workflow.id} triggered successfully")
                    TriggerExecutionResult(
                        workflow.id, 
                        "Executed", 
                        true, 
                        "Execution ID: ${executionResult.executionId}"
                    )
                },
                onFailure = { error ->
                    Log.e(TAG, "Workflow ${workflow.id} execution failed", error)
                    TriggerExecutionResult(
                        workflow.id, 
                        "ExecutionFailed", 
                        false, 
                        "Execution failed: ${error.message}"
                    )
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error executing workflow ${workflow.id}", e)
            TriggerExecutionResult(
                workflow.id, 
                "ExecutionError", 
                false, 
                "Error: ${e.message}"
            )
        }
    }
    
    private suspend fun executeWorkflow(
        workflow: MultiUserWorkflow, 
        triggerUserId: String, 
        triggerData: String
    ): TriggerExecutionResult {
        return try {
            val result = workflowEngine.executeWorkflow(workflow.id, triggerUserId, triggerData)
            
            result.fold(
                onSuccess = { executionResult ->
                    Log.i(TAG, "Workflow ${workflow.id} triggered successfully")
                    TriggerExecutionResult(
                        workflow.id, 
                        "Executed", 
                        true, 
                        "Execution ID: ${executionResult.executionId}"
                    )
                },
                onFailure = { error ->
                    Log.e(TAG, "Workflow ${workflow.id} execution failed", error)
                    TriggerExecutionResult(
                        workflow.id, 
                        "ExecutionFailed", 
                        false, 
                        "Execution failed: ${error.message}"
                    )
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error executing triggered workflow", e)
            TriggerExecutionResult(workflow.id, "Error", false, "Exception: ${e.message}")
        }
    }
    
    /**
     * Check Gmail condition (simplified implementation)
     */
    private suspend fun checkGmailCondition(
        gmailService: GmailIntegrationService, 
        condition: GmailIntegrationService.EmailCondition
    ): Boolean {
        return try {
            Log.d(TAG, "Checking Gmail condition for new emails")
            
            // Check for new emails based on the condition
            val result = gmailService.checkForNewEmails(condition, 1)
            val hasNewEmails = result.fold(
                onSuccess = { emails -> 
                    Log.d(TAG, "Found ${emails.size} emails matching condition")
                    emails.isNotEmpty() 
                },
                onFailure = { error -> 
                    Log.w(TAG, "Gmail check failed: ${error.message}")
                    false 
                }
            )
            
            hasNewEmails
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Gmail condition", e)
            false
        }
    }
    
    /**
     * Check Telegram condition (simplified implementation)
     */
    private suspend fun checkTelegramCondition(
        telegramService: TelegramBotService, 
        condition: TelegramBotService.TelegramCondition
    ): Boolean {
        return try {
            // This would check for new messages based on the condition
            // For now, return false to avoid excessive API calls during testing
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Telegram condition", e)
            false
        }
    }
    
    /**
     * Get last execution time for a workflow
     */
    private suspend fun getLastExecutionTime(workflowId: String): Long? {
        return try {
            val executions = workflowEngine.executionRepository.getExecutionHistory(workflowId, 1).getOrNull()
            executions?.firstOrNull()?.timestamp
        } catch (e: Exception) {
            Log.e(TAG, "Error getting last execution time", e)
            null
        }
    }
    
    /**
     * Manually trigger a workflow
     */
    suspend fun triggerWorkflow(workflowId: String, triggerUserId: String): Result<String> {
        return try {
            Log.i(TAG, "Manually triggering workflow: $workflowId")
            
            val workflow = workflowRepository.getWorkflowById(workflowId).getOrNull()
            if (workflow == null) {
                return Result.failure(Exception("Workflow not found"))
            }
            
            val result = workflowEngine.executeWorkflow(workflowId, triggerUserId, "Manual trigger")
            
            result.fold(
                onSuccess = { executionResult ->
                    Log.i(TAG, "Manual trigger successful for workflow $workflowId")
                    Result.success("Workflow triggered successfully. Execution ID: ${executionResult.executionId}")
                },
                onFailure = { error ->
                    Log.e(TAG, "Manual trigger failed for workflow $workflowId", error)
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in manual trigger", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get trigger statistics
     */
    suspend fun getTriggerStats(): TriggerStats {
        return try {
            val workflows = workflowRepository.getAllWorkflows().getOrNull() ?: emptyList()
            val totalTriggers = workflows.sumOf { if (it is MultiUserWorkflow) it.triggers.size else 0 }
            val activeTriggers = workflows.count { it.isEnabled }
            
            TriggerStats(
                totalTriggers = totalTriggers,
                activeWorkflows = activeTriggers,
                lastCheckTime = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting trigger stats", e)
            TriggerStats(0, 0, System.currentTimeMillis())
        }
    }
    
    data class TriggerExecutionResult(
        val workflowId: String,
        val triggerType: String,
        val triggered: Boolean,
        val message: String? = null
    )
    
    data class TriggerStats(
        val totalTriggers: Int,
        val activeWorkflows: Int,
        val lastCheckTime: Long
    )
}

/**
 * WorkManager worker for checking triggers
 */
class TriggerCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val TAG = "TriggerCheckWorker"
    }
    
    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting trigger check work")
            
            // Get trigger manager instance
            // Note: In a real implementation, you'd inject this properly
            val userManager = com.localllm.myapplication.di.AppContainer.provideUserManager(applicationContext)
            val workflowRepository = com.localllm.myapplication.di.AppContainer.provideWorkflowRepository(applicationContext)
            val workflowEngine = com.localllm.myapplication.di.AppContainer.provideWorkflowEngine(applicationContext)
            
            val triggerManager = WorkflowTriggerManager(
                applicationContext,
                workflowEngine,
                workflowRepository,
                userManager
            )
            
            val checkResult = triggerManager.checkTriggers()
            
            checkResult.fold(
                onSuccess = { results ->
                    val triggeredCount = results.count { it.triggered }
                    Log.i(TAG, "Trigger check completed: $triggeredCount workflows triggered")
                    Result.success()
                },
                onFailure = { error ->
                    Log.e(TAG, "Trigger check failed", error)
                    Result.failure()
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in trigger check work", e)
            Result.failure()
        }
    }
}