# Multimodal LLM Implementation Guide

## Overview

This document provides a comprehensive guide for implementing multimodal Large Language Model (LLM) support using MediaPipe GenAI, enabling both text and image processing capabilities in the LocalLLM application.

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Key Components](#key-components)
3. [Configuration Requirements](#configuration-requirements)
4. [Implementation Details](#implementation-details)
5. [Troubleshooting Guide](#troubleshooting-guide)
6. [Code Examples](#code-examples)
7. [Best Practices](#best-practices)
8. [Performance Optimization](#performance-optimization)

## Architecture Overview

The multimodal LLM implementation consists of several layers:

```
┌─────────────────────────────────────┐
│           UI Layer (Chat)           │
├─────────────────────────────────────┤
│         ChatViewModel               │
├─────────────────────────────────────┤
│         ModelManager                │
├─────────────────────────────────────┤
│       ModelServiceImpl              │
├─────────────────────────────────────┤
│      MediaPipeLLMService            │
├─────────────────────────────────────┤
│    MediaPipe GenAI Framework        │
├─────────────────────────────────────┤
│      Multimodal Model File          │
└─────────────────────────────────────┘
```

### Data Flow

1. **User uploads image** → UI captures Bitmap
2. **ChatViewModel processes** → Converts to model format
3. **ModelManager coordinates** → Manages service calls
4. **ModelServiceImpl executes** → Handles the request
5. **MediaPipeLLMService processes** → Converts Bitmap to MPImage and adds to session
6. **MediaPipe GenAI analyzes** → Processes text + image
7. **Response streams back** → Through the same layers

## Key Components

### 1. MediaPipeLLMService

**File**: `/app/src/main/java/com/localllm/myapplication/service/ai/MediaPipeLLMService.kt`

**Purpose**: Core service handling MediaPipe GenAI integration with multimodal support.

**Key Features**:
- Vision modality configuration
- Image processing and conversion
- Session management with GraphOptions
- Streaming response handling

### 2. ChatScreen

**File**: `/app/src/main/java/com/localllm/myapplication/ui/screen/ChatScreen.kt`

**Purpose**: UI for image upload and chat interaction.

**Key Features**:
- Image picker integration
- Bitmap conversion from URI
- Image preview and management
- Multimodal message display

### 3. ChatViewModel

**File**: `/app/src/main/java/com/localllm/myapplication/ui/viewmodel/ChatViewModel.kt`

**Purpose**: Manages chat state and coordinates with model services.

**Key Features**:
- Image handling in messages
- Multimodal message types
- Response streaming coordination

## Configuration Requirements

### 1. MediaPipe LLM Inference Options

```kotlin
val options = LlmInference.LlmInferenceOptions.builder()
    .setModelPath(modelPath)
    .setMaxTokens(MAX_TOKENS)
    .setPreferredBackend(LlmInference.Backend.GPU)
    .setMaxNumImages(MAX_IMAGE_COUNT) // Enable image support
    .build()
```

### 2. LLM Session with Vision Modality

**CRITICAL**: The key to enabling vision modality is in the session creation:

```kotlin
llmSession = LlmInferenceSession.createFromOptions(
    llmInference,
    LlmInferenceSession.LlmInferenceSessionOptions.builder()
        .setTopK(DEFAULT_TOP_K)
        .setTopP(DEFAULT_TOP_P)
        .setTemperature(DEFAULT_TEMPERATURE)
        .setGraphOptions(
            GraphOptions.builder()
                .setEnableVisionModality(true) // KEY: Enable vision modality
                .build()
        )
        .build()
)
```

### 3. Constants Configuration

```kotlin
companion object {
    private const val TAG = "MediaPipeLLMService"
    private const val MAX_TOKENS = 1024
    private const val DEFAULT_TEMPERATURE = 0.8f
    private const val DEFAULT_TOP_K = 40
    private const val DEFAULT_TOP_P = 0.95f
    private const val MAX_IMAGE_COUNT = 8 // Support up to 8 images
}
```

## Implementation Details

### 1. Image Processing Pipeline

#### Step 1: Image Conversion
```kotlin
// Convert Android Bitmap to MediaPipe MPImage
val mpImage: MPImage = BitmapImageBuilder(image).build()
```

#### Step 2: Adding Images to Session
```kotlin
// Add images to session for multimodal processing
if (images.isNotEmpty()) {
    Log.d(TAG, "📸 Adding ${images.size} image(s) to LLM session")
    for ((index, image) in images.withIndex()) {
        try {
            val mpImage: MPImage = BitmapImageBuilder(image).build()
            llmSession!!.addImage(mpImage)
            Log.d(TAG, "✅ Added image $index to session (${image.width}x${image.height})")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to add image $index to session", e)
        }
    }
}
```

#### Step 3: Text Query Processing
```kotlin
// Add text query to session
if (prompt.trim().isNotEmpty()) {
    llmSession!!.addQueryChunk(prompt)
    Log.d(TAG, "📝 Added query chunk to session")
}
```

#### Step 4: Response Generation
```kotlin
// Generate response with both text and images
llmSession!!.generateResponseAsync { partialResult, done ->
    // Handle streaming response
    partialResult?.let { result ->
        if (result.isNotBlank()) {
            responseBuilder.append(result)
            onPartialResult?.invoke(result)
        }
    }
    
    if (done) {
        val response = responseBuilder.toString().trim()
        continuation.resume(response)
    }
}
```

### 2. Error Handling and Recovery

#### Common Error: "Vision modality is not enabled"
```kotlin
Caused by: java.lang.IllegalStateException: Failed to add image:, %sUNAVAILABLE: Vision modality is not enabled.
```

**Root Cause**: Missing `GraphOptions.setEnableVisionModality(true)` in session creation.

**Solution**: Always include GraphOptions when creating sessions:
```kotlin
.setGraphOptions(
    GraphOptions.builder()
        .setEnableVisionModality(true)
        .build()
)
```

#### Image Processing Errors
```kotlin
try {
    val mpImage: MPImage = BitmapImageBuilder(image).build()
    llmSession!!.addImage(mpImage)
} catch (e: Exception) {
    Log.e(TAG, "Failed to add image to session", e)
    // Continue processing other images
}
```

### 3. Session Management

#### Session Initialization
```kotlin
suspend fun initialize(modelPath: String): Result<Unit> = withContext(Dispatchers.IO) {
    try {
        // Create inference instance
        llmInference = LlmInference.createFromOptions(context, options)
        
        // Create session with vision modality enabled
        llmSession = LlmInferenceSession.createFromOptions(
            llmInference,
            LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(DEFAULT_TOP_K)
                .setTopP(DEFAULT_TOP_P)
                .setTemperature(DEFAULT_TEMPERATURE)
                .setGraphOptions(
                    GraphOptions.builder()
                        .setEnableVisionModality(true)
                        .build()
                )
                .build()
        )
        
        isInitialized = true
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

#### Session Reset
```kotlin
suspend fun resetSession(): Result<Unit> = withContext(Dispatchers.IO) {
    try {
        llmSession?.close()
        
        // Recreate session with same vision modality settings
        llmSession = LlmInferenceSession.createFromOptions(
            llmInference,
            LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(DEFAULT_TOP_K)
                .setTopP(DEFAULT_TOP_P)
                .setTemperature(DEFAULT_TEMPERATURE)
                .setGraphOptions(
                    GraphOptions.builder()
                        .setEnableVisionModality(true)
                        .build()
                )
                .build()
        )
        
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

## Troubleshooting Guide

### 1. Vision Modality Not Enabled

**Symptoms**:
- Error: "Vision modality is not enabled"
- Images not being processed
- Model responds as if no image was provided

**Solutions**:
1. Verify `GraphOptions.setEnableVisionModality(true)` is set in session creation
2. Ensure `setMaxNumImages(MAX_IMAGE_COUNT)` is set in inference options
3. Check that the model file supports multimodal input

### 2. Image Processing Failures

**Symptoms**:
- Images not converting to MPImage format
- Crashes during image addition
- Memory issues with large images

**Solutions**:
1. Resize images before processing:
   ```kotlin
   val resizedBitmap = Bitmap.createScaledBitmap(
       originalBitmap, 
       maxWidth, 
       maxHeight, 
       true
   )
   ```
2. Check image format compatibility
3. Monitor memory usage with large images

### 3. Performance Issues

**Symptoms**:
- Slow response generation
- High memory usage
- GPU/CPU overload

**Solutions**:
1. Optimize image resolution
2. Limit concurrent requests with mutex
3. Use appropriate backend (GPU vs CPU)
4. Implement proper resource cleanup

### 4. Model Compatibility

**Symptoms**:
- Model loads but doesn't process images
- Inconsistent multimodal behavior
- Model-specific errors

**Solutions**:
1. Verify model supports vision modality
2. Check MediaPipe version compatibility
3. Validate model file format
4. Test with known working models (e.g., Gemma3N)

## Code Examples

### 1. Complete Multimodal Chat Implementation

```kotlin
class MultimodalChatExample {
    private val mediaPipeService = MediaPipeLLMService(context)
    
    suspend fun sendMultimodalMessage(
        text: String, 
        images: List<Bitmap>
    ): Result<String> {
        return try {
            mediaPipeService.generateResponse(
                prompt = text,
                images = images,
                onPartialResult = { partialText ->
                    // Handle streaming response
                    updateUI(partialText)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

### 2. Image Preprocessing

```kotlin
fun preprocessImage(bitmap: Bitmap): Bitmap {
    val maxDimension = 1024
    val width = bitmap.width
    val height = bitmap.height
    
    return if (width > maxDimension || height > maxDimension) {
        val ratio = maxDimension.toFloat() / Math.max(width, height)
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()
        
        Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    } else {
        bitmap
    }
}
```

### 3. Error Recovery

```kotlin
suspend fun generateResponseWithRetry(
    prompt: String,
    images: List<Bitmap>,
    maxRetries: Int = 3
): Result<String> {
    repeat(maxRetries) { attempt ->
        try {
            return mediaPipeService.generateResponse(prompt, images)
        } catch (e: Exception) {
            Log.w(TAG, "Attempt ${attempt + 1} failed", e)
            if (attempt == maxRetries - 1) {
                return Result.failure(e)
            }
            // Reset session and retry
            mediaPipeService.resetSession()
        }
    }
    return Result.failure(Exception("Max retries exceeded"))
}
```

## Best Practices

### 1. Resource Management

- **Always close sessions and inference instances**:
  ```kotlin
  override fun onDestroy() {
      mediaPipeService.cleanup()
      super.onDestroy()
  }
  ```

- **Use mutex for thread safety**:
  ```kotlin
  private val sessionMutex = Mutex()
  
  suspend fun generateResponse(...) = sessionMutex.withLock {
      // MediaPipe operations
  }
  ```

### 2. Memory Management

- **Preprocess images to reduce memory footprint**
- **Limit concurrent multimodal requests**
- **Implement proper image cleanup**:
  ```kotlin
  bitmap.recycle() // When done with bitmap
  ```

### 3. User Experience

- **Provide visual feedback during processing**:
  ```kotlin
  // Show loading indicator
  isGeneratingResponse.value = true
  
  // Update with partial results
  onPartialResult = { partial ->
      updateChatMessage(partial)
  }
  ```

- **Handle long processing times gracefully**
- **Implement cancellation support**

### 4. Error Handling

- **Graceful degradation**: Fall back to text-only if image processing fails
- **User-friendly error messages**: Don't expose technical errors to users
- **Comprehensive logging**: For debugging and monitoring

## Performance Optimization

### 1. Image Optimization

```kotlin
// Optimal image settings
private const val MAX_IMAGE_DIMENSION = 1024
private const val JPEG_QUALITY = 85
private const val MAX_IMAGE_COUNT = 4 // Balance between capability and performance
```

### 2. Model Configuration

```kotlin
// Performance-optimized settings
private const val MAX_TOKENS = 1024 // Reasonable limit for mobile
private const val DEFAULT_TEMPERATURE = 0.8f // Good balance
private const val DEFAULT_TOP_K = 40 // Reasonable diversity
```

### 3. Backend Selection

```kotlin
val preferredBackend = when {
    isHighEndDevice() -> LlmInference.Backend.GPU
    hasGPUSupport() -> LlmInference.Backend.GPU
    else -> LlmInference.Backend.CPU
}
```

### 4. Concurrent Request Management

```kotlin
// Limit concurrent requests
private val requestSemaphore = Semaphore(1)

suspend fun generateResponse(...) {
    requestSemaphore.withPermit {
        // Process request
    }
}
```

## Integration Testing

### 1. Unit Tests

```kotlin
@Test
fun testImageConversion() {
    val bitmap = createTestBitmap()
    val mpImage = BitmapImageBuilder(bitmap).build()
    assertNotNull(mpImage)
}

@Test
fun testMultimodalResponse() = runTest {
    val service = MediaPipeLLMService(context)
    val result = service.generateResponse(
        prompt = "Describe this image",
        images = listOf(testBitmap)
    )
    assertTrue(result.isSuccess)
}
```

### 2. Integration Tests

```kotlin
@Test
fun testEndToEndMultimodalChat() = runTest {
    // Test complete flow from UI to model
    val chatViewModel = ChatViewModel(context, modelManager)
    chatViewModel.sendMessage("What's in this image?", testBitmap)
    
    // Verify response contains image analysis
    val messages = chatViewModel.chatSession.value.messages
    assertTrue(messages.last().text.contains("image"))
}
```

## Version Compatibility

### MediaPipe Versions

- **Minimum**: MediaPipe GenAI 0.10.14
- **Recommended**: Latest stable version
- **Key Features**:
  - GraphOptions.setEnableVisionModality() support
  - MPImage format support
  - LlmInferenceSession.addImage() method

### Android Compatibility

- **Minimum SDK**: 21 (Android 5.0)
- **Target SDK**: 34 (Android 14)
- **Recommended**: Android 8.0+ for optimal performance

## Troubleshooting Quick Reference

| Error | Cause | Solution |
|-------|-------|----------|
| "Vision modality is not enabled" | Missing GraphOptions.setEnableVisionModality(true) | Add GraphOptions to session creation |
| "Failed to add image" | Image format/size issues | Preprocess image, check format |
| "Out of memory" | Large images, memory leak | Resize images, proper cleanup |
| "Model not responding to images" | Text-only model | Use multimodal-capable model |
| "Slow performance" | Large images, wrong backend | Optimize images, check GPU support |

## References

- [MediaPipe GenAI Documentation](https://developers.google.com/mediapipe/solutions/genai)
- [Working Gallery App Reference](/run/media/vortex/CSE/CSE 327 NBM/Demo2/gallery)
- [LLM Inference API Reference](https://developers.google.com/mediapipe/api/solutions/java/com/google/mediapipe/tasks/genai/llminference)

---

*This documentation covers the complete implementation of multimodal LLM support in the LocalLLM application. For additional support or questions, refer to the troubleshooting section or examine the working Gallery app implementation.*

**Last Updated**: August 2025  
**Version**: 1.0.0  
**Author**: LocalLLM Development Team