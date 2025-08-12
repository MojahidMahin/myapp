package com.localllm.myapplication.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localllm.myapplication.data.*
import com.localllm.myapplication.di.AppContainer
import com.localllm.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity for monitoring workflow execution in real-time
 * Provides debugging and performance insights
 */
class WorkflowMonitorActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MyApplicationTheme {
                WorkflowMonitorScreen(
                    onBackPressed = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowMonitorScreen(
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val workflowRepository = remember { AppContainer.provideWorkflowRepository() }
    val userManager = remember { AppContainer.provideUserManager(context) }
    
    var selectedTab by remember { mutableStateOf(0) }
    var executionLogs by remember { mutableStateOf<List<WorkflowExecutionLog>>(emptyList()) }
    var activeWorkflows by remember { mutableStateOf<List<Workflow>>(emptyList()) }
    var performanceMetrics by remember { mutableStateOf<WorkflowPerformanceMetrics?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    
    // Auto-refresh data
    LaunchedEffect(Unit) {
        while (true) {
            loadMonitoringData(
                workflowRepository = workflowRepository,
                userManager = userManager,
                onLogsLoaded = { executionLogs = it },
                onActiveWorkflowsLoaded = { activeWorkflows = it },
                onMetricsLoaded = { performanceMetrics = it }
            )
            delay(5000) // Refresh every 5 seconds
        }
    }
    
    val tabs = listOf("Execution Logs", "Active Workflows", "Performance", "Debug")
    
    Surface(modifier = Modifier.fillMaxSize()) {
        Column {
            // Top App Bar
            TopAppBar(
                title = { Text("Workflow Monitor") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                isRefreshing = true
                                loadMonitoringData(
                                    workflowRepository = workflowRepository,
                                    userManager = userManager,
                                    onLogsLoaded = { executionLogs = it },
                                    onActiveWorkflowsLoaded = { activeWorkflows = it },
                                    onMetricsLoaded = { performanceMetrics = it }
                                )
                                isRefreshing = false
                            }
                        }
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            )
            
            // Tab Row
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        text = { Text(title) },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            when (index) {
                                0 -> Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(16.dp))
                                1 -> Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                2 -> Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                                3 -> Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    )
                }
            }
            
            // Tab Content
            when (selectedTab) {
                0 -> ExecutionLogsTab(executionLogs)
                1 -> ActiveWorkflowsTab(activeWorkflows)
                2 -> PerformanceTab(performanceMetrics)
                3 -> DebugTab(executionLogs.filter { it.status == ExecutionStatus.FAILED })
            }
        }
    }
}

@Composable
fun ExecutionLogsTab(logs: List<WorkflowExecutionLog>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (logs.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Default.Info,
                    title = "No Execution Logs",
                    description = "Workflow execution logs will appear here when workflows are triggered."
                )
            }
        } else {
            items(logs) { log ->
                ExecutionLogCard(log)
            }
        }
    }
}

@Composable
fun ActiveWorkflowsTab(workflows: List<Workflow>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (workflows.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Default.PlayArrow,
                    title = "No Active Workflows",
                    description = "Active workflows will appear here when they are running or scheduled."
                )
            }
        } else {
            items(workflows) { workflow ->
                ActiveWorkflowCard(workflow)
            }
        }
    }
}

@Composable
fun PerformanceTab(metrics: WorkflowPerformanceMetrics?) {
    if (metrics == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            PerformanceOverviewCard(metrics)
        }
        
        item {
            ExecutionTimeChart(metrics.executionTimes)
        }
        
        item {
            SuccessRateCard(metrics.successRate, metrics.totalExecutions)
        }
        
        item {
            TopPerformingWorkflowsCard(metrics.topPerformingWorkflows)
        }
    }
}

@Composable
fun DebugTab(failedLogs: List<WorkflowExecutionLog>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            DebugInfoCard(failedLogs.size)
        }
        
        if (failedLogs.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Default.CheckCircle,
                    title = "No Failed Workflows",
                    description = "Great! All workflows are executing successfully."
                )
            }
        } else {
            items(failedLogs) { log ->
                FailedExecutionCard(log)
            }
        }
    }
}

