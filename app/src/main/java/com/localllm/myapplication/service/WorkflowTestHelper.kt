package com.localllm.myapplication.service

import android.content.Context
import android.util.Log
import com.localllm.myapplication.data.*
import com.localllm.myapplication.di.AppContainer
import kotlinx.coroutines.*

/**
 * Helper class for testing workflow functionality
 */
class WorkflowTestHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "WorkflowTestHelper"
    }
    
    private val userManager = AppContainer.provideUserManager(context)
    private val workflowRepository = AppContainer.provideWorkflowRepository(context)
    private val workflowEngine = AppContainer.provideWorkflowEngine(context)
    
    /**
     * Test basic workflow creation and execution
     */
    suspend fun testBasicWorkflow(): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "=== Starting Basic Workflow Test ===")
            
            // 1. Create a test user
            val testUser = WorkflowUser(
                id = "test-user-123",
                email = "test@example.com",
                displayName = "Test User"
            )
            
            val userResult = userManager.registerOrGetUser(testUser.email, testUser.displayName)
            userResult.fold(
                onSuccess = { user ->
                    Log.d(TAG, "Test user created/retrieved: ${user.email}")
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to create test user", error)
                    return@withContext Result.failure(error)
                }
            )
            
            // 2. Create a simple workflow with log action
            val testWorkflow = MultiUserWorkflow(
                id = "test-workflow-123",
                name = "Test Workflow",
                description = "Simple test workflow with logging",
                createdBy = testUser.id,
                actions = listOf(
                    MultiUserAction.LogAction(
                        message = "Test workflow executed successfully!",
                        level = "INFO"
                    )
                ),
                workflowType = WorkflowType.PERSONAL
            )
            
            // 3. Save workflow
            val saveResult = workflowRepository.saveWorkflow(testWorkflow)
            saveResult.fold(
                onSuccess = {
                    Log.d(TAG, "Test workflow saved successfully")
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to save test workflow", error)
                    return@withContext Result.failure(error)
                }
            )
            
            // 4. Execute workflow
            val executionResult = workflowEngine.executeWorkflow(
                workflowId = testWorkflow.id,
                triggerUserId = testUser.id,
                triggerData = "Manual test trigger"
            )
            
            executionResult.fold(
                onSuccess = { result ->
                    if (result.success) {
                        Log.i(TAG, "=== Workflow Test PASSED ===")
                        Log.i(TAG, "Execution ID: ${result.executionId}")
                        Log.i(TAG, "Duration: ${result.duration}ms")
                        Result.success("Workflow test completed successfully")
                    } else {
                        Log.e(TAG, "=== Workflow Test FAILED ===")
                        Log.e(TAG, "Failure message: ${result.message}")
                        Result.failure(Exception("Workflow execution failed: ${result.message}"))
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "=== Workflow Test FAILED ===", error)
                    Result.failure(error)
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Exception during workflow test", e)
            Result.failure(e)
        }
    }
    
    /**
     * Test AI workflow action (if model is loaded)
     */
    suspend fun testAIWorkflow(): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "=== Starting AI Workflow Test ===")
            
            val modelManager = AppContainer.provideModelManager(context)
            if (!modelManager.isModelLoaded.value) {
                Log.w(TAG, "AI model not loaded, skipping AI workflow test")
                return@withContext Result.success("AI workflow test skipped - no model loaded")
            }
            
            // Create test user if needed
            val testUser = WorkflowUser(
                id = "test-user-ai-123",
                email = "testai@example.com",
                displayName = "Test AI User"
            )
            
            userManager.registerOrGetUser(testUser.email, testUser.displayName)
            
            // Create AI workflow
            val aiWorkflow = MultiUserWorkflow(
                id = "test-ai-workflow-123",
                name = "Test AI Workflow",
                description = "Test workflow with AI analysis",
                createdBy = testUser.id,
                actions = listOf(
                    MultiUserAction.AIAnalyzeText(
                        inputText = "This is a test message for AI analysis.",
                        analysisPrompt = "Analyze this text and provide a brief summary.",
                        outputVariable = "ai_result"
                    ),
                    MultiUserAction.LogAction(
                        message = "AI analysis result: {{ai_result}}",
                        level = "INFO"
                    )
                ),
                workflowType = WorkflowType.PERSONAL
            )
            
            workflowRepository.saveWorkflow(aiWorkflow)
            
            val executionResult = workflowEngine.executeWorkflow(
                workflowId = aiWorkflow.id,
                triggerUserId = testUser.id,
                triggerData = "AI test trigger"
            )
            
            executionResult.fold(
                onSuccess = { result ->
                    if (result.success) {
                        Log.i(TAG, "=== AI Workflow Test PASSED ===")
                        Result.success("AI workflow test completed successfully")
                    } else {
                        Log.e(TAG, "=== AI Workflow Test FAILED ===")
                        Result.failure(Exception("AI workflow failed: ${result.message}"))
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "=== AI Workflow Test FAILED ===", error)
                    Result.failure(error)
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Exception during AI workflow test", e)
            Result.failure(e)
        }
    }
    
    /**
     * Test service connections
     */
    suspend fun testServiceConnections(): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "=== Testing Service Connections ===")
            
            val testUser = WorkflowUser(
                id = "test-service-user-123",
                email = "testservice@example.com",
                displayName = "Test Service User"
            )
            
            val userResult = userManager.registerOrGetUser(testUser.email, testUser.displayName)
            val user = userResult.getOrNull() ?: return@withContext Result.failure(Exception("Failed to create test user"))
            
            // Test Gmail connection (will likely fail without proper OAuth setup)
            Log.d(TAG, "Testing Gmail connection...")
            val gmailResult = userManager.connectGmail(user)
            gmailResult.fold(
                onSuccess = {
                    Log.i(TAG, "Gmail connection test PASSED")
                },
                onFailure = { error ->
                    Log.w(TAG, "Gmail connection test failed (expected): ${error.message}")
                }
            )
            
            // Test connection status
            val connectionStatus = userManager.getConnectionStatus(user)
            Log.d(TAG, "Connection Status - Gmail: ${connectionStatus.gmail}, Telegram: ${connectionStatus.telegram}")
            
            Result.success("Service connection tests completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Exception during service connection test", e)
            Result.failure(e)
        }
    }
    
    /**
     * Run all workflow tests
     */
    suspend fun runAllTests(): Result<String> {
        Log.i(TAG, "=== Running All Workflow Tests ===")
        
        val results = mutableListOf<String>()
        
        // Test 1: Basic workflow
        testBasicWorkflow().fold(
            onSuccess = { message ->
                results.add("✅ Basic Workflow: $message")
            },
            onFailure = { error ->
                results.add("❌ Basic Workflow: ${error.message}")
            }
        )
        
        // Test 2: AI workflow
        testAIWorkflow().fold(
            onSuccess = { message ->
                results.add("✅ AI Workflow: $message")
            },
            onFailure = { error ->
                results.add("❌ AI Workflow: ${error.message}")
            }
        )
        
        // Test 3: Service connections
        testServiceConnections().fold(
            onSuccess = { message ->
                results.add("✅ Service Connections: $message")
            },
            onFailure = { error ->
                results.add("❌ Service Connections: ${error.message}")
            }
        )
        
        val summary = results.joinToString("\n")
        Log.i(TAG, "=== Test Results ===\n$summary")
        
        return Result.success(summary)
    }
}