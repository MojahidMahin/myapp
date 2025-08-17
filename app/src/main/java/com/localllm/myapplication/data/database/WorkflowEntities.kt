package com.localllm.myapplication.data.database

import androidx.room.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.localllm.myapplication.data.*

@Entity(tableName = "workflow_users")
data class WorkflowUserEntity(
    @PrimaryKey val id: String,
    val email: String,
    val displayName: String,
    val telegramUserId: Long? = null,
    val telegramUsername: String? = null,
    val gmailConnected: Boolean = false,
    val telegramConnected: Boolean = false,
    val permissions: String, // JSON string of permissions
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toWorkflowUser(): WorkflowUser {
        val permissionSet = try {
            val gson = Gson()
            val type = object : TypeToken<Set<Permission>>() {}.type
            gson.fromJson<Set<Permission>>(permissions, type)
        } catch (e: Exception) {
            setOf(Permission.CREATE_WORKFLOW, Permission.EXECUTE_WORKFLOW)
        }
        
        return WorkflowUser(
            id = id,
            email = email,
            displayName = displayName,
            telegramUserId = telegramUserId,
            telegramUsername = telegramUsername,
            gmailConnected = gmailConnected,
            telegramConnected = telegramConnected,
            permissions = permissionSet,
            createdAt = createdAt
        )
    }
}

fun WorkflowUser.toEntity(): WorkflowUserEntity {
    val gson = Gson()
    val permissionsJson = gson.toJson(permissions)
    
    return WorkflowUserEntity(
        id = id,
        email = email,
        displayName = displayName,
        telegramUserId = telegramUserId,
        telegramUsername = telegramUsername,
        gmailConnected = gmailConnected,
        telegramConnected = telegramConnected,
        permissions = permissionsJson,
        createdAt = createdAt
    )
}

@Entity(tableName = "workflows")
data class WorkflowEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val createdBy: String,
    val workflowType: String, // WorkflowType as string
    val sharedWith: String, // JSON array of user IDs
    val isPublic: Boolean = false,
    val permissions: String, // JSON string of WorkflowPermissions
    val triggers: String, // JSON array of triggers
    val actions: String, // JSON array of actions
    val runInBackground: Boolean = true,
    val variables: String // JSON map of variables
) {
    fun toMultiUserWorkflow(): MultiUserWorkflow {
        val gson = Gson()
        
        val sharedWithList = try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(sharedWith, type) ?: emptyList()
        } catch (e: Exception) { emptyList<String>() }
        
        val workflowPermissions = try {
            gson.fromJson(permissions, WorkflowPermissions::class.java) ?: WorkflowPermissions()
        } catch (e: Exception) { WorkflowPermissions() }
        
        val triggersList = try {
            val type = object : TypeToken<List<MultiUserTrigger>>() {}.type
            gson.fromJson<List<MultiUserTrigger>>(triggers, type) ?: emptyList()
        } catch (e: Exception) { emptyList<MultiUserTrigger>() }
        
        val actionsList = try {
            val type = object : TypeToken<List<MultiUserAction>>() {}.type
            gson.fromJson<List<MultiUserAction>>(actions, type) ?: emptyList()
        } catch (e: Exception) { emptyList<MultiUserAction>() }
        
        val variablesMap = try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson<Map<String, String>>(variables, type) ?: emptyMap()
        } catch (e: Exception) { emptyMap<String, String>() }
        
        return MultiUserWorkflow(
            id = id,
            name = name,
            description = description,
            isEnabled = isEnabled,
            createdAt = createdAt,
            updatedAt = updatedAt,
            createdBy = createdBy,
            workflowType = WorkflowType.valueOf(workflowType),
            sharedWith = sharedWithList,
            isPublic = isPublic,
            permissions = workflowPermissions,
            triggers = triggersList,
            actions = actionsList,
            runInBackground = runInBackground,
            variables = variablesMap
        )
    }
}

fun MultiUserWorkflow.toEntity(): WorkflowEntity {
    val gson = Gson()
    
    return WorkflowEntity(
        id = id,
        name = name,
        description = description,
        isEnabled = isEnabled,
        createdAt = createdAt,
        updatedAt = updatedAt,
        createdBy = createdBy,
        workflowType = workflowType.name,
        sharedWith = gson.toJson(sharedWith),
        isPublic = isPublic,
        permissions = gson.toJson(permissions),
        triggers = gson.toJson(triggers),
        actions = gson.toJson(actions),
        runInBackground = runInBackground,
        variables = gson.toJson(variables)
    )
}

@Entity(tableName = "workflow_executions")
data class WorkflowExecutionEntity(
    @PrimaryKey val executionId: String,
    val workflowId: String,
    val success: Boolean,
    val message: String,
    val executedActions: String, // JSON array
    val variables: String, // JSON map
    val duration: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toWorkflowExecutionResult(): WorkflowExecutionResult {
        val gson = Gson()
        
        val executedActionsList = try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(executedActions, type) ?: emptyList()
        } catch (e: Exception) { emptyList<String>() }
        
        val variablesMap = try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson<Map<String, String>>(variables, type) ?: emptyMap()
        } catch (e: Exception) { emptyMap<String, String>() }
        
        return WorkflowExecutionResult(
            executionId = executionId,
            workflowId = workflowId,
            success = success,
            message = message,
            executedActions = executedActionsList,
            variables = variablesMap,
            duration = duration,
            timestamp = timestamp
        )
    }
}

fun WorkflowExecutionResult.toEntity(): WorkflowExecutionEntity {
    val gson = Gson()
    
    return WorkflowExecutionEntity(
        executionId = executionId,
        workflowId = workflowId,
        success = success,
        message = message,
        executedActions = gson.toJson(executedActions),
        variables = gson.toJson(variables),
        duration = duration,
        timestamp = timestamp
    )
}

@Entity(tableName = "processed_emails")
data class ProcessedEmailEntity(
    @PrimaryKey val emailId: String,
    val workflowId: String,
    val userId: String,
    val emailFrom: String,
    val emailSubject: String,
    val processedAt: Long = System.currentTimeMillis(),
    val triggerTimestamp: Long // When the email was originally received
)

@Entity(tableName = "processed_telegram_messages")
data class ProcessedTelegramMessageEntity(
    @PrimaryKey val messageId: String, // Combination of chatId_messageId_workflowId for uniqueness
    val telegramMessageId: Long, // Original Telegram message ID
    val chatId: Long,
    val workflowId: String,
    val userId: String,
    val senderName: String,
    val senderUsername: String?,
    val messageText: String,
    val processedAt: Long = System.currentTimeMillis(),
    val triggerTimestamp: Long // When the message was originally sent
)

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val id: String,
    val name: String,
    val gmail: String? = null,
    val telegramId: String? = null,
    val telegramUsername: String? = null,
    val isAutoSaved: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toContact(): Contact {
        return Contact(
            id = id,
            name = name,
            gmail = gmail,
            telegramId = telegramId,
            telegramUsername = telegramUsername,
            isAutoSaved = isAutoSaved,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}

fun Contact.toEntity(): ContactEntity {
    return ContactEntity(
        id = id,
        name = name,
        gmail = gmail,
        telegramId = telegramId,
        telegramUsername = telegramUsername,
        isAutoSaved = isAutoSaved,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}