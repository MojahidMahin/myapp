package com.localllm.myapplication.service

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.work.*
import com.localllm.myapplication.data.*
import com.localllm.myapplication.service.integration.GmailIntegrationService
import com.localllm.myapplication.service.integration.TelegramBotService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
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
                is MultiUserTrigger.ImageAnalysisTrigger -> checkImageAnalysisTrigger(workflow, trigger)
                is MultiUserTrigger.AutoImageAnalysisTrigger -> checkAutoImageAnalysisTrigger(workflow, trigger)
                is MultiUserTrigger.TimeBasedImageAnalysisTrigger -> checkTimeBasedImageAnalysisTrigger(workflow, trigger)
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
    
    /**
     * Check Image Analysis trigger
     */
    private suspend fun checkImageAnalysisTrigger(
        workflow: MultiUserWorkflow,
        trigger: MultiUserTrigger.ImageAnalysisTrigger
    ): TriggerExecutionResult {
        return try {
            Log.i(TAG, "🖼️ === IMAGE ANALYSIS TRIGGER CHECK START ===")
            Log.i(TAG, "📋 Workflow: ${workflow.name} (ID: ${workflow.id})")
            Log.i(TAG, "👤 User: ${trigger.userId}")
            Log.i(TAG, "🏷️ Trigger Name: ${trigger.triggerName}")
            Log.i(TAG, "📷 Image attachments: ${trigger.imageAttachments.size}")
            
            if (trigger.imageAttachments.isEmpty()) {
                Log.w(TAG, "⚠️ No image attachments provided for image analysis trigger")
                return TriggerExecutionResult(
                    workflow.id,
                    "ImageAnalysisTrigger",
                    false,
                    "No image attachments provided"
                )
            }
            
            // Check rate limiting based on retrigger delay
            val triggerKey = "${workflow.id}_${trigger.triggerName}"
            val lastTrigger = lastEmailCheckTimes[triggerKey] ?: 0
            val currentTime = System.currentTimeMillis()
            val timeSinceLastTrigger = currentTime - lastTrigger
            
            if (timeSinceLastTrigger < trigger.retriggerDelay) {
                val remainingTime = (trigger.retriggerDelay - timeSinceLastTrigger) / 1000
                Log.d(TAG, "Rate limiting: Last trigger was ${timeSinceLastTrigger}ms ago, waiting ${remainingTime}s more")
                return TriggerExecutionResult(
                    workflow.id,
                    "ImageAnalysisTrigger",
                    false,
                    "Rate limited: triggered ${remainingTime}s ago"
                )
            }
            
            // Get image analysis service
            val imageAnalysisService = getImageAnalysisService()
            if (imageAnalysisService == null) {
                Log.e(TAG, "❌ Image analysis service not available")
                return TriggerExecutionResult(
                    workflow.id,
                    "ImageAnalysisTrigger",
                    false,
                    "Image analysis service not available"
                )
            }
            
            Log.d(TAG, "🔍 Starting analysis of ${trigger.imageAttachments.size} image(s)")
            
            val analysisResults = mutableListOf<ImageAnalysisWorkflowResult>()
            var hasValidTrigger = false
            
            // Analyze each image attachment
            for ((index, attachment) in trigger.imageAttachments.withIndex()) {
                Log.d(TAG, "📷 Analyzing image ${index + 1}/${trigger.imageAttachments.size}: ${attachment.fileName}")
                
                try {
                    val bitmap = loadImageFromAttachment(attachment)
                    if (bitmap == null) {
                        Log.w(TAG, "❌ Failed to load image: ${attachment.fileName}")
                        continue
                    }
                    
                    // Combine analysis questions from trigger and attachment
                    val combinedQuestions = (trigger.analysisQuestions + attachment.analysisQuestions).distinct()
                    
                    // Perform image analysis
                    val analysisResult = imageAnalysisService.analyzeImage(
                        bitmap = bitmap,
                        userQuestion = combinedQuestions.joinToString("; ")
                    )
                    
                    if (analysisResult.success) {
                        // Extract keywords from analysis
                        val extractedKeywords = extractKeywordsFromAnalysis(analysisResult)
                        
                        // Check for keyword matches if required
                        val keywordMatches = if (trigger.analysisKeywords.isNotEmpty()) {
                            findKeywordMatches(extractedKeywords, trigger.analysisKeywords)
                        } else {
                            emptyList()
                        }
                        
                        // Check if this result should trigger the workflow
                        val shouldTrigger = if (trigger.triggerOnKeywordMatch) {
                            keywordMatches.isNotEmpty()
                        } else {
                            analysisResult.confidence >= trigger.minimumConfidence
                        }
                        
                        if (shouldTrigger) {
                            hasValidTrigger = true
                        }
                        
                        val workflowResult = ImageAnalysisWorkflowResult(
                            attachmentId = attachment.id,
                            fileName = attachment.fileName,
                            success = true,
                            analysisType = trigger.analysisType,
                            description = analysisResult.description,
                            ocrText = analysisResult.ocrText,
                            peopleCount = analysisResult.objectsDetected.peopleCount,
                            detectedObjects = analysisResult.objectsDetected.detectedObjects,
                            dominantColors = analysisResult.visualElements.dominantColors,
                            confidence = analysisResult.confidence,
                            keywords = extractedKeywords,
                            keywordMatches = keywordMatches,
                            visualElements = mapOf(
                                "brightness" to analysisResult.visualElements.brightness,
                                "contrast" to analysisResult.visualElements.contrast,
                                "composition" to analysisResult.visualElements.composition,
                                "clarity" to analysisResult.visualElements.clarity
                            ),
                            analysisTime = System.currentTimeMillis()
                        )
                        
                        analysisResults.add(workflowResult)
                        
                        Log.i(TAG, "✅ Analysis completed for ${attachment.fileName}")
                        Log.d(TAG, "   Confidence: ${analysisResult.confidence}")
                        Log.d(TAG, "   Keywords found: ${extractedKeywords.size}")
                        Log.d(TAG, "   Keyword matches: ${keywordMatches.size}")
                        Log.d(TAG, "   Should trigger: $shouldTrigger")
                        
                    } else {
                        Log.w(TAG, "❌ Analysis failed for ${attachment.fileName}")
                        analysisResults.add(
                            ImageAnalysisWorkflowResult(
                                attachmentId = attachment.id,
                                fileName = attachment.fileName,
                                success = false,
                                analysisType = trigger.analysisType,
                                description = "Analysis failed",
                                error = "Image analysis failed"
                            )
                        )
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "💥 Exception analyzing image ${attachment.fileName}", e)
                    analysisResults.add(
                        ImageAnalysisWorkflowResult(
                            attachmentId = attachment.id,
                            fileName = attachment.fileName,
                            success = false,
                            analysisType = trigger.analysisType,
                            description = "Analysis failed with exception",
                            error = e.message
                        )
                    )
                }
            }
            
            if (hasValidTrigger && analysisResults.isNotEmpty()) {
                // Update last trigger time
                lastEmailCheckTimes[triggerKey] = currentTime
                
                // Create trigger data with analysis results
                val triggerData = mapOf(
                    "source" to "image_analysis",
                    "trigger_name" to trigger.triggerName,
                    "analysis_type" to trigger.analysisType.name,
                    "images_analyzed" to analysisResults.size.toString(),
                    "successful_analyses" to analysisResults.count { it.success }.toString(),
                    "analysis_results" to analysisResults.joinToString("\n\n") { result ->
                        "Image: ${result.fileName}\n" +
                        "Success: ${result.success}\n" +
                        "Description: ${result.description}\n" +
                        "OCR Text: ${result.ocrText}\n" +
                        "People Count: ${result.peopleCount}\n" +
                        "Objects: ${result.detectedObjects.joinToString(", ")}\n" +
                        "Keywords: ${result.keywords.joinToString(", ")}\n" +
                        "Keyword Matches: ${result.keywordMatches.joinToString(", ")}"
                    },
                    "type" to "image_analysis_trigger"
                )
                
                Log.i(TAG, "🎯 TRIGGERING WORKFLOW - Image analysis conditions met")
                Log.i(TAG, "   Analyzed images: ${analysisResults.size}")
                Log.i(TAG, "   Successful analyses: ${analysisResults.count { it.success }}")
                
                executeWorkflowWithData(workflow, trigger.userId, triggerData)
            } else {
                Log.d(TAG, "⭕ No trigger conditions met")
                Log.d(TAG, "   Has valid trigger: $hasValidTrigger")
                Log.d(TAG, "   Analysis results: ${analysisResults.size}")
                TriggerExecutionResult(
                    workflow.id,
                    "ImageAnalysisTrigger",
                    false,
                    "Analysis completed but trigger conditions not met"
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 ERROR in Image Analysis trigger check", e)
            TriggerExecutionResult(
                workflow.id,
                "ImageAnalysisTrigger",
                false,
                "Error: ${e.message}"
            )
        } finally {
            Log.d(TAG, "🏁 === IMAGE ANALYSIS TRIGGER CHECK END ===")
        }
    }
    
    /**
     * Check Auto Image Analysis trigger (monitors directory for new images)
     */
    private suspend fun checkAutoImageAnalysisTrigger(
        workflow: MultiUserWorkflow,
        trigger: MultiUserTrigger.AutoImageAnalysisTrigger
    ): TriggerExecutionResult {
        return try {
            Log.i(TAG, "📁 === AUTO IMAGE ANALYSIS TRIGGER CHECK START ===")
            Log.i(TAG, "📋 Workflow: ${workflow.name}")
            Log.i(TAG, "📂 Source directory: ${trigger.sourceDirectory}")
            
            // For now, return not implemented
            // This would monitor a directory for new images and trigger analysis
            TriggerExecutionResult(
                workflow.id,
                "AutoImageAnalysisTrigger",
                false,
                "Auto image analysis monitoring not yet implemented"
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 ERROR in Auto Image Analysis trigger check", e)
            TriggerExecutionResult(
                workflow.id,
                "AutoImageAnalysisTrigger",
                false,
                "Error: ${e.message}"
            )
        } finally {
            Log.d(TAG, "🏁 === AUTO IMAGE ANALYSIS TRIGGER CHECK END ===")
        }
    }
    
    /**
     * Check Time-Based Image Analysis trigger
     */
    private suspend fun checkTimeBasedImageAnalysisTrigger(
        workflow: MultiUserWorkflow,
        trigger: MultiUserTrigger.TimeBasedImageAnalysisTrigger
    ): TriggerExecutionResult {
        return try {
            Log.i(TAG, "⏰ === TIME-BASED IMAGE ANALYSIS TRIGGER CHECK START ===")
            Log.i(TAG, "📋 Workflow: ${workflow.name} (ID: ${workflow.id})")
            Log.i(TAG, "👤 User: ${trigger.userId}")
            Log.i(TAG, "🏷️ Trigger Name: ${trigger.triggerName}")
            Log.i(TAG, "📅 Schedule Type: ${trigger.timeSchedule.scheduleType}")
            Log.i(TAG, "🕐 Time of Day: ${trigger.timeSchedule.timeOfDay}")
            Log.i(TAG, "📷 Image attachments: ${trigger.imageAttachments.size}")
            
            // Check if it's time to trigger based on schedule
            val currentTime = System.currentTimeMillis()
            val shouldTrigger = isTimeToTrigger(trigger.timeSchedule, trigger.lastExecutionTime, currentTime)
            
            if (!shouldTrigger.first) {
                Log.d(TAG, "⏰ Not yet time to trigger: ${shouldTrigger.second}")
                return TriggerExecutionResult(
                    workflow.id,
                    "TimeBasedImageAnalysisTrigger",
                    false,
                    shouldTrigger.second
                )
            }
            
            Log.i(TAG, "✅ Time condition met, proceeding with image analysis")
            
            if (trigger.imageAttachments.isEmpty()) {
                Log.w(TAG, "⚠️ No image attachments provided for time-based image analysis trigger")
                return TriggerExecutionResult(
                    workflow.id,
                    "TimeBasedImageAnalysisTrigger",
                    false,
                    "No image attachments provided"
                )
            }
            
            // Get image analysis service
            val imageAnalysisService = getImageAnalysisService()
            if (imageAnalysisService == null) {
                Log.e(TAG, "❌ Image analysis service not available")
                return TriggerExecutionResult(
                    workflow.id,
                    "TimeBasedImageAnalysisTrigger",
                    false,
                    "Image analysis service not available"
                )
            }
            
            Log.d(TAG, "🔍 Starting scheduled analysis of ${trigger.imageAttachments.size} image(s)")
            
            val analysisResults = mutableListOf<ImageAnalysisWorkflowResult>()
            var hasValidTrigger = false
            
            // Analyze each image attachment
            for ((index, attachment) in trigger.imageAttachments.withIndex()) {
                Log.d(TAG, "📷 Analyzing image ${index + 1}/${trigger.imageAttachments.size}: ${attachment.fileName}")
                
                try {
                    val bitmap = loadImageFromAttachment(attachment)
                    if (bitmap == null) {
                        Log.w(TAG, "❌ Failed to load image: ${attachment.fileName}")
                        continue
                    }
                    
                    // Combine analysis questions from trigger and attachment
                    val combinedQuestions = (trigger.analysisQuestions + attachment.analysisQuestions).distinct()
                    
                    // Perform image analysis
                    val analysisResult = imageAnalysisService.analyzeImage(
                        bitmap = bitmap,
                        userQuestion = combinedQuestions.joinToString("; ")
                    )
                    
                    if (analysisResult.success) {
                        // Extract keywords from analysis
                        val extractedKeywords = extractKeywordsFromAnalysis(analysisResult)
                        
                        // Check for keyword matches if required
                        val keywordMatches = if (trigger.analysisKeywords.isNotEmpty()) {
                            findKeywordMatches(extractedKeywords, trigger.analysisKeywords)
                        } else {
                            emptyList()
                        }
                        
                        // Check if this result should trigger the workflow
                        val shouldTriggerWorkflow = if (trigger.triggerOnKeywordMatch) {
                            keywordMatches.isNotEmpty()
                        } else {
                            analysisResult.confidence >= trigger.minimumConfidence
                        }
                        
                        if (shouldTriggerWorkflow) {
                            hasValidTrigger = true
                        }
                        
                        val workflowResult = ImageAnalysisWorkflowResult(
                            attachmentId = attachment.id,
                            fileName = attachment.fileName,
                            success = true,
                            analysisType = trigger.analysisType,
                            description = analysisResult.description,
                            ocrText = analysisResult.ocrText,
                            peopleCount = analysisResult.objectsDetected.peopleCount,
                            detectedObjects = analysisResult.objectsDetected.detectedObjects,
                            dominantColors = analysisResult.visualElements.dominantColors,
                            confidence = analysisResult.confidence,
                            keywords = extractedKeywords,
                            keywordMatches = keywordMatches,
                            visualElements = mapOf(
                                "brightness" to analysisResult.visualElements.brightness,
                                "contrast" to analysisResult.visualElements.contrast,
                                "composition" to analysisResult.visualElements.composition,
                                "clarity" to analysisResult.visualElements.clarity
                            ),
                            analysisTime = System.currentTimeMillis()
                        )
                        
                        analysisResults.add(workflowResult)
                        
                        Log.i(TAG, "✅ Scheduled analysis completed for ${attachment.fileName}")
                        Log.d(TAG, "   Confidence: ${analysisResult.confidence}")
                        Log.d(TAG, "   Keywords found: ${extractedKeywords.size}")
                        Log.d(TAG, "   Keyword matches: ${keywordMatches.size}")
                        Log.d(TAG, "   Should trigger: $shouldTriggerWorkflow")
                        
                    } else {
                        Log.w(TAG, "❌ Scheduled analysis failed for ${attachment.fileName}")
                        analysisResults.add(
                            ImageAnalysisWorkflowResult(
                                attachmentId = attachment.id,
                                fileName = attachment.fileName,
                                success = false,
                                analysisType = trigger.analysisType,
                                description = "Analysis failed",
                                error = "Image analysis failed"
                            )
                        )
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "💥 Exception analyzing scheduled image ${attachment.fileName}", e)
                    analysisResults.add(
                        ImageAnalysisWorkflowResult(
                            attachmentId = attachment.id,
                            fileName = attachment.fileName,
                            success = false,
                            analysisType = trigger.analysisType,
                            description = "Analysis failed with exception",
                            error = e.message
                        )
                    )
                }
            }
            
            if (hasValidTrigger && analysisResults.isNotEmpty()) {
                // Update last execution time - you would need to persist this in the workflow
                // For now, we'll track it in memory
                updateLastExecutionTime(workflow.id, currentTime)
                
                // Create trigger data with analysis results
                val triggerData = mapOf(
                    "source" to "time_based_image_analysis",
                    "trigger_name" to trigger.triggerName,
                    "scheduled_time" to trigger.timeSchedule.timeOfDay,
                    "execution_time" to java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(currentTime)),
                    "analysis_type" to trigger.analysisType.name,
                    "images_analyzed" to analysisResults.size.toString(),
                    "successful_analyses" to analysisResults.count { it.success }.toString(),
                    "analysis_results" to analysisResults.joinToString("\n\n") { result ->
                        "Image: ${result.fileName}\n" +
                        "Success: ${result.success}\n" +
                        "Description: ${result.description}\n" +
                        "OCR Text: ${result.ocrText}\n" +
                        "People Count: ${result.peopleCount}\n" +
                        "Objects: ${result.detectedObjects.joinToString(", ")}\n" +
                        "Keywords: ${result.keywords.joinToString(", ")}\n" +
                        "Keyword Matches: ${result.keywordMatches.joinToString(", ")}"
                    },
                    "type" to "time_based_image_analysis_trigger"
                )
                
                Log.i(TAG, "🎯 TRIGGERING SCHEDULED WORKFLOW - Time-based image analysis conditions met")
                Log.i(TAG, "   Scheduled time: ${trigger.timeSchedule.timeOfDay}")
                Log.i(TAG, "   Analyzed images: ${analysisResults.size}")
                Log.i(TAG, "   Successful analyses: ${analysisResults.count { it.success }}")
                
                executeWorkflowWithData(workflow, trigger.userId, triggerData)
            } else {
                Log.d(TAG, "⭕ Time-based trigger executed but analysis conditions not met")
                Log.d(TAG, "   Has valid trigger: $hasValidTrigger")
                Log.d(TAG, "   Analysis results: ${analysisResults.size}")
                
                // Still update execution time even if analysis conditions weren't met
                // to prevent repeated executions at the same scheduled time
                updateLastExecutionTime(workflow.id, currentTime)
                
                TriggerExecutionResult(
                    workflow.id,
                    "TimeBasedImageAnalysisTrigger",
                    false,
                    "Scheduled analysis completed but trigger conditions not met"
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 ERROR in Time-Based Image Analysis trigger check", e)
            TriggerExecutionResult(
                workflow.id,
                "TimeBasedImageAnalysisTrigger",
                false,
                "Error: ${e.message}"
            )
        } finally {
            Log.d(TAG, "🏁 === TIME-BASED IMAGE ANALYSIS TRIGGER CHECK END ===")
        }
    }
    
    /**
     * Check if it's time to trigger based on the schedule
     */
    private fun isTimeToTrigger(
        schedule: ImageAnalysisTimeSchedule,
        lastExecutionTime: Long,
        currentTime: Long
    ): Pair<Boolean, String> {
        val currentCalendar = java.util.Calendar.getInstance()
        val currentHour = currentCalendar.get(java.util.Calendar.HOUR_OF_DAY)
        val currentMinute = currentCalendar.get(java.util.Calendar.MINUTE)
        val currentDayOfWeek = DayOfWeek.fromCalendarDay(currentCalendar.get(java.util.Calendar.DAY_OF_WEEK))
        
        // Parse scheduled time
        val timeParts = schedule.timeOfDay.split(":")
        if (timeParts.size != 2) {
            return Pair(false, "Invalid time format: ${schedule.timeOfDay}")
        }
        
        val scheduledHour = timeParts[0].toIntOrNull() ?: return Pair(false, "Invalid hour: ${timeParts[0]}")
        val scheduledMinute = timeParts[1].toIntOrNull() ?: return Pair(false, "Invalid minute: ${timeParts[1]}")
        
        // Check if we're within the scheduled time window (allow 1 minute window)
        val timeDiffMinutes = kotlin.math.abs((currentHour * 60 + currentMinute) - (scheduledHour * 60 + scheduledMinute))
        val isWithinTimeWindow = timeDiffMinutes <= 1
        
        if (!isWithinTimeWindow) {
            val nextTriggerTime = String.format("%02d:%02d", scheduledHour, scheduledMinute)
            return Pair(false, "Current time ${String.format("%02d:%02d", currentHour, currentMinute)} is not within trigger window (${nextTriggerTime} ±1 min)")
        }
        
        // Check if we've already executed today (for daily/weekly schedules)
        val lastExecutionCalendar = java.util.Calendar.getInstance()
        lastExecutionCalendar.timeInMillis = lastExecutionTime
        
        val isSameDay = currentCalendar.get(java.util.Calendar.YEAR) == lastExecutionCalendar.get(java.util.Calendar.YEAR) &&
                       currentCalendar.get(java.util.Calendar.DAY_OF_YEAR) == lastExecutionCalendar.get(java.util.Calendar.DAY_OF_YEAR)
        
        when (schedule.scheduleType) {
            TimeScheduleType.DAILY -> {
                if (isSameDay && lastExecutionTime > 0) {
                    return Pair(false, "Already executed today at ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(lastExecutionTime))}")
                }
                return Pair(true, "Daily trigger condition met")
            }
            
            TimeScheduleType.WEEKLY -> {
                if (currentDayOfWeek == null || !schedule.daysOfWeek.contains(currentDayOfWeek)) {
                    val daysStr = schedule.daysOfWeek.joinToString(", ") { it.name.take(3) }
                    return Pair(false, "Today (${currentDayOfWeek?.name ?: "Unknown"}) is not in scheduled days: $daysStr")
                }
                
                if (isSameDay && lastExecutionTime > 0) {
                    return Pair(false, "Already executed today at ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(lastExecutionTime))}")
                }
                
                return Pair(true, "Weekly trigger condition met for ${currentDayOfWeek.name}")
            }
            
            TimeScheduleType.ONCE -> {
                if (lastExecutionTime > 0) {
                    return Pair(false, "One-time trigger already executed at ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(lastExecutionTime))}")
                }
                return Pair(true, "One-time trigger condition met")
            }
            
            TimeScheduleType.INTERVAL -> {
                val intervalMillis = schedule.intervalMinutes * 60 * 1000L
                if (lastExecutionTime > 0 && (currentTime - lastExecutionTime) < intervalMillis) {
                    val remainingMinutes = ((intervalMillis - (currentTime - lastExecutionTime)) / 60000).toInt()
                    return Pair(false, "Interval not yet reached, ${remainingMinutes} minutes remaining")
                }
                return Pair(true, "Interval trigger condition met")
            }
        }
    }
    
    /**
     * Update last execution time for a workflow (in-memory for now)
     */
    private val lastExecutionTimes = mutableMapOf<String, Long>()
    
    private fun updateLastExecutionTime(workflowId: String, executionTime: Long) {
        lastExecutionTimes[workflowId] = executionTime
        Log.d(TAG, "Updated last execution time for workflow $workflowId: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(executionTime))}")
    }
    
    /**
     * Load image from attachment
     */
    private fun loadImageFromAttachment(attachment: ImageAttachment): android.graphics.Bitmap? {
        return try {
            when {
                !attachment.filePath.isNullOrBlank() -> {
                    val file = File(attachment.filePath)
                    if (file.exists()) {
                        BitmapFactory.decodeFile(attachment.filePath)
                    } else {
                        Log.w(TAG, "Image file not found: ${attachment.filePath}")
                        null
                    }
                }
                !attachment.uri.isNullOrBlank() -> {
                    val uri = Uri.parse(attachment.uri)
                    val inputStream = context.contentResolver.openInputStream(uri)
                    BitmapFactory.decodeStream(inputStream)
                }
                else -> {
                    Log.w(TAG, "No valid image source in attachment: ${attachment.fileName}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading image from attachment: ${attachment.fileName}", e)
            null
        }
    }
    
    /**
     * Extract keywords from image analysis result
     */
    private fun extractKeywordsFromAnalysis(analysisResult: com.localllm.myapplication.service.ImageAnalysisResult): List<String> {
        val keywords = mutableSetOf<String>()
        
        // Extract from OCR text
        if (analysisResult.ocrText.isNotBlank()) {
            keywords.addAll(
                analysisResult.ocrText
                    .split(Regex("[\\s,;.!?]+"))
                    .filter { it.length > 2 }
                    .map { it.lowercase() }
            )
        }
        
        // Extract from description
        keywords.addAll(
            analysisResult.description
                .split(Regex("[\\s,;.!?]+"))
                .filter { it.length > 2 }
                .map { it.lowercase() }
        )
        
        // Add detected objects
        keywords.addAll(analysisResult.objectsDetected.detectedObjects.map { it.lowercase() })
        
        // Add dominant colors
        keywords.addAll(analysisResult.visualElements.dominantColors.map { it.lowercase() })
        
        return keywords.toList()
    }
    
    /**
     * Find keyword matches between extracted and target keywords
     */
    private fun findKeywordMatches(extractedKeywords: List<String>, targetKeywords: List<String>): List<String> {
        val matches = mutableListOf<String>()
        
        for (target in targetKeywords) {
            val targetLower = target.lowercase()
            
            // Exact match
            if (extractedKeywords.any { it.lowercase() == targetLower }) {
                matches.add(target)
                continue
            }
            
            // Partial match (target keyword contains or is contained in extracted keyword)
            if (extractedKeywords.any { 
                it.lowercase().contains(targetLower) || targetLower.contains(it.lowercase())
            }) {
                matches.add(target)
            }
        }
        
        return matches.distinct()
    }
    
    /**
     * Get image analysis service instance
     */
    private fun getImageAnalysisService(): ImageAnalysisService? {
        return try {
            ImageAnalysisService()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create image analysis service", e)
            null
        }
    }
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