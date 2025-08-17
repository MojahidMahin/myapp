# Email Summarization Troubleshooting Guide

## Quick Diagnosis

### Check Current System Status

Run these commands to quickly assess the email summarization system:

```bash
# Check recent logs for summarization activity
adb logcat -d | grep -E "(SummarizationService|MultiUserWorkflowEngine)" | tail -20

# Check for timeout or error patterns
adb logcat -d | grep -E "(timeout|summarization|fallback|failed)" | tail -10

# Monitor real-time summarization activity
adb logcat | grep -E "(Strategy|fallback|Summary)"
```

### Status Indicators

Look for these log patterns to determine system health:

✅ **Healthy System**:
```
D/SummarizationService: 🚀 Trying AI summarization first
D/SummarizationService: AI summarization successful: Email summary for...
D/MultiUserWorkflowEngine: Email summarized successfully: Email summary...
```

⚠️ **Degraded Performance**:
```
W/SummarizationService: AI summarization failed, falling back: Model not available
I/SummarizationService: Using extractive summarization
D/SummarizationService: ✅ Extractive summary created successfully
```

❌ **System Issues**:
```
E/MultiUserWorkflowEngine: All summarization strategies failed
E/SummarizationService: Error in summarizeText
```

## Common Issues and Solutions

### 1. "Summarization timeout" Errors

**Symptoms**:
```
E/MultiUserWorkflowEngine: Failed to summarize email: Summarization timeout
E/MultiUserWorkflowEngine: Action AIAutoEmailSummarizer failed after 10267ms
```

**Root Cause**: The old implementation had timeout issues that have been resolved.

**Solution**: Ensure you're using the updated implementation with fallback strategies.

**Verification**:
```bash
# Check if the new multi-strategy implementation is active
adb logcat | grep -E "(Strategy|tryAISummarization|createExtractiveSummary)"

# Should see logs like:
# D/SummarizationService: 🚀 Trying AI summarization first
# I/SummarizationService: Using extractive summarization
```

### 2. AI Model Not Available

**Symptoms**:
```
W/SummarizationService: AI summarization failed: LLM model not loaded
W/SummarizationService: Model availability check failed
```

**Root Cause**: MediaPipe LLM model is not properly loaded or initialized.

**Solutions**:

1. **Check Model Status**:
```bash
adb logcat | grep -E "(MediaPipe|ModelManager|model.*load)"
```

2. **Restart Model Service**:
   - Go to app settings
   - Force stop the application
   - Restart and allow model loading to complete

3. **Verify Model Files**:
   - Check if model files are present in app storage
   - Ensure sufficient storage space (models can be 2-4GB)

4. **Check Memory**:
```bash
# Monitor memory usage
adb shell dumpsys meminfo com.localllm.myapplication
```

**Expected Behavior**: System should automatically fall back to extractive summarization.

### 3. Poor Summary Quality

**Symptoms**:
- Summaries are too brief or miss important information
- Extractive summaries don't capture key points
- Emergency fallback summaries are being used frequently

**Diagnostic Steps**:

1. **Check Strategy Usage**:
```bash
# Monitor which strategy is being used
adb logcat | grep -E "(AI summarization successful|extractive|emergency)"
```

2. **Analyze Content Type**:
```bash
# Check email content being processed
adb logcat | grep -E "(email_body|emailContent)" | head -5
```

**Solutions**:

1. **For AI Strategy Issues**:
   - Verify model is properly loaded
   - Check available memory (>2GB recommended)
   - Monitor for timeout patterns

2. **For Extractive Strategy Issues**:
   - Content may be too short for effective extraction
   - Check if important keywords are being detected

3. **For Emergency Fallback Issues**:
   - This indicates both AI and extractive strategies failed
   - Check for malformed input text
   - Verify system resources

### 4. Memory-Related Issues

**Symptoms**:
```
E/SummarizationService: OutOfMemoryError during summarization
W/ModelManager: Model loading failed: Insufficient memory
```

