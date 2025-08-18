# Image Analysis Workflows Documentation

## Overview

The Image Analysis Workflow feature allows you to create automated workflows that trigger based on image content analysis. This powerful feature combines computer vision, OCR (Optical Character Recognition), and AI analysis to process images and execute actions based on what's found.

## Table of Contents

1. [Quick Start](#quick-start)
2. [Trigger Types](#trigger-types)
3. [Action Types](#action-types)
4. [Configuration Guide](#configuration-guide)
5. [Workflow Examples](#workflow-examples)
6. [Best Practices](#best-practices)
7. [Troubleshooting](#troubleshooting)
8. [API Reference](#api-reference)

## Quick Start

### Creating Your First Image Analysis Workflow

1. **Open WorkflowManager**
   - Navigate to the main menu
   - Select "WorkflowManager"
   - Click "Create Dynamic Workflow"

2. **Choose Image Analysis Trigger**
   - Click "Choose Trigger"
   - Select the "Image Analysis" tab
   - Choose either "Image Analysis Trigger" or "Auto Image Analysis"

3. **Configure the Trigger**
   - Set a descriptive trigger name
   - Add keywords to look for (comma-separated)
   - Set minimum confidence level (0.0-1.0)
   - Enable/disable OCR and object detection

4. **Add Actions**
   - Click "Add Action"
   - Choose from image analysis actions or any other available actions
   - Configure action parameters

5. **Save and Test**
   - Give your workflow a name and description
   - Save the workflow
   - Test with sample images

## Trigger Types

### 1. Image Analysis Trigger 📸

**Description**: Analyzes uploaded images for specific content and triggers when criteria are met.

**Use Cases**:
- Document processing (receipts, invoices, contracts)
- Security monitoring (person detection)
- Content moderation
- Inventory management (product recognition)

**Configuration Options**:
```kotlin
ImageAnalysisTrigger(
    triggerName = "Document Scanner",
    analysisKeywords = ["receipt", "invoice", "total", "amount"],
    triggerOnKeywordMatch = true,
    minimumConfidence = 0.7f,
    enableOCR = true,
    enableObjectDetection = true,
    enablePeopleDetection = false
)
```

### 2. Auto Image Analysis Trigger 🤖

**Description**: Automatically monitors a directory or source for new images and analyzes them.

**Use Cases**:
- Camera roll monitoring
- Automatic document processing
- Real-time image classification
- Batch processing of images

**Configuration Options**:
```kotlin
AutoImageAnalysisTrigger(
    triggerName = "Auto Document Processor",
    sourceDirectory = "/storage/emulated/0/Pictures/Documents",
    analysisKeywords = ["document", "text"],
    enableOCR = true,
    enableObjectDetection = false
)
```

## Action Types

### 1. AI Image Analysis Action

**Purpose**: Performs comprehensive analysis on a single image.

**Features**:
- Visual description generation
- OCR text extraction
- Object detection
- People counting
- Color analysis
- Custom question answering

**Configuration**:
```kotlin
AIImageAnalysisAction(
    imageSource = ImageSource.TriggerImageSource(index = 0),
    analysisType = ImageAnalysisType.COMPREHENSIVE,
    analysisQuestions = [
        "What type of document is this?",
        "Extract any dates or amounts",
        "How many people are visible?"
    ],
    enableOCR = true,
    enableObjectDetection = true,
    enablePeopleDetection = true,
    outputVariable = "analysis_result",
    saveAnalysisToFile = true
)
```

### 2. AI Batch Image Analysis Action

**Purpose**: Analyzes multiple images in a single operation.

**Features**:
- Process multiple images simultaneously
- Combine results or keep separate
- Parallel processing support
- Aggregate statistics

**Configuration**:
```kotlin
AIBatchImageAnalysisAction(
    imageSources = [
        ImageSource.TriggerImageSource(0),
        ImageSource.TriggerImageSource(1),
        ImageSource.FilePathSource("/path/to/image.jpg")
    ],
    analysisType = ImageAnalysisType.OBJECT_DETECTION,
    combineResults = true,
    parallelProcessing = true,
    outputVariable = "batch_results"
)
```

### 3. AI Image Comparison Action

**Purpose**: Compares images to detect differences or similarities.

**Features**:
- Visual similarity comparison
- Object difference detection
- Text content comparison
- Structural analysis

**Configuration**:
```kotlin
AIImageComparisonAction(
    primaryImageSource = ImageSource.TriggerImageSource(0),
    comparisonImageSources = [
        ImageSource.TriggerImageSource(1),
        ImageSource.FilePathSource("/reference.jpg")
    ],
    comparisonType = ImageComparisonType.COMPREHENSIVE,
    includeDetailedDifferences = true,
    outputVariable = "comparison_report"
)
```

## Configuration Guide

### Analysis Types

| Type | Description | Use Case |
|------|-------------|----------|
| `COMPREHENSIVE` | Full analysis with OCR, objects, people, and visual elements | General purpose analysis |
| `OCR_ONLY` | Extract text only | Document processing |
| `OBJECT_DETECTION` | Detect objects and items | Inventory, security |
| `PEOPLE_DETECTION` | Count and analyze people | Security, events |
| `QUICK_SCAN` | Fast basic analysis | Real-time processing |
| `CUSTOM` | Custom analysis based on parameters | Specialized needs |

### Image Sources

| Source Type | Description | Example |
|-------------|-------------|---------|
| `TriggerImageSource` | Images from trigger | `ImageSource.TriggerImageSource(0)` |
| `FilePathSource` | Direct file path | `ImageSource.FilePathSource("/path/image.jpg")` |
| `UriSource` | Content URI | `ImageSource.UriSource("content://...")` |
| `VariableSource` | Path from workflow variable | `ImageSource.VariableSource("image_path")` |
| `EmailAttachmentSource` | Image from email | `ImageSource.EmailAttachmentSource("email_id", 0)` |
| `TelegramPhotoSource` | Image from Telegram | `ImageSource.TelegramPhotoSource("msg_id", 0)` |

### Confidence Levels

- **0.3-0.5**: Very permissive, catches most content but may have false positives
- **0.5-0.7**: Balanced approach, good for general use
- **0.7-0.9**: High confidence, fewer false positives
- **0.9-1.0**: Very strict, only very clear matches

## Workflow Examples

### Example 1: Receipt Processing Workflow

```kotlin
val receiptProcessor = MultiUserWorkflow(
    name = "Receipt Processor",
    description = "Automatically process receipt images and extract information",
    trigger = MultiUserTrigger.ImageAnalysisTrigger(
        userId = "user123",
        triggerName = "Receipt Upload",
        analysisKeywords = ["receipt", "total", "tax", "subtotal"],
        triggerOnKeywordMatch = true,
        minimumConfidence = 0.6f,
        enableOCR = true
    ),
    actions = [
        // Extract receipt data
        MultiUserAction.AIImageAnalysisAction(
            imageSource = ImageSource.TriggerImageSource(),
            analysisQuestions = [
                "What is the total amount?",
                "What is the vendor name?",
                "What is the date of purchase?",
                "List all items purchased"
            ],
            enableOCR = true,
            outputVariable = "receipt_data"
        ),
        
        // Email the results
        MultiUserAction.SendToUserGmail(
            targetUserId = "user123",
            subject = "Receipt Processed: {{receipt_data_vendor}}",
            body = """
                Receipt Analysis Complete:
                
                Vendor: {{receipt_data_vendor}}
                Date: {{receipt_data_date}}
                Total: {{receipt_data_total}}
                
                Items:
                {{receipt_data_items}}
                
                OCR Text:
                {{receipt_data_ocr}}
            """.trimIndent()
        )
    ]
)
```

### Example 2: Security Monitoring Workflow

```kotlin
val securityMonitor = MultiUserWorkflow(
    name = "Security Monitor",
    description = "Monitor for people in restricted areas",
    trigger = MultiUserTrigger.AutoImageAnalysisTrigger(
        userId = "security_user",
        triggerName = "Camera Monitor",
        sourceDirectory = "/security/cameras",
        analysisType = ImageAnalysisType.PEOPLE_DETECTION,
        enablePeopleDetection = true
    ),
    actions = [
        // Analyze for people
        MultiUserAction.AIImageAnalysisAction(
            imageSource = ImageSource.TriggerImageSource(),
            analysisType = ImageAnalysisType.PEOPLE_DETECTION,
            analysisQuestions = [
                "How many people are in this image?",
                "Describe what each person is doing"
            ],
            enablePeopleDetection = true,
            outputVariable = "security_analysis"
        ),
        
        // Send alert if people detected
        MultiUserAction.SendToUserTelegram(
            targetUserId = "security_user",
            text = "🚨 Security Alert: {{security_analysis_people_count}} people detected in restricted area. {{security_analysis}}"
        )
    ]
)
```

### Example 3: Document Classification Workflow

```kotlin
val documentClassifier = MultiUserWorkflow(
    name = "Document Classifier",
    description = "Automatically classify and route documents",
    trigger = MultiUserTrigger.ImageAnalysisTrigger(
        userId = "admin",
        triggerName = "Document Upload",
        analysisKeywords = ["contract", "invoice", "receipt", "letter"],
        triggerOnKeywordMatch = true,
        enableOCR = true
    ),
    actions = [
        // Classify document
        MultiUserAction.AIImageAnalysisAction(
            imageSource = ImageSource.TriggerImageSource(),
            analysisQuestions = [
                "What type of document is this?",
                "Who is the sender or author?",
                "What is the main subject or purpose?",
                "Are there any important dates?"
            ],
            enableOCR = true,
            outputVariable = "doc_classification"
        ),
        
        // Route based on type
        MultiUserAction.SendToUserGmail(
            targetUserId = "admin",
            subject = "Document Classified: {{doc_classification_type}}",
            body = """
                Document Classification Results:
                
                Type: {{doc_classification_type}}
                Author/Sender: {{doc_classification_sender}}
                Subject: {{doc_classification_subject}}
                Important Dates: {{doc_classification_dates}}
                
                Full Analysis:
                {{doc_classification}}
            """.trimIndent()
        )
    ]
)
```

## Best Practices

### 1. Trigger Configuration

- **Use specific keywords**: More specific keywords reduce false positives
- **Set appropriate confidence levels**: Start with 0.6-0.7 and adjust based on results
- **Enable only needed features**: Disable OCR or object detection if not needed for better performance
- **Test with sample images**: Always test your triggers with representative images

### 2. Action Configuration

- **Use descriptive output variables**: Makes it easier to reference results in subsequent actions
- **Save analysis to files**: For detailed results that need persistence
- **Include specific questions**: More targeted questions give better analysis results
- **Use batch processing**: For multiple related images, use batch analysis for efficiency

### 3. Performance Optimization

- **Image size**: Resize large images to 1024x1024 or smaller for faster processing
- **Analysis type selection**: Use the most specific analysis type for your needs
- **Parallel processing**: Enable for batch operations when processing multiple images
- **Caching**: Results are cached for 15 minutes to avoid reprocessing same images

### 4. Error Handling

- **Confidence thresholds**: Set realistic thresholds to balance accuracy and completeness
- **Fallback actions**: Include actions that handle cases where analysis fails
- **Validation**: Always validate critical extracted data before using it
- **Logging**: Enable detailed logging for troubleshooting

## Troubleshooting

### Common Issues and Solutions

#### 1. Trigger Not Firing

**Problem**: Image analysis trigger isn't activating when expected.

**Solutions**:
- Check keyword matching settings
- Verify confidence threshold isn't too high
- Ensure image format is supported (JPG, PNG, WebP)
- Check image file size (must be under 10MB)
- Verify OCR/object detection settings match your needs

#### 2. Poor OCR Results

**Problem**: Text extraction is inaccurate or incomplete.

**Solutions**:
- Ensure image has good contrast
- Use higher resolution images
- Crop to focus on text areas
- Enable image preprocessing
- Check if text is in a supported language

#### 3. Object Detection Misses Objects

**Problem**: Objects in images aren't being detected.

**Solutions**:
- Lower confidence threshold
- Ensure objects are clearly visible
- Use better lighting in images
- Try different analysis types
- Add more specific keywords

#### 4. Performance Issues

**Problem**: Image analysis is taking too long.

**Solutions**:
- Reduce image resolution
- Use more specific analysis types
- Enable parallel processing for batch operations
- Process images in smaller batches
- Check device resources and memory

### Error Codes

| Code | Description | Solution |
|------|-------------|----------|
| `IMG_001` | Image file not found | Check file path and permissions |
| `IMG_002` | Unsupported image format | Convert to JPG, PNG, or WebP |
| `IMG_003` | Image too large | Resize to under 10MB |
| `IMG_004` | OCR processing failed | Check image quality and contrast |
| `IMG_005` | Analysis timeout | Reduce image size or complexity |

## API Reference

### Output Variables

When using image analysis actions, the following variables are automatically created and can be used in subsequent actions:

#### Single Image Analysis (`AIImageAnalysisAction`)

- `{outputVariable}` - Main analysis description
- `{outputVariable}_ocr` - Extracted OCR text
- `{outputVariable}_people_count` - Number of people detected
- `{outputVariable}_objects` - Comma-separated list of detected objects
- `{outputVariable}_colors` - Dominant colors found
- `{outputVariable}_confidence` - Analysis confidence score (0-100)
- `{outputVariable}_file_path` - Path to saved analysis file (if enabled)

#### Batch Analysis (`AIBatchImageAnalysisAction`)

- `{outputVariable}` - Combined analysis results or individual results
- `{outputVariable}_total_count` - Total number of images processed
- `{outputVariable}_success_count` - Number of successful analyses
- `{outputVariable}_total_people` - Total people count across all images
- `{outputVariable}_{index}_description` - Individual image descriptions (if enabled)
- `{outputVariable}_{index}_ocr` - Individual OCR results (if enabled)

#### Image Comparison (`AIImageComparisonAction`)

- `{outputVariable}` - Comprehensive comparison report
- `{outputVariable}_comparisons_count` - Number of successful comparisons

### Supported Image Formats

- **JPEG** (.jpg, .jpeg)
- **PNG** (.png)
- **WebP** (.webp)
- **Maximum file size**: 10MB
- **Recommended resolution**: 1024x1024 pixels or smaller

### Analysis Capabilities

#### OCR (Optical Character Recognition)
- Supports 100+ languages
- Handles printed and handwritten text
- Extracts text positioning and confidence scores
- Works with various fonts and sizes

#### Object Detection
- Recognizes 80+ common object categories
- Provides bounding box coordinates
- Confidence scores for each detection
- Supports multiple objects per image

#### People Detection
- Counts number of people in images
- Provides basic demographic information
- Detects faces and body poses
- Privacy-focused processing

#### Visual Analysis
- Color palette extraction
- Composition analysis
- Scene understanding
- Style and aesthetic evaluation

## Integration Examples

### Using with Other Triggers

You can combine image analysis with other triggers for more complex workflows:

```kotlin
// Email with image attachment triggers image analysis
val emailImageWorkflow = MultiUserWorkflow(
    name = "Email Image Processor",
    trigger = MultiUserTrigger.UserGmailNewEmail(
        userId = "user123",
        condition = GmailIntegrationService.EmailCondition(
            hasAttachments = true
        )
    ),
    actions = [
        // Analyze email attachments
        MultiUserAction.AIImageAnalysisAction(
            imageSource = ImageSource.EmailAttachmentSource("{{email_id}}", 0),
            analysisType = ImageAnalysisType.COMPREHENSIVE,
            outputVariable = "email_image_analysis"
        ),
        // Forward analysis results
        MultiUserAction.SendToUserTelegram(
            targetUserId = "user123",
            text = "Email image analysis: {{email_image_analysis}}"
        )
    ]
)
```

### Custom Integration

For advanced use cases, you can create custom integrations using the ImageAnalysisService directly:

```kotlin
val imageAnalysisService = ImageAnalysisService(context)
val result = imageAnalysisService.analyzeImage(
    imagePath = "/path/to/image.jpg",
    analysisType = ImageAnalysisType.COMPREHENSIVE,
    questions = listOf("What's in this image?"),
    enableOCR = true
)
```

## Support and Resources

- **GitHub Issues**: Report bugs and request features
- **Documentation**: Latest docs at `/docs/`
- **Examples**: Sample workflows in `/docs/examples/`
- **API Reference**: Complete API docs in `/docs/API_REFERENCE.md`

---

*Last updated: August 2025*
*Version: 1.0.0*