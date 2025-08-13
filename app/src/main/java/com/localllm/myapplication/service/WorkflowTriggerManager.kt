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
                refreshActiveTriggers()
                
                // Actually check triggers for new emails/messages
                val result = checkTriggers()
                result.fold(
                    onSuccess = { results ->
                        val triggeredCount = results.count { it.triggered }
                        if (triggeredCount > 0) {
                            Log.i(TAG, "Triggers processed: $triggeredCount workflows triggered")
                        } else {
                            Log.d(TAG, "Triggers checked: ${results.size} workflows, none triggered")
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Error checking triggers: ${error.message}")
                    }
                )
                
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
                is MultiUserTrigger.UserTelegramMessage -> checkTelegramTrigger(workflow, trigger)
                is MultiUserTrigger.ManualTrigger -> {
                    // Manual triggers are not checked automatically
                    TriggerExecutionResult(workflow.id, trigger::class.simpleName ?: "Manual", false)
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
     * Check Gmail trigger
     */
    private suspend fun checkGmailTrigger(
        workflow: MultiUserWorkflow, 
        trigger: MultiUserTrigger.UserGmailNewEmail
    ): TriggerExecutionResult {
        return try {
            Log.d(TAG, "Checking Gmail trigger for workflow: ${workflow.name}, user: ${trigger.userId}")
            
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
            
            Log.d(TAG, "Gmail service available, checking condition for new emails")
            
            // Check for new emails based on condition and get email details
            val emailsResult = gmailService.checkForNewEmails(trigger.condition, 1)
            emailsResult.fold(
                onSuccess = { emails ->
                    if (emails.isNotEmpty()) {
                        val latestEmail = emails.first()
                        Log.i(TAG, "New emails found! Executing workflow: ${workflow.name}")
                        
                        // Create trigger data with email details
                        val triggerData = mapOf(
                            "source" to "gmail",
                            "email_from" to latestEmail.from,
                            "email_subject" to latestEmail.subject,
                            "email_body" to latestEmail.body,
                            "trigger_email_id" to latestEmail.id, // For template substitution
                            "type" to "new_email"
                        )
                        
                        executeWorkflowWithData(workflow, trigger.userId, triggerData)
                    } else {
                        Log.d(TAG, "No new emails matching condition for workflow: ${workflow.name}")
                        TriggerExecutionResult(workflow.id, "UserGmailNewEmail", false, "No matching emails found")
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to check emails: ${error.message}")
                    TriggerExecutionResult(workflow.id, "UserGmailNewEmail", false, "Failed to check emails: ${error.message}")
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Gmail trigger", e)
            TriggerExecutionResult(workflow.id, "UserGmailNewEmail", false, "Error: ${e.message}")
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
            val telegramService = userManager.getTelegramService(trigger.userId)
            if (telegramService == null) {
                return TriggerExecutionResult(
                    workflow.id, 
                    "UserTelegramMessage", 
                    false, 
                    "Telegram service not available for user"
                )
            }
            
            // Check for new messages based on condition
            val hasNewMessages = checkTelegramCondition(telegramService, trigger.condition)
            
            if (hasNewMessages) {
                executeWorkflow(workflow, trigger.userId, "Telegram trigger - new message")
            } else {
                TriggerExecutionResult(workflow.id, "UserTelegramMessage", false, "No matching messages found")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Telegram trigger", e)
            TriggerExecutionResult(workflow.id, "UserTelegramMessage", false, "Error: ${e.message}")
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