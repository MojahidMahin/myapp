# Time-Based Image Analysis Trigger Demo

## Overview
This document demonstrates how to set up and test the new time-based image analysis trigger that was just implemented.

## Features Implemented

### 1. Data Structures
- **`TimeBasedImageAnalysisTrigger`**: New trigger type for scheduled image analysis
- **`ImageAnalysisTimeSchedule`**: Configuration for time-based scheduling
- **`TimeScheduleType`**: Enum for different schedule types (DAILY, WEEKLY, ONCE, INTERVAL)
- **`DayOfWeek`**: Custom enum for day selection

### 2. UI Components
- **`TimeBasedImageAnalysisTriggerComposer`**: Complete UI for configuring time-based triggers
- **Time Picker Dialog**: Material Design 3 time picker for selecting trigger times
- **Schedule Preview**: Real-time preview of the configured schedule

### 3. Scheduling Logic
- **`isTimeToTrigger()`**: Intelligent time matching with 1-minute window tolerance
- **Daily/Weekly/Once/Interval scheduling**: Support for all common scheduling patterns
- **Duplicate execution prevention**: Prevents multiple executions on the same day
- **Timezone support**: Built-in timezone handling (default UTC)

## How to Use

### Step 1: Create a New Workflow
1. Open **Workflow Manager** → **Create New Workflow**
2. Enter workflow name and description
3. Click "Choose Trigger"

### Step 2: Select Time-Based Image Analysis Trigger
1. In the trigger picker, select **"Image Analysis"** category
2. Choose **"Time-Based Image Analysis"**
3. Configure the trigger:
   - **Trigger Name**: e.g., "Daily morning photo analysis"
   - **Time**: e.g., "22:00" for 10 PM or "09:30" for 9:30 AM
   - **Schedule Type**: Choose DAILY, WEEKLY, ONCE, or INTERVAL
   - **Days of Week**: Select specific days (for WEEKLY type)
   - **Analysis Keywords**: Keywords to look for in images
   - **Minimum Confidence**: Confidence threshold (0.0-1.0)

### Step 3: Add Images
1. Click "Add Images" to attach images for analysis
2. Optionally add specific questions for each image
3. Configure analysis options (OCR, Object Detection, People Detection)

### Step 4: Add Actions
Choose what happens when the trigger fires:
- Send results via email
- Forward to Telegram
- Generate AI summaries
- Create notifications

## Example Use Cases

### 1. Daily Security Check (10 PM)
- **Trigger**: Every day at 22:00
- **Images**: Security camera screenshots
- **Keywords**: "person", "vehicle", "motion"
- **Action**: Send alert email if person detected

### 2. Morning Weather Analysis (9:30 AM)
- **Trigger**: Daily at 09:30
- **Images**: Weather camera images
- **Keywords**: "rain", "snow", "clear", "cloudy"
- **Action**: Send weather summary to Telegram

### 3. Weekly Report Generation (Monday 8 AM)
- **Trigger**: Every Monday at 08:00
- **Images**: Business dashboard screenshots
- **Analysis**: Extract key metrics via OCR
- **Action**: Generate weekly report email

## Technical Features

### Scheduling Accuracy
- **1-minute tolerance window**: Triggers fire within ±1 minute of scheduled time
- **Duplicate prevention**: Won't trigger multiple times in the same day
- **Timezone aware**: Supports different timezone configurations

### Analysis Options
- **Comprehensive Analysis**: Full OCR, object detection, people counting
- **OCR Only**: Text extraction only
- **Object Detection**: Focus on identifying objects
- **Custom**: User-defined analysis parameters

### Smart Triggering
- **Keyword matching**: Only trigger when specific keywords found
- **Confidence thresholds**: Set minimum analysis confidence levels
- **Conditional execution**: Sophisticated filtering logic

## Testing the Implementation

### Test Schedule (Example: 2:30 PM today)
1. Create workflow with time "14:30" (2:30 PM)
2. Set schedule type to "ONCE"
3. Add test images
4. Save workflow
5. Wait for 2:30 PM - trigger should fire within 1 minute

### Monitoring
- Check workflow logs for trigger execution
- Verify analysis results in action outputs
- Monitor execution times and accuracy

### Expected Behavior
```
⏰ === TIME-BASED IMAGE ANALYSIS TRIGGER CHECK START ===
📋 Workflow: Daily morning photo analysis (ID: workflow_123)
👤 User: user_1
🏷️ Trigger Name: Daily morning photo analysis
📅 Schedule Type: DAILY
🕐 Time of Day: 09:30
📷 Image attachments: 2
✅ Time condition met, proceeding with image analysis
🔍 Starting scheduled analysis of 2 image(s)
📷 Analyzing image 1/2: photo1.jpg
✅ Scheduled analysis completed for photo1.jpg
   Confidence: 0.85
   Keywords found: 5
   Keyword matches: 2
   Should trigger: true
🎯 TRIGGERING SCHEDULED WORKFLOW - Time-based image analysis conditions met
   Scheduled time: 09:30
   Analyzed images: 2
   Successful analyses: 2
```

## Architecture Integration

### Workflow Engine
- Integrates with existing `MultiUserWorkflowEngine`
- Uses standard workflow execution pipeline
- Compatible with all existing actions

### Trigger Manager
- Added to `WorkflowTriggerManager.checkTrigger()`
- Runs in background every 30 seconds
- Memory-efficient execution tracking

### Database Compatibility
- Uses existing workflow repository
- Backward compatible with current workflows
- No database schema changes required

## Future Enhancements

### Planned Features
1. **Persistent execution tracking**: Store last execution times in database
2. **Advanced timezone support**: User-specific timezone settings
3. **Cron expression support**: More complex scheduling patterns
4. **Image source monitoring**: Watch folders for new images
5. **Smart retry logic**: Handle failed executions gracefully

### Potential Improvements
1. **Battery optimization**: Reduce frequency during low-battery states
2. **Network awareness**: Delay execution during poor connectivity
3. **User presence detection**: Skip execution when user is away
4. **Dynamic scheduling**: Adjust times based on usage patterns

## Conclusion

The time-based image analysis trigger provides a powerful and flexible way to automate image processing at specific times. With support for various scheduling patterns, intelligent time matching, and comprehensive analysis options, it enables sophisticated automation workflows for a wide range of use cases.

The implementation follows the existing architecture patterns and integrates seamlessly with the current workflow system, making it easy to use and maintain.