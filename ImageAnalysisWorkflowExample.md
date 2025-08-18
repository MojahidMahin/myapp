# Image Analysis Workflow Examples

This document demonstrates how to use the new image analysis features in the workflow manager.

## Example 1: Basic Image Analysis Trigger

```kotlin
val basicImageAnalysis = MultiUserTrigger.ImageAnalysisTrigger(
    userId = "user123",
    triggerName = "Analyze uploaded photos",
    imageAttachments = listOf(
        ImageAttachment(
            fileName = "document.jpg",
            filePath = "/storage/emulated/0/Pictures/document.jpg",
            analysisQuestions = listOf("What text is in this image?", "Is this a document?")
        )
    ),
    analysisType = ImageAnalysisType.COMPREHENSIVE,
    analysisKeywords = listOf("receipt", "invoice", "contract", "document"),
    triggerOnKeywordMatch = true,
    minimumConfidence = 0.7f,
    enableOCR = true,
    enableObjectDetection = true,
    enablePeopleDetection = false
)
```

## Example 2: Single Image Analysis Action

```kotlin
val analyzeImageAction = MultiUserAction.AIImageAnalysisAction(
    imageSource = ImageSource.TriggerImageSource(index = 0),
    analysisType = ImageAnalysisType.COMPREHENSIVE,
    analysisQuestions = listOf(
        "Describe what you see in this image",
        "How many people are in the image?",
        "What objects can you identify?"
    ),
    enableOCR = true,
    enableObjectDetection = true,
    enablePeopleDetection = true,
    outputVariable = "image_analysis_result",
    saveAnalysisToFile = true,
    includeVisualDescription = true
)
```

## Example 3: Batch Image Analysis

```kotlin
val batchAnalysisAction = MultiUserAction.AIBatchImageAnalysisAction(
    imageSources = listOf(
        ImageSource.TriggerImageSource(0),
        ImageSource.TriggerImageSource(1),
        ImageSource.TriggerImageSource(2)
    ),
    analysisType = ImageAnalysisType.OBJECT_DETECTION,
    analysisQuestions = listOf("What objects are visible?"),
    enableOCR = false,
    enableObjectDetection = true,
    enablePeopleDetection = true,
    outputVariable = "batch_results",
    combineResults = true,
    saveIndividualAnalyses = false,
    parallelProcessing = true
)
```

## Example 4: Image Comparison

```kotlin
val compareImagesAction = MultiUserAction.AIImageComparisonAction(
    primaryImageSource = ImageSource.TriggerImageSource(0),
    comparisonImageSources = listOf(
        ImageSource.TriggerImageSource(1),
        ImageSource.FilePathSource("/storage/emulated/0/reference.jpg")
    ),
    comparisonType = ImageComparisonType.COMPREHENSIVE,
    outputVariable = "comparison_report",
    includeDetailedDifferences = true
)
```

## Example 5: Complete Workflow

```kotlin
val imageProcessingWorkflow = MultiUserWorkflow(
    id = "img_workflow_001",
    name = "Document Analysis Workflow",
    description = "Analyze uploaded documents and extract information",
    createdBy = "user123",
    trigger = MultiUserTrigger.ImageAnalysisTrigger(
        userId = "user123",
        triggerName = "Document Upload",
        imageAttachments = emptyList(), // Will be populated at runtime
        analysisType = ImageAnalysisType.OCR_ONLY,
        analysisKeywords = listOf("invoice", "receipt", "contract"),
        triggerOnKeywordMatch = true,
        enableOCR = true,
        enableObjectDetection = false,
        enablePeopleDetection = false
    ),
    actions = listOf(
        // Step 1: Analyze the uploaded image
        MultiUserAction.AIImageAnalysisAction(
            imageSource = ImageSource.TriggerImageSource(),
            analysisType = ImageAnalysisType.OCR_ONLY,
            analysisQuestions = listOf(
                "Extract all text from this document",
                "What type of document is this?",
                "Are there any dates or amounts visible?"
            ),
            enableOCR = true,
            outputVariable = "document_analysis"
        ),
        
        // Step 2: Send analysis results via email
        MultiUserAction.SendToUserGmail(
            targetUserId = "user123",
            subject = "Document Analysis Complete",
            body = """
                Document analysis has been completed.
                
                Analysis Results:
                {{document_analysis}}
                
                OCR Text:
                {{document_analysis_ocr}}
                
                Confidence: {{document_analysis_confidence}}%
            """.trimIndent()
        ),
        
        // Step 3: Send summary to Telegram
        MultiUserAction.SendToUserTelegram(
            targetUserId = "user123",
            text = "📄 Document processed! Found {{document_analysis_objects}} objects with {{document_analysis_confidence}}% confidence."
        )
    ),
    isEnabled = true,
    runInBackground = true
)
```

