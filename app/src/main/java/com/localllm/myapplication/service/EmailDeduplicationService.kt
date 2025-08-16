package com.localllm.myapplication.service

import android.content.Context
import android.util.Log
import com.localllm.myapplication.data.database.ProcessedEmailDao
import com.localllm.myapplication.data.database.ProcessedEmailEntity
import com.localllm.myapplication.data.database.WorkflowDatabase
import com.localllm.myapplication.service.integration.GmailIntegrationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service for managing email deduplication to prevent duplicate workflow triggers
 */
class EmailDeduplicationService(context: Context) {
    
    companion object {
        private const val TAG = "EmailDeduplicationService"
        private const val EMAIL_RETENTION_DAYS = 30 // Keep processed emails for 30 days
        private const val MIN_EMAIL_AGE_MINUTES = 0 // Process emails immediately for workflow automation
    }
    
    private val database = WorkflowDatabase.getDatabase(context)
    private val processedEmailDao: ProcessedEmailDao = database.processedEmailDao()
    
    /**
     * Check if an email has already been processed for a specific workflow
     */
    suspend fun isEmailProcessed(emailId: String, workflowId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val processed = processedEmailDao.isEmailProcessed(emailId, workflowId)
                val result = processed != null
                Log.d(TAG, "Email $emailId for workflow $workflowId - Already processed: $result")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Error checking if email is processed", e)
                false // Assume not processed if error occurs
            }
        }
    }
    
    /**
     * Mark an email as processed for a specific workflow
     */
    suspend fun markEmailAsProcessed(
        emailId: String,
        workflowId: String,
        userId: String,
        emailFrom: String,
        emailSubject: String,
        emailTimestamp: Long
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val processedEmail = ProcessedEmailEntity(
                    emailId = emailId,
                    workflowId = workflowId,
                    userId = userId,
                    emailFrom = emailFrom,
                    emailSubject = emailSubject,
                    processedAt = System.currentTimeMillis(),
                    triggerTimestamp = emailTimestamp
                )
                
                processedEmailDao.insertProcessedEmail(processedEmail)
                Log.i(TAG, "Marked email $emailId as processed for workflow $workflowId")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error marking email as processed", e)
                false
            }
        }
    }
    
    /**
     * Filter emails to only include new, unprocessed ones
     */
    suspend fun filterNewEmails(
        emails: List<GmailIntegrationService.EmailMessage>,
        workflowId: String
    ): List<GmailIntegrationService.EmailMessage> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Filtering ${emails.size} emails for workflow $workflowId")
                
                val currentTime = System.currentTimeMillis()
                val minAgeThreshold = currentTime - (MIN_EMAIL_AGE_MINUTES * 60 * 1000)
                
                val filteredEmails = mutableListOf<GmailIntegrationService.EmailMessage>()
                
                for (email in emails) {
                    // Check if email is old enough to process (only if MIN_EMAIL_AGE_MINUTES > 0)
                    if (MIN_EMAIL_AGE_MINUTES > 0 && email.timestamp > minAgeThreshold) {
                        Log.d(TAG, "Email ${email.id} is too recent (${email.timestamp} > $minAgeThreshold), skipping")
                        continue
                    }
                    
                    // Check if already processed
                    if (!isEmailProcessed(email.id, workflowId)) {
                        filteredEmails.add(email)
                        Log.d(TAG, "Email ${email.id} is new and unprocessed, including in results")
                    } else {
                        Log.d(TAG, "Email ${email.id} already processed, skipping")
                    }
                }
                
                Log.i(TAG, "Filtered ${filteredEmails.size} new emails from ${emails.size} total emails")
                filteredEmails
                
            } catch (e: Exception) {
                Log.e(TAG, "Error filtering emails", e)
                emptyList() // Return empty list if error occurs
            }
        }
    }
    
    /**
     * Get processed email statistics for a workflow
     */
    suspend fun getProcessedEmailStats(workflowId: String): ProcessedEmailStats {
        return withContext(Dispatchers.IO) {
            try {
                val count = processedEmailDao.getProcessedEmailCount(workflowId)
                val recentEmails = processedEmailDao.getProcessedEmails(workflowId, 10)
                
                ProcessedEmailStats(
                    totalProcessed = count,
                    recentEmails = recentEmails.map { 
                        ProcessedEmailInfo(
                            emailId = it.emailId,
                            emailFrom = it.emailFrom,
                            emailSubject = it.emailSubject,
                            processedAt = it.processedAt
                        )
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error getting processed email stats", e)
                ProcessedEmailStats(0, emptyList())
            }
        }
    }
    
    /**
     * Clean up old processed email records
     */
    suspend fun cleanupOldRecords() {
        withContext(Dispatchers.IO) {
            try {
                val cutoffTime = System.currentTimeMillis() - (EMAIL_RETENTION_DAYS * 24 * 60 * 60 * 1000L)
                processedEmailDao.deleteOldProcessedEmails(cutoffTime)
                Log.i(TAG, "Cleaned up processed email records older than $EMAIL_RETENTION_DAYS days")
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up old records", e)
            }
        }
    }
    
    /**
     * Clear all processed emails for a specific workflow
     */
    suspend fun clearProcessedEmailsForWorkflow(workflowId: String) {
        withContext(Dispatchers.IO) {
            try {
                processedEmailDao.deleteProcessedEmailsForWorkflow(workflowId)
                Log.i(TAG, "Cleared all processed emails for workflow $workflowId")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing processed emails for workflow", e)
            }
        }
    }
    
    data class ProcessedEmailStats(
        val totalProcessed: Int,
        val recentEmails: List<ProcessedEmailInfo>
    )
    
    data class ProcessedEmailInfo(
        val emailId: String,
        val emailFrom: String,
        val emailSubject: String,
        val processedAt: Long
    )
}