**Diagnostic Commands**:
```bash
# Check current memory usage
adb shell dumpsys meminfo com.localllm.myapplication

# Monitor memory during summarization
adb shell top | grep com.localllm.myapplication
```

**Solutions**:

1. **Immediate Relief**:
   - Close other applications
   - Restart the app to clear memory
   - System will automatically use lighter strategies

2. **Long-term Solutions**:
   - Use smaller model variants if available
   - Implement text chunking for very long emails
   - Configure more aggressive memory management

### 5. Performance Issues

**Symptoms**:
- Summarization takes longer than expected
- UI becomes unresponsive during processing
- Frequent timeouts even with updated implementation

**Diagnostic Steps**:

1. **Monitor Processing Times**:
```bash
adb logcat | grep -E "(duration|completed.*ms|took.*ms)"
```

2. **Check Device Resources**:
```bash
# CPU usage
adb shell top -n 1 | grep com.localllm.myapplication

# Temperature (if device is overheating)
adb shell cat /sys/class/thermal/thermal_zone0/temp
```

**Solutions**:

1. **Optimize Settings**:
   - Reduce max summary length
   - Use CONCISE style instead of DETAILED
   - Enable extractive-first mode for faster processing

2. **System-level Optimizations**:
   - Close background apps
   - Ensure device is not overheating
   - Check for sufficient storage space

### 6. Workflow Integration Issues

**Symptoms**:
```
E/MultiUserWorkflowEngine: Email workflow failed at summarization step
W/MultiUserWorkflowEngine: No email content found in trigger context
```

**Diagnostic Steps**:

1. **Check Email Trigger Data**:
```bash
adb logcat | grep -E "(email_subject|email_body|email_from)"
```

2. **Verify Workflow Context**:
```bash
adb logcat | grep -E "(variables|context|trigger)"
```

**Solutions**:

1. **Missing Email Data**:
   - Verify email trigger is properly configured
   - Check Gmail integration service
   - Ensure email permissions are granted

2. **Context Variable Issues**:
   - Verify variable naming matches between trigger and action
   - Check for proper variable substitution in templates

## Performance Optimization

### Strategy Selection Optimization

**For High-End Devices (>4GB RAM)**:
- Prefer AI summarization
- Increase timeout to 15 seconds
- Use DETAILED summarization style

**For Mid-Range Devices (2-4GB RAM)**:
- Use AI with 10-second timeout
- Fall back to extractive quickly
- Use STRUCTURED style for better readability

**For Low-End Devices (<2GB RAM)**:
- Prefer extractive summarization
- Use AI only for short content
- Use CONCISE style

### Configuration Examples

```kotlin
// High-performance configuration
val config = SummarizationConfig(
    aiTimeoutMs = 15_000,
    preferAI = true,
    maxExtractiveSentences = 5,
    emergencyMaxWords = 100
)

// Battery-optimized configuration  
val config = SummarizationConfig(
    aiTimeoutMs = 5_000,
    preferAI = false,
    maxExtractiveSentences = 3,
    emergencyMaxWords = 50
)
```

## Monitoring and Maintenance

### Health Check Script

Create a simple health monitoring script:

```bash
#!/bin/bash
# email_summarization_health.sh

echo "=== Email Summarization Health Check ==="

# Check for recent activity
echo "Recent summarization activity:"
adb logcat -d | grep -E "(SummarizationService|executeAutoEmailSummarizer)" | tail -5

# Check for errors
echo -e "\nRecent errors:"
adb logcat -d | grep -E "(ERROR.*Summar|failed.*summar)" | tail -5

# Check strategy usage
echo -e "\nStrategy usage (last 10 summaries):"
adb logcat -d | grep -E "(AI summarization|extractive|emergency)" | tail -10

# Check model status
echo -e "\nModel availability:"
adb logcat -d | grep -E "(Model.*available|model.*load)" | tail -3

echo -e "\n=== Health Check Complete ==="
```

### Performance Metrics to Track

1. **Strategy Distribution** (daily):
   ```bash
   # Count strategy usage
   adb logcat -d | grep -c "AI summarization successful"
   adb logcat -d | grep -c "extractive"
   adb logcat -d | grep -c "emergency"
   ```