## Available Output Variables

When using image analysis actions, the following variables are automatically created:

### Single Image Analysis (`AIImageAnalysisAction`)
- `{outputVariable}` - Main analysis description
- `{outputVariable}_ocr` - Extracted OCR text
- `{outputVariable}_people_count` - Number of people detected
- `{outputVariable}_objects` - Comma-separated list of detected objects
- `{outputVariable}_colors` - Dominant colors found
- `{outputVariable}_confidence` - Analysis confidence score (0-100)
- `{outputVariable}_file_path` - Path to saved analysis file (if enabled)

### Batch Analysis (`AIBatchImageAnalysisAction`)
- `{outputVariable}` - Combined analysis results or individual results
- `{outputVariable}_total_count` - Total number of images processed
- `{outputVariable}_success_count` - Number of successful analyses
- `{outputVariable}_total_people` - Total people count across all images
- `{outputVariable}_{index}_description` - Individual image descriptions (if enabled)
- `{outputVariable}_{index}_ocr` - Individual OCR results (if enabled)
- `{outputVariable}_{index}_people` - Individual people counts (if enabled)
- `{outputVariable}_{index}_objects` - Individual object lists (if enabled)

### Image Comparison (`AIImageComparisonAction`)
- `{outputVariable}` - Comprehensive comparison report
- `{outputVariable}_comparisons_count` - Number of successful comparisons

## Image Source Types

The system supports multiple ways to specify image sources:

1. **Trigger Images**: `ImageSource.TriggerImageSource(index = 0)`
2. **File Paths**: `ImageSource.FilePathSource("/path/to/image.jpg")`
3. **URIs**: `ImageSource.UriSource("content://media/external/images/123")`
4. **Variables**: `ImageSource.VariableSource("image_path_variable")`
5. **Email Attachments**: `ImageSource.EmailAttachmentSource("email_id", 0)`
6. **Telegram Photos**: `ImageSource.TelegramPhotoSource("message_id", 0)`

## Analysis Types

- **COMPREHENSIVE**: Full analysis including OCR, objects, people, and visual elements
- **OCR_ONLY**: Extract text only
- **OBJECT_DETECTION**: Detect objects and items in the image
- **PEOPLE_DETECTION**: Count and analyze people in the image
- **QUICK_SCAN**: Fast basic analysis
- **CUSTOM**: Custom analysis based on specific parameters

## Image Comparison Types

- **VISUAL_SIMILARITY**: Compare visual similarity and colors
- **OBJECT_DIFFERENCES**: Compare detected objects
- **TEXT_DIFFERENCES**: Compare OCR text content
- **COLOR_DIFFERENCES**: Compare color schemes
- **STRUCTURAL_DIFFERENCES**: Compare composition and structure
- **COMPREHENSIVE**: All comparison types

## Best Practices

1. **Use appropriate analysis types** for your use case to improve performance
2. **Set reasonable confidence thresholds** (0.5-0.8 typically work well)
3. **Combine keyword matching** with confidence scores for better filtering
4. **Use batch processing** for multiple related images
5. **Save analysis to files** for detailed results that need persistence
6. **Include specific questions** to get more targeted analysis results
7. **Test with sample images** before deploying workflows