@Composable
fun ExecutionLogCard(log: WorkflowExecutionLog) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (log.status) {
                ExecutionStatus.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
                ExecutionStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
                ExecutionStatus.RUNNING -> MaterialTheme.colorScheme.secondaryContainer
                ExecutionStatus.PENDING -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = log.workflowName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Triggered by: ${log.triggerType}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Surface(
                    color = when (log.status) {
                        ExecutionStatus.SUCCESS -> Color.Green
                        ExecutionStatus.FAILED -> Color.Red
                        ExecutionStatus.RUNNING -> Color.Blue
                        ExecutionStatus.PENDING -> Color.Gray
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = log.status.name,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
            
            if (log.executionTime > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Execution time: ${log.executionTime}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (log.stepDetails.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Steps completed: ${log.stepDetails.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ActiveWorkflowCard(workflow: Workflow) {
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = workflow.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = workflow.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Surface(
                    color = if (workflow.isEnabled) Color.Green else Color.Gray,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (workflow.isEnabled) "ACTIVE" else "PAUSED",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Last run: ${SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(System.currentTimeMillis()))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Success rate: 95%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PerformanceOverviewCard(metrics: WorkflowPerformanceMetrics) {
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
                text = "Performance Overview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricItem("Total Executions", metrics.totalExecutions.toString())
                MetricItem("Avg. Time", "${metrics.averageExecutionTime}ms")
                MetricItem("Success Rate", "${metrics.successRate}%")
            }
        }
    }
}

@Composable
fun MetricItem(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ExecutionTimeChart(executionTimes: List<Long>) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Execution Times (Last 10)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            if (executionTimes.isNotEmpty()) {
                // Simple bar chart representation
                val maxTime = executionTimes.maxOrNull() ?: 1
                executionTimes.takeLast(10).forEachIndexed { index, time ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}",
                            modifier = Modifier.width(24.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                        LinearProgressIndicator(
                            progress = time.toFloat() / maxTime.toFloat(),
                            modifier = Modifier
                                .weight(1f)
                                .height(8.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${time}ms",
                            modifier = Modifier.width(64.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            } else {
                Text(
                    text = "No execution data available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SuccessRateCard(successRate: Double, totalExecutions: Int) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Success Rate Analysis",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            CircularProgressIndicator(
                progress = (successRate / 100).toFloat(),
                modifier = Modifier
                    .size(80.dp)
                    .align(Alignment.CenterHorizontally),
                strokeWidth = 8.dp,
                color = if (successRate >= 90) Color.Green else if (successRate >= 70) Color.Yellow else Color.Red
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "${successRate}% success rate",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Text(
                text = "Based on $totalExecutions executions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
fun TopPerformingWorkflowsCard(workflows: List<WorkflowPerformance>) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Top Performing Workflows",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            if (workflows.isNotEmpty()) {
                workflows.take(5).forEach { workflow ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = workflow.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${workflow.averageTime}ms",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            } else {
                Text(
                    text = "No performance data available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun DebugInfoCard(failedCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (failedCount > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (failedCount > 0) Icons.Default.Warning else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (failedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = if (failedCount > 0) "Debug Issues Found" else "All Systems Healthy",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (failedCount > 0) "$failedCount failed executions need attention" else "No failed workflows detected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun FailedExecutionCard(log: WorkflowExecutionLog) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = log.workflowName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Error: ${log.errorMessage ?: "Unknown error"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            
            Text(
                text = "Failed at: ${SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            if (log.stepDetails.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Last successful step: ${log.stepDetails.lastOrNull() ?: "None"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Data classes for monitoring
data class WorkflowExecutionLog(
    val id: String,
    val workflowId: String,
    val workflowName: String,
    val status: ExecutionStatus,
    val timestamp: Long,
    val executionTime: Long = 0,
    val triggerType: String,
    val stepDetails: List<String> = emptyList(),
    val errorMessage: String? = null
)

enum class ExecutionStatus {
    PENDING, RUNNING, SUCCESS, FAILED
}

data class WorkflowPerformanceMetrics(
    val totalExecutions: Int,
    val successRate: Double,
    val averageExecutionTime: Long,
    val executionTimes: List<Long>,
    val topPerformingWorkflows: List<WorkflowPerformance>
)

data class WorkflowPerformance(
    val name: String,
    val averageTime: Long,
    val successRate: Double
)

// Mock data loading function (replace with actual implementation)
suspend fun loadMonitoringData(
    workflowRepository: WorkflowRepository,
    userManager: com.localllm.myapplication.service.UserManager,
    onLogsLoaded: (List<WorkflowExecutionLog>) -> Unit,
    onActiveWorkflowsLoaded: (List<Workflow>) -> Unit,
    onMetricsLoaded: (WorkflowPerformanceMetrics) -> Unit
) {
    // Load execution logs (mock data for now)
    val logs = listOf(
        WorkflowExecutionLog(
            id = "1",
            workflowId = "w1",
            workflowName = "Email to Telegram Alert",
            status = ExecutionStatus.SUCCESS,
            timestamp = System.currentTimeMillis() - 30000,
            executionTime = 1250,
            triggerType = "Gmail New Email",
            stepDetails = listOf("Trigger activated", "AI analysis completed", "Telegram sent")
        ),
        WorkflowExecutionLog(
            id = "2",
            workflowId = "w2",
            workflowName = "Daily Summary",
            status = ExecutionStatus.RUNNING,
            timestamp = System.currentTimeMillis() - 5000,
            triggerType = "Scheduled",
            stepDetails = listOf("Trigger activated", "Collecting data")
        ),
        WorkflowExecutionLog(
            id = "3",
            workflowId = "w3",
            workflowName = "AI Content Analysis",
            status = ExecutionStatus.FAILED,
            timestamp = System.currentTimeMillis() - 120000,
            triggerType = "Manual",
            errorMessage = "AI model not available",
            stepDetails = listOf("Trigger activated")
        )
    )
    onLogsLoaded(logs)
    
    // Load active workflows
    val currentUserId = userManager.getCurrentUserId()
    if (currentUserId != null) {
        workflowRepository.getWorkflowsByUser(currentUserId).fold(
            onSuccess = { workflows ->
                onActiveWorkflowsLoaded(workflows.filter { it.isEnabled })
            },
            onFailure = {
                onActiveWorkflowsLoaded(emptyList())
            }
        )
    }
    
    // Load performance metrics (mock data)
    val metrics = WorkflowPerformanceMetrics(
        totalExecutions = 45,
        successRate = 87.5,
        averageExecutionTime = 1350,
        executionTimes = listOf(1200, 1350, 980, 1500, 1100, 1400, 1250, 1600, 900, 1300),
        topPerformingWorkflows = listOf(
            WorkflowPerformance("Email Alerts", 850, 95.0),
            WorkflowPerformance("AI Analysis", 1200, 92.0),
            WorkflowPerformance("Daily Reports", 2100, 88.0)
        )
    )
    onMetricsLoaded(metrics)
}