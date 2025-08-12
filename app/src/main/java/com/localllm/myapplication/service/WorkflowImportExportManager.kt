package com.localllm.myapplication.service

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.localllm.myapplication.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages workflow import and export functionality
 */
class WorkflowImportExportManager(
    private val context: Context,
    private val workflowRepository: WorkflowRepository,
    private val userManager: UserManager
) {
    
    companion object {
        private const val TAG = "WorkflowImportExport"
        private const val WORKFLOW_FILE_VERSION = "1.0"
        private const val WORKFLOW_FILE_EXTENSION = ".workflow.json"
    }
    
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .setDateFormat("yyyy-MM-dd HH:mm:ss")
        .create()
    
    data class WorkflowExportData(
        val version: String,
        val exportedAt: Long,
        val exportedBy: String,
        val workflows: List<ExportableWorkflow>
    )
    
    data class ExportableWorkflow(
        val name: String,
        val description: String,
        val workflowType: WorkflowType,
        val triggers: List<MultiUserTrigger>,
        val actions: List<MultiUserAction>,
        val variables: Map<String, String>,
        val isPublic: Boolean,
        val permissions: WorkflowPermissions,
        val metadata: WorkflowMetadata
    )
    
    data class WorkflowMetadata(
        val originalId: String,
        val originalCreatedBy: String,
        val originalCreatedAt: Long,
        val exportedAt: Long
    )
    
    data class ImportResult(
        val success: Boolean,
        val importedCount: Int,
        val skippedCount: Int,
        val errors: List<String>,
        val importedWorkflows: List<String> // workflow IDs
    )
    
    /**
     * Export workflows to JSON format
     */
    suspend fun exportWorkflows(
        workflowIds: List<String>,
        outputUri: Uri,
        exportedBy: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Starting workflow export for ${workflowIds.size} workflows")
            
            val workflows = mutableListOf<ExportableWorkflow>()
            var exportedCount = 0
            
            for (workflowId in workflowIds) {
                val workflow = workflowRepository.getWorkflowById(workflowId).getOrNull()
                if (workflow is MultiUserWorkflow) {
                    workflows.add(
                        ExportableWorkflow(
                            name = workflow.name,
                            description = workflow.description,
                            workflowType = workflow.workflowType,
                            triggers = workflow.triggers,
                            actions = workflow.actions,
                            variables = workflow.variables,
                            isPublic = workflow.isPublic,
                            permissions = workflow.permissions,
                            metadata = WorkflowMetadata(
                                originalId = workflow.id,
                                originalCreatedBy = workflow.createdBy,
                                originalCreatedAt = workflow.createdAt,
                                exportedAt = System.currentTimeMillis()
                            )
                        )
                    )
                    exportedCount++
                } else {
                    Log.w(TAG, "Workflow $workflowId not found or not exportable")
                }
            }
            
            val exportData = WorkflowExportData(
                version = WORKFLOW_FILE_VERSION,
                exportedAt = System.currentTimeMillis(),
                exportedBy = exportedBy,
                workflows = workflows
            )
            
            // Write to file
            val contentResolver = context.contentResolver
            contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                val writer = OutputStreamWriter(outputStream, "UTF-8")
                gson.toJson(exportData, writer)
                writer.flush()
            } ?: return@withContext Result.failure(Exception("Failed to open output stream"))
            
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val exportTime = dateFormat.format(Date())
            
            Log.i(TAG, "Workflow export completed: $exportedCount workflows exported")
            Result.success("Successfully exported $exportedCount workflows at $exportTime")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during workflow export", e)
            Result.failure(Exception("Export failed: ${e.message}"))
        }
    }
    
    /**
     * Export single workflow to JSON string
     */
    suspend fun exportWorkflowToString(workflowId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val workflow = workflowRepository.getWorkflowById(workflowId).getOrNull()
                ?: return@withContext Result.failure(Exception("Workflow not found"))
            
            if (workflow !is MultiUserWorkflow) {
                return@withContext Result.failure(Exception("Workflow is not exportable"))
            }
            
            val exportableWorkflow = ExportableWorkflow(
                name = workflow.name,
                description = workflow.description,
                workflowType = workflow.workflowType,
                triggers = workflow.triggers,
                actions = workflow.actions,
                variables = workflow.variables,
                isPublic = workflow.isPublic,
                permissions = workflow.permissions,
                metadata = WorkflowMetadata(
                    originalId = workflow.id,
                    originalCreatedBy = workflow.createdBy,
                    originalCreatedAt = workflow.createdAt,
                    exportedAt = System.currentTimeMillis()
                )
            )
            
            Result.success(gson.toJson(exportableWorkflow))
            
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting workflow to string", e)
            Result.failure(e)
        }
    }
    
    /**
     * Import workflows from JSON file
     */
    suspend fun importWorkflows(
        inputUri: Uri,
        importingUserId: String,
        overwriteExisting: Boolean = false
    ): Result<ImportResult> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Starting workflow import")
            
            // Read and parse file
            val contentResolver = context.contentResolver
            val importData = contentResolver.openInputStream(inputUri)?.use { inputStream ->
                val reader = InputStreamReader(inputStream, "UTF-8")
                gson.fromJson(reader, WorkflowExportData::class.java)
            } ?: return@withContext Result.failure(Exception("Failed to open input stream"))
            
            // Validate import data
            if (importData.version != WORKFLOW_FILE_VERSION) {
                Log.w(TAG, "Import file version mismatch: ${importData.version} vs $WORKFLOW_FILE_VERSION")
            }
            
            val importedWorkflows = mutableListOf<String>()
            val errors = mutableListOf<String>()
            var skippedCount = 0
            
            // Import each workflow
            for ((index, exportableWorkflow) in importData.workflows.withIndex()) {
                try {
                    val result = importSingleWorkflow(exportableWorkflow, importingUserId, overwriteExisting)
                    result.fold(
                        onSuccess = { workflowId ->
                            importedWorkflows.add(workflowId)
                            Log.d(TAG, "Successfully imported workflow: ${exportableWorkflow.name}")
                        },
                        onFailure = { error ->
                            if (error.message?.contains("already exists") == true && !overwriteExisting) {
                                skippedCount++
                                Log.d(TAG, "Skipped existing workflow: ${exportableWorkflow.name}")
                            } else {
                                errors.add("Workflow ${index + 1} (${exportableWorkflow.name}): ${error.message}")
                                Log.e(TAG, "Failed to import workflow: ${exportableWorkflow.name}", error)
                            }
                        }
                    )
                } catch (e: Exception) {
                    errors.add("Workflow ${index + 1} (${exportableWorkflow.name}): ${e.message}")
                    Log.e(TAG, "Exception importing workflow: ${exportableWorkflow.name}", e)
                }
            }
            
            val importResult = ImportResult(
                success = errors.isEmpty(),
                importedCount = importedWorkflows.size,
                skippedCount = skippedCount,
                errors = errors,
                importedWorkflows = importedWorkflows
            )
            
            Log.i(TAG, "Workflow import completed: ${importResult.importedCount} imported, ${importResult.skippedCount} skipped, ${errors.size} errors")
            Result.success(importResult)
            
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Invalid JSON format in import file", e)
            Result.failure(Exception("Invalid workflow file format"))
        } catch (e: Exception) {
            Log.e(TAG, "Error during workflow import", e)
            Result.failure(Exception("Import failed: ${e.message}"))
        }
    }
    
    /**
     * Import workflow from JSON string
     */
    suspend fun importWorkflowFromString(
        jsonString: String,
        importingUserId: String,
        overwriteExisting: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val exportableWorkflow = gson.fromJson(jsonString, ExportableWorkflow::class.java)
            importSingleWorkflow(exportableWorkflow, importingUserId, overwriteExisting)
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Invalid JSON format in workflow string", e)
            Result.failure(Exception("Invalid workflow format"))
        } catch (e: Exception) {
            Log.e(TAG, "Error importing workflow from string", e)
            Result.failure(e)
        }
    }
    
    /**
     * Import a single workflow
     */
    private suspend fun importSingleWorkflow(
        exportableWorkflow: ExportableWorkflow,
        importingUserId: String,
        overwriteExisting: Boolean
    ): Result<String> {
        return try {
            // Generate new ID for imported workflow
            val newWorkflowId = "imported_${UUID.randomUUID()}"
            
            // Check if workflow with same name already exists
            val existingWorkflows = workflowRepository.getAllWorkflows().getOrNull() ?: emptyList()
            val nameExists = existingWorkflows.any { it.name == exportableWorkflow.name && it.createdBy == importingUserId }
            
            if (nameExists && !overwriteExisting) {
                return Result.failure(Exception("Workflow with name '${exportableWorkflow.name}' already exists"))
            }
            
            // Create new workflow
            val importedWorkflow = MultiUserWorkflow(
                id = newWorkflowId,
                name = if (nameExists && overwriteExisting) "${exportableWorkflow.name} (Imported)" else exportableWorkflow.name,
                description = exportableWorkflow.description + "\n\nImported from: ${exportableWorkflow.metadata.originalCreatedBy} on ${Date(exportableWorkflow.metadata.exportedAt)}",
                createdBy = importingUserId,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                workflowType = exportableWorkflow.workflowType,
                triggers = exportableWorkflow.triggers,
                actions = exportableWorkflow.actions,
                variables = exportableWorkflow.variables,
                isPublic = false, // Imported workflows are private by default
                permissions = WorkflowPermissions(), // Reset permissions for security
                sharedWith = emptyList() // No sharing on import
            )
            
            // Validate workflow before import
            val validator = WorkflowValidator(context)
            val validationResult = validator.validateWorkflow(importedWorkflow, userManager)
            
            if (!validationResult.isValid) {
                val errorMessage = validationResult.errors.joinToString("; ") { it.message }
                return Result.failure(Exception("Workflow validation failed: $errorMessage"))
            }
            
            // Save workflow
            val saveResult = workflowRepository.saveWorkflow(importedWorkflow)
            saveResult.fold(
                onSuccess = {
                    Log.i(TAG, "Workflow imported successfully: ${importedWorkflow.name}")
                    Result.success(newWorkflowId)
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to save imported workflow", error)
                    Result.failure(error)
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error importing single workflow", e)
            Result.failure(e)
        }
    }
    
    /**
     * Generate workflow template for sharing
     */
    suspend fun generateWorkflowTemplate(workflowId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val workflow = workflowRepository.getWorkflowById(workflowId).getOrNull()
                ?: return@withContext Result.failure(Exception("Workflow not found"))
            
            if (workflow !is MultiUserWorkflow) {
                return@withContext Result.failure(Exception("Workflow is not exportable"))
            }
            
            // Create sanitized template
            val template = mapOf(
                "name" to workflow.name,
                "description" to workflow.description,
                "type" to workflow.workflowType.name,
                "triggerTypes" to workflow.triggers.map { it::class.simpleName },
                "actionTypes" to workflow.actions.map { it::class.simpleName },
                "variableCount" to workflow.variables.size,
                "createdAt" to SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(workflow.createdAt))
            )
            
            Result.success(gson.toJson(template))
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating workflow template", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get export summary for workflows
     */
    suspend fun getExportSummary(workflowIds: List<String>): Result<ExportSummary> = withContext(Dispatchers.IO) {
        try {
            var totalWorkflows = 0
            var totalActions = 0
            var totalTriggers = 0
            val workflowTypes = mutableSetOf<WorkflowType>()
            val actionTypes = mutableSetOf<String>()
            val triggerTypes = mutableSetOf<String>()
            
            for (workflowId in workflowIds) {
                val workflow = workflowRepository.getWorkflowById(workflowId).getOrNull()
                if (workflow is MultiUserWorkflow) {
                    totalWorkflows++
                    totalActions += workflow.actions.size
                    totalTriggers += workflow.triggers.size
                    workflowTypes.add(workflow.workflowType)
                    actionTypes.addAll(workflow.actions.map { it::class.simpleName ?: "Unknown" })
                    triggerTypes.addAll(workflow.triggers.map { it::class.simpleName ?: "Unknown" })
                }
            }
            
            val summary = ExportSummary(
                totalWorkflows = totalWorkflows,
                totalActions = totalActions,
                totalTriggers = totalTriggers,
                workflowTypes = workflowTypes.toList(),
                actionTypes = actionTypes.toList(),
                triggerTypes = triggerTypes.toList()
            )
            
            Result.success(summary)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating export summary", e)
            Result.failure(e)
        }
    }
    
    data class ExportSummary(
        val totalWorkflows: Int,
        val totalActions: Int,
        val totalTriggers: Int,
        val workflowTypes: List<WorkflowType>,
        val actionTypes: List<String>,
        val triggerTypes: List<String>
    )
}