2. **Average Response Times**:
   ```bash
   # Extract timing information
   adb logcat -d | grep -E "completed.*ms|took.*ms" | tail -20
   ```

3. **Error Rates**:
   ```bash
   # Count failures
   adb logcat -d | grep -c -E "(failed|error|timeout).*summar"
   ```

## Advanced Troubleshooting

### Deep Diagnostic Mode

Enable verbose logging for detailed analysis:

```kotlin
// Add to LocalLLMSummarizationService
private val DEBUG_MODE = BuildConfig.DEBUG

private fun logDetailed(message: String, data: Any? = null) {
    if (DEBUG_MODE) {
        Log.d(TAG, "🔍 $message: $data")
    }
}
```

### Memory Debugging

Monitor memory usage during summarization:

```bash
# Start memory monitoring
adb shell dumpsys meminfo com.localllm.myapplication --package > memory_before.txt

# Trigger summarization
# ... perform email summarization ...

# Check memory after
adb shell dumpsys meminfo com.localllm.myapplication --package > memory_after.txt

# Compare results
diff memory_before.txt memory_after.txt
```

### Network Debugging

If using external model services:

```bash
# Monitor network activity
adb shell nethogs com.localllm.myapplication

# Check for DNS issues
adb shell nslookup your-model-service.com
```

## Recovery Procedures

### Automatic Recovery

The system includes automatic recovery mechanisms:

1. **Strategy Fallback**: Automatically tries next strategy on failure
2. **Model Reloading**: Attempts to reload model on availability check failure
3. **Memory Cleanup**: Triggers garbage collection on memory errors

### Manual Recovery Steps

1. **Soft Reset**:
   ```bash
   # Clear app cache
   adb shell pm clear com.localllm.myapplication
   
   # Restart app
   adb shell am start -n com.localllm.myapplication/.MainActivity
   ```

2. **Model Reset**:
   - Go to app settings → Storage
   - Clear model cache
   - Reload models from scratch

3. **Hard Reset**:
   - Uninstall and reinstall application
   - Reconfigure email integrations
   - Reload models

## Prevention Strategies

### Proactive Monitoring

1. **Set up Log Alerts**:
   ```bash
   # Monitor for error patterns
   adb logcat | grep -E "(ERROR|FATAL).*summar" --line-buffered | while read line; do
       echo "ALERT: $line"
       # Send notification or log to file
   done
   ```

2. **Performance Baselines**:
   - Establish normal response time ranges
   - Monitor memory usage patterns
   - Track strategy distribution trends

### Best Practices

1. **Resource Management**:
   - Monitor device memory before processing large emails
   - Implement graceful degradation for low-resource scenarios
   - Use background processing for non-urgent summarization

2. **User Experience**:
   - Show progress indicators during processing
   - Provide clear feedback about which strategy was used
   - Allow users to retry with different settings

3. **System Integration**:
   - Ensure proper error handling in workflow engine
   - Implement comprehensive logging at all levels
   - Test fallback scenarios regularly

## Contact and Escalation

### Log Collection for Support

When reporting issues, collect these logs:

```bash
# Comprehensive log collection
adb logcat -d > full_logcat.txt
adb shell dumpsys meminfo com.localllm.myapplication > memory_info.txt
adb shell dumpsys package com.localllm.myapplication > package_info.txt

# Filter for relevant logs
grep -E "(SummarizationService|MultiUserWorkflowEngine|ModelManager)" full_logcat.txt > relevant_logs.txt
```

### Issue Escalation Criteria

Escalate if you see:
- ❌ All three strategies failing consistently
- ❌ Memory leaks during summarization
- ❌ Model corruption or loading failures
- ❌ System crashes during email processing

### Self-Service Resolution

Most issues can be resolved by:
1. ✅ Restarting the application
2. ✅ Clearing model cache and reloading
3. ✅ Checking device resources and closing other apps
4. ✅ Verifying email integration permissions

The multi-strategy implementation ensures that email workflows continue to function even when individual components fail, providing a robust and user-friendly experience.