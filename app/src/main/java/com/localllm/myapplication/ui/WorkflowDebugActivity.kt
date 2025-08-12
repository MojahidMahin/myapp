package com.localllm.myapplication.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localllm.myapplication.data.*
import com.localllm.myapplication.di.AppContainer
import com.localllm.myapplication.service.WorkflowTestHelper
import com.localllm.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

/**
 * Debug activity for testing and monitoring workflow execution
 */
class WorkflowDebugActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MyApplicationTheme {
                WorkflowDebugScreen(
                    onBackPressed = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowDebugScreen(
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // State management
    var selectedTab by remember { mutableStateOf(0) }
    var isRunningTests by remember { mutableStateOf(false) }
    var testResults by remember { mutableStateOf("") }
    var workflowExecutions by remember { mutableStateOf<List<WorkflowExecutionResult>>(emptyList()) }
    var debugLogs by remember { mutableStateOf<List<String>>(emptyList()) }
    
    // Services
    val testHelper = remember { WorkflowTestHelper(context) }
    val workflowRepository = remember { AppContainer.provideWorkflowRepository(context) }
    val executionRepository = remember { AppContainer.provideExecutionRepository(context) }
    val modelManager = remember { AppContainer.provideModelManager(context) }
    
    // Load execution history
    LaunchedEffect(Unit) {
        val result = executionRepository.getAllExecutions(50)
        result.onSuccess { executions ->
            workflowExecutions = executions
        }
    }
    
    Surface(modifier = Modifier.fillMaxSize()) {
        Column {
            // Top App Bar
            TopAppBar(
                title = { Text("Workflow Debug Center") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
            
            // Tab Row
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                ) {
                    Text(
                        "Tests",
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                ) {
                    Text(
                        "Executions",
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                ) {
                    Text(
                        "System Info",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            
            // Tab Content
            when (selectedTab) {
                0 -> TestsTab(
                    testHelper = testHelper,
                    isRunningTests = isRunningTests,
                    testResults = testResults,
                    onRunTests = { 
                        isRunningTests = true
                        scope.launch {
                            testResults = testHelper.runAllTests().getOrElse { "Test failed: ${it.message}" }
                            isRunningTests = false
                        }
                    }
                )
                1 -> ExecutionsTab(
                    executions = workflowExecutions,
                    onRefresh = {
                        scope.launch {
                            val result = executionRepository.getAllExecutions(50)
                            result.onSuccess { executions ->
                                workflowExecutions = executions
                            }
                        }
                    }
                )
                2 -> SystemInfoTab(
                    modelManager = modelManager,
                    workflowRepository = workflowRepository
                )
            }
        }
    }
}

@Composable
private fun TestsTab(
    testHelper: WorkflowTestHelper,
    isRunningTests: Boolean,
    testResults: String,
    onRunTests: () -> Unit
) {
    val scope = rememberCoroutineScope()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Test Controls
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "Workflow Tests",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    "Run comprehensive tests to verify workflow functionality",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onRunTests,
                        enabled = !isRunningTests
                    ) {
                        if (isRunningTests) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Run All Tests")
                    }
                    
                    Button(
                        onClick = {
                            scope.launch {
                                testHelper.testBasicWorkflow()
                            }
                        },
                        enabled = !isRunningTests
                    ) {
                        Text("Basic Test")
                    }
                    
                    Button(
                        onClick = {
                            scope.launch {
                                testHelper.testAIWorkflow()
                            }
                        },
                        enabled = !isRunningTests
                    ) {
                        Text("AI Test")
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Test Results
        if (testResults.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "Test Results",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        testResults,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ExecutionsTab(
    executions: List<WorkflowExecutionResult>,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header with refresh button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Execution History (${executions.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }
        
        // Executions List
        if (executions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No workflow executions found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        "Run a test to see execution history",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(executions) { execution ->
                    ExecutionCard(execution = execution)
                }
            }
        }
    }
}

@Composable
private fun ExecutionCard(execution: WorkflowExecutionResult) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    execution.workflowId,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                
                // Success/Failure indicator
                Surface(
                    color = if (execution.success) Color.Green.copy(alpha = 0.2f) else Color.Red.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        if (execution.success) "SUCCESS" else "FAILED",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (execution.success) Color.Green else Color.Red
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                "Execution ID: ${execution.executionId}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
            
            Text(
                "Duration: ${execution.duration}ms",
                style = MaterialTheme.typography.bodySmall
            )
            
            if (execution.message.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    execution.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (execution.success) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error
                )
            }
            
            if (execution.executedActions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Actions: ${execution.executedActions.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun SystemInfoTab(
    modelManager: com.localllm.myapplication.service.ModelManager,
    workflowRepository: WorkflowRepository
) {
    val scope = rememberCoroutineScope()
    var workflows by remember { mutableStateOf<List<Workflow>>(emptyList()) }
    
    LaunchedEffect(Unit) {
        val result = workflowRepository.getAllWorkflows()
        result.onSuccess { workflowList ->
            workflows = workflowList
        }
    }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            InfoCard(
                title = "AI Model Status",
                content = listOf(
                    "Model Loaded" to (if (modelManager.isModelLoaded.value) "✅ Yes" else "❌ No"),
                    "Model Loading" to (if (modelManager.isModelLoading.value) "🔄 Yes" else "❌ No"),
                    "Load Error" to (modelManager.modelLoadError.value ?: "None")
                )
            )
        }
        
        item {
            InfoCard(
                title = "Workflow System",
                content = listOf(
                    "Total Workflows" to workflows.size.toString(),
                    "Personal Workflows" to workflows.count { it.workflowType == WorkflowType.PERSONAL }.toString(),
                    "Team Workflows" to workflows.count { it.workflowType == WorkflowType.TEAM }.toString(),
                    "Public Workflows" to workflows.count { it.workflowType == WorkflowType.PUBLIC }.toString(),
                    "Database Type" to "Room SQLite"
                )
            )
        }
        
        item {
            InfoCard(
                title = "System Memory",
                content = listOf(
                    "Available Memory" to "${Runtime.getRuntime().freeMemory() / (1024 * 1024)} MB",
                    "Total Memory" to "${Runtime.getRuntime().totalMemory() / (1024 * 1024)} MB",
                    "Max Memory" to "${Runtime.getRuntime().maxMemory() / (1024 * 1024)} MB"
                )
            )
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    content: List<Pair<String, String>>
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            content.forEach { (key, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        key,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}