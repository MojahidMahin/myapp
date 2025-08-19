package com.localllm.myapplication.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localllm.myapplication.data.*

/**
 * Composable for creating time-based image analysis triggers
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeBasedImageAnalysisTriggerComposer(
    onTriggerCreated: (MultiUserTrigger.TimeBasedImageAnalysisTrigger) -> Unit,
    modifier: Modifier = Modifier
) {
    
    // ONLY time and schedule type - nothing else
    var scheduleType by remember { mutableStateOf(TimeScheduleType.DAILY) }
    var selectedHour by remember { mutableStateOf(9) }
    var selectedMinute by remember { mutableStateOf(30) }
    var selectedDays by remember { mutableStateOf(DayOfWeek.values().toList()) }
    
    // Time picker state
    var showTimePicker by remember { mutableStateOf(false) }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "⏰ Schedule",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            // Time Scheduling Configuration
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Schedule Configuration",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Schedule Type Selection
                    Text(
                        text = "Schedule Type",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    TimeScheduleType.values().forEach { type ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { scheduleType = type }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = scheduleType == type,
                                onClick = { scheduleType = type }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = getScheduleTypeDisplayName(type),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = getScheduleTypeDescription(type),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Time Selection
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "Trigger Time",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Current time display
                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = String.format("%02d:%02d", selectedHour, selectedMinute),
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            text = if (selectedHour < 12) "AM" else "PM",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                                
                                // Time picker button
                                Button(
                                    onClick = { showTimePicker = true },
                                    modifier = Modifier.height(56.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Set Time")
                                }
                            }
                            
                            // Days of week selection for weekly schedule
                            if (scheduleType == TimeScheduleType.WEEKLY || scheduleType == TimeScheduleType.DAILY) {
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Text(
                                    text = "Days of Week",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Days selection grid
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    DayOfWeek.values().forEach { day ->
                                        val isSelected = selectedDays.contains(day)
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                selectedDays = if (isSelected) {
                                                    selectedDays - day
                                                } else {
                                                    selectedDays + day
                                                }
                                            },
                                            label = {
                                                Text(
                                                    text = day.name.take(3),
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                            
                        }
                    }
                }
            }
            
            
            // Create Trigger Button
            Button(
                onClick = {
                    val timeSchedule = ImageAnalysisTimeSchedule(
                        scheduleType = scheduleType,
                        timeOfDay = String.format("%02d:%02d", selectedHour, selectedMinute),
                        daysOfWeek = selectedDays,
                        isRecurring = true
                    )
                    
                    val trigger = MultiUserTrigger.TimeBasedImageAnalysisTrigger(
                        userId = "current_user",
                        triggerName = "Schedule ${String.format("%02d:%02d", selectedHour, selectedMinute)}",
                        timeSchedule = timeSchedule,
                        imageAttachments = emptyList(),
                        analysisQuestions = emptyList(),
                        analysisKeywords = emptyList(),
                        analysisType = ImageAnalysisType.COMPREHENSIVE,
                        triggerOnKeywordMatch = false,
                        minimumConfidence = 0.5f,
                        enableOCR = true,
                        enableObjectDetection = true,
                        enablePeopleDetection = true,
                        timezone = "UTC"
                    )
                    
                    onTriggerCreated(trigger)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create Schedule")
            }
        }
    }
    
    // Time Picker Dialog
    if (showTimePicker) {
        TimePickerDialog(
            initialHour = selectedHour,
            initialMinute = selectedMinute,
            onTimeSelected = { hour, minute ->
                selectedHour = hour
                selectedMinute = minute
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onTimeSelected: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Time") },
        text = {
            TimePicker(
                state = timePickerState,
                modifier = Modifier.padding(16.dp)
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onTimeSelected(timePickerState.hour, timePickerState.minute)
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun getScheduleTypeDisplayName(type: TimeScheduleType): String {
    return when (type) {
        TimeScheduleType.DAILY -> "Daily"
        TimeScheduleType.WEEKLY -> "Weekly"
        TimeScheduleType.ONCE -> "One Time"
        TimeScheduleType.INTERVAL -> "Interval"
    }
}

private fun getScheduleTypeDescription(type: TimeScheduleType): String {
    return when (type) {
        TimeScheduleType.DAILY -> "Execute every day at the specified time"
        TimeScheduleType.WEEKLY -> "Execute on selected days of the week"
        TimeScheduleType.ONCE -> "Execute only once at the specified time"
        TimeScheduleType.INTERVAL -> "Execute at regular intervals"
    }
}

private fun generateSchedulePreview(
    scheduleType: TimeScheduleType,
    hour: Int,
    minute: Int,
    selectedDays: List<DayOfWeek>,
    isRecurring: Boolean
): String {
    val timeStr = String.format("%02d:%02d", hour, minute)
    val period = if (hour < 12) "AM" else "PM"
    val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
    val timeDisplay = String.format("%d:%02d %s", displayHour, minute, period)
    
    return when (scheduleType) {
        TimeScheduleType.DAILY -> {
            if (isRecurring) {
                "Triggers every day at $timeDisplay"
            } else {
                "Triggers once tomorrow at $timeDisplay"
            }
        }
        TimeScheduleType.WEEKLY -> {
            val daysStr = selectedDays.joinToString(", ") { it.name.take(3) }
            if (isRecurring) {
                "Triggers every $daysStr at $timeDisplay"
            } else {
                "Triggers once on the next occurrence of $daysStr at $timeDisplay"
            }
        }
        TimeScheduleType.ONCE -> {
            "Triggers once at $timeDisplay"
        }
        TimeScheduleType.INTERVAL -> {
            "Triggers at regular intervals starting at $timeDisplay"
        }
    }
}

