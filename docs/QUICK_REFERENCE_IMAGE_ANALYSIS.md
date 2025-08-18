# Image Analysis Quick Reference

## 🚀 Quick Start
1. **WorkflowManager** → **Create Dynamic Workflow**
2. **Choose Trigger** → **Image Analysis** tab
3. Select trigger type → Configure → Add actions → Save

## 📸 Trigger Types

| Type | Icon | Description | Use Case |
|------|------|-------------|----------|
| **Image Analysis Trigger** | 📸 | Analyzes uploaded images | Manual document processing |
| **Auto Image Analysis** | 🤖 | Monitors directory for images | Automatic batch processing |

## ⚙️ Key Configuration

### Confidence Levels
- **0.3-0.5**: Very permissive
- **0.5-0.7**: Balanced (recommended)
- **0.7-0.9**: High confidence
- **0.9-1.0**: Very strict

### Analysis Types
- **Comprehensive**: Everything (OCR + Objects + People)
- **OCR Only**: Text extraction only
- **Object Detection**: Item recognition
- **People Detection**: Count people
- **Quick Scan**: Fast basic analysis

## 🔧 Essential Actions

### AI Image Analysis Action
```kotlin
// Single image analysis
imageSource = TriggerImageSource(0)
analysisType = COMPREHENSIVE
outputVariable = "result"
enableOCR = true
```

### AI Batch Image Analysis Action
```kotlin
// Multiple images
imageSources = [TriggerImageSource(0), TriggerImageSource(1)]
combineResults = true
parallelProcessing = true
```

## 📤 Output Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `{var}` | Main analysis | "Document contains invoice data" |
| `{var}_ocr` | Extracted text | "Invoice #12345 Total: $99.99" |
| `{var}_people_count` | Number of people | "2" |
| `{var}_objects` | Detected objects | "person, car, building" |
| `{var}_confidence` | Confidence score | "85" |

## 💡 Common Workflows

### 📄 Receipt Processor
```
Trigger: Image Analysis (keywords: "receipt, total")
Action 1: AI Image Analysis (OCR enabled)
Action 2: Send Gmail with extracted data
```

### 🔒 Security Monitor
```
Trigger: Auto Image Analysis (people detection)
Action 1: AI Image Analysis (people only)
Action 2: Send Telegram alert if people detected
```

### 📋 Document Classifier
```
Trigger: Image Analysis (keywords: "document")
Action 1: AI Image Analysis (questions: "What type?")
Action 2: Send email with classification
```

## 🛠️ Best Practices

### ✅ Do
- Use specific keywords for better accuracy
- Set appropriate confidence levels (0.6-0.7)
- Test with sample images first
- Use descriptive variable names
- Enable only needed analysis features

### ❌ Don't
- Set confidence too high (causes missed detections)
- Use very large images (>10MB)
- Enable all features if not needed
- Use generic variable names
- Forget to test workflows

## 🐛 Troubleshooting

| Problem | Solution |
|---------|----------|
| Trigger not firing | Check keywords, lower confidence |
| Poor OCR results | Better image quality, higher resolution |
| Missing objects | Lower confidence, better lighting |
| Slow processing | Smaller images, specific analysis types |

## 📱 UI Quick Steps

1. **Main Menu** → WorkflowManager
2. **Create Dynamic Workflow**
3. **Choose Trigger** → Image Analysis tab
4. **Configure trigger** (name, keywords, confidence)
5. **Add Action** → AI Image Analysis Action
6. **Configure action** (source, questions, output)
7. **Add follow-up actions** (email, telegram, etc.)
8. **Save workflow** (name + description)

## 🔗 File Formats Supported
- **JPEG** (.jpg, .jpeg) ✅
- **PNG** (.png) ✅  
- **WebP** (.webp) ✅
- **Max size**: 10MB
- **Recommended**: 1024x1024px

## 📚 More Documentation
- **Full Guide**: `/docs/IMAGE_ANALYSIS_WORKFLOWS.md`
- **UI Guide**: `/docs/guides/IMAGE_ANALYSIS_UI_GUIDE.md`
- **API Reference**: `/docs/API_REFERENCE.md`
- **Examples**: `/ImageAnalysisWorkflowExample.md`

---
*Quick Reference v1.0 | August 2025*