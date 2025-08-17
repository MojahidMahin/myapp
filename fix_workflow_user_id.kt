import android.content.Context
import kotlinx.coroutines.runBlocking
import com.localllm.myapplication.data.database.AppDatabase
import com.localllm.myapplication.data.database.WorkflowEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.localllm.myapplication.data.MultiUserAction

/**
 * Utility script to fix workflow with incorrect user ID
 * Run this to update workflow "ee" to use correct user ID "user_1" instead of "8104710428"
 */
fun fixWorkflowUserId(context: Context) {
    runBlocking {
        val database = AppDatabase.getDatabase(context)
        val workflowDao = database.workflowDao()
        val gson = Gson()
        
        println("🔍 Searching for workflow with name 'ee'...")
        
        // Get all workflows
        val allWorkflows = workflowDao.getAllWorkflows()
        println("📋 Found ${allWorkflows.size} total workflows")
        
        // Find workflow named "ee"
        val targetWorkflow = allWorkflows.find { it.name == "ee" }
        
        if (targetWorkflow == null) {
            println("❌ No workflow found with name 'ee'")
            return@runBlocking
        }
        
        println("✅ Found workflow 'ee' with ID: ${targetWorkflow.id}")
        
        // Parse actions to find the problematic user ID
        val actionsListType = object : TypeToken<List<MultiUserAction>>() {}.type
        val actions = try {
            gson.fromJson<List<MultiUserAction>>(targetWorkflow.actions, actionsListType) ?: emptyList()
        } catch (e: Exception) {
            println("❌ Failed to parse actions: ${e.message}")
            return@runBlocking
        }
        
        println("📝 Workflow has ${actions.size} actions")
        
        // Check for actions with user ID "8104710428"
        var foundProblematicAction = false
        val updatedActions = actions.map { action ->
            when (action) {
                is MultiUserAction.ForwardGmailToTelegram -> {
                    if (action.targetUserId == "8104710428") {
                        println("🔧 Found ForwardGmailToTelegram action with wrong user ID: ${action.targetUserId}")
                        foundProblematicAction = true
                        action.copy(targetUserId = "user_1")
                    } else {
                        action
                    }
                }
                is MultiUserAction.SendToUserTelegram -> {
                    if (action.targetUserId == "8104710428") {
                        println("🔧 Found SendToUserTelegram action with wrong user ID: ${action.targetUserId}")
                        foundProblematicAction = true
                        action.copy(targetUserId = "user_1")
                    } else {
                        action
                    }
                }
                is MultiUserAction.SendToUserGmail -> {
                    if (action.targetUserId == "8104710428") {
                        println("🔧 Found SendToUserGmail action with wrong user ID: ${action.targetUserId}")
                        foundProblematicAction = true
                        action.copy(targetUserId = "user_1")
                    } else {
                        action
                    }
                }
                else -> action
            }
        }
        
        if (!foundProblematicAction) {
            println("❌ No actions found with user ID '8104710428'")
            
            // Print all actions for debugging
            actions.forEachIndexed { index, action ->
                when (action) {
                    is MultiUserAction.ForwardGmailToTelegram -> {
                        println("Action $index: ForwardGmailToTelegram -> targetUserId: ${action.targetUserId}")
                    }
                    is MultiUserAction.SendToUserTelegram -> {
                        println("Action $index: SendToUserTelegram -> targetUserId: ${action.targetUserId}")
                    }
                    is MultiUserAction.SendToUserGmail -> {
                        println("Action $index: SendToUserGmail -> targetUserId: ${action.targetUserId}")
                    }
                    else -> {
                        println("Action $index: ${action::class.simpleName}")
                    }
                }
            }
            return@runBlocking
        }
        
        // Convert updated actions back to JSON
        val updatedActionsJson = gson.toJson(updatedActions)
        
        // Create updated workflow entity
        val updatedWorkflow = targetWorkflow.copy(
            actions = updatedActionsJson,
            updatedAt = System.currentTimeMillis()
        )
        
        try {
            // Update the workflow in database
            workflowDao.updateWorkflow(updatedWorkflow)
            println("✅ Successfully updated workflow 'ee' - changed user ID from '8104710428' to 'user_1'")
            println("🚀 Your workflow should now execute properly!")
        } catch (e: Exception) {
            println("❌ Failed to update workflow: ${e.message}")
        }
    }
}

// Alternative approach - find all workflows with problematic user IDs
fun findAllProblematicWorkflows(context: Context) {
    runBlocking {
        val database = AppDatabase.getDatabase(context)
        val workflowDao = database.workflowDao()
        val gson = Gson()
        
        println("🔍 Searching for all workflows with user ID '8104710428'...")
        
        val allWorkflows = workflowDao.getAllWorkflows()
        val problematicWorkflows = mutableListOf<WorkflowEntity>()
        
        allWorkflows.forEach { workflow ->
            val actionsListType = object : TypeToken<List<MultiUserAction>>() {}.type
            val actions = try {
                gson.fromJson<List<MultiUserAction>>(workflow.actions, actionsListType) ?: emptyList()
            } catch (e: Exception) {
                return@forEach
            }
            
            val hasProblematicUserId = actions.any { action ->
                when (action) {
                    is MultiUserAction.ForwardGmailToTelegram -> action.targetUserId == "8104710428"
                    is MultiUserAction.SendToUserTelegram -> action.targetUserId == "8104710428"
                    is MultiUserAction.SendToUserGmail -> action.targetUserId == "8104710428"
                    else -> false
                }
            }
            
            if (hasProblematicUserId) {
                problematicWorkflows.add(workflow)
                println("🚨 Found problematic workflow: '${workflow.name}' (ID: ${workflow.id})")
            }
        }
        
        if (problematicWorkflows.isEmpty()) {
            println("✅ No workflows found with user ID '8104710428'")
        } else {
            println("📊 Found ${problematicWorkflows.size} workflow(s) that need fixing")
        }
    }
}