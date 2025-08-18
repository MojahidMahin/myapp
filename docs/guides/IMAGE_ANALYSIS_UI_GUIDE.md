# Image Analysis UI Guide

## Step-by-Step UI Usage

### Creating an Image Analysis Workflow

#### Step 1: Access WorkflowManager
1. Open the main application
2. Navigate to the main menu
3. Select **"WorkflowManager"**
4. Click **"Create Dynamic Workflow"** button

#### Step 2: Choose Image Analysis Trigger
1. In the workflow builder, locate the **"Choose Trigger"** section
2. Click the **"Choose Trigger"** button
3. You'll see a dialog with trigger category tabs:
   - Gmail
   - Telegram
   - Location
   - Manual
   - **Image Analysis** ← Select this tab

#### Step 3: Select Trigger Type
You'll see two options in the Image Analysis tab:

**Option A: Image Analysis Trigger 📸**
- **Title**: "Image Analysis Trigger"
- **Description**: "Triggers when uploaded images match analysis criteria"
- **Use for**: Processing manually uploaded images
- **Configuration required**: Yes

**Option B: Auto Image Analysis 🤖**
- **Title**: "Auto Image Analysis"
- **Description**: "Automatically analyzes images from other triggers"
- **Use for**: Monitoring directories or automatic processing
- **Configuration required**: Yes

#### Step 4: Configure Your Selected Trigger

**For Image Analysis Trigger:**

1. **Trigger Name** (Required)
   - Enter a descriptive name like "Receipt Scanner" or "Document Processor"
   - This helps you identify the workflow later

2. **Keywords to Look For** (Optional)
   - Enter comma-separated keywords
   - Example: "receipt, total, invoice, amount"
   - Leave blank to analyze all images

3. **Minimum Confidence** (Required)
   - Slider from 0.0 to 1.0
   - Default: 0.5
   - Higher = more strict, Lower = more permissive

4. Click **"Create"** to confirm the trigger

**For Auto Image Analysis:**

1. **Trigger Name** (Required)
   - Enter a descriptive name

2. **Keywords to Monitor** (Optional)
   - Keywords to look for in automatic analysis

3. **Enable OCR** (Optional)
   - Toggle: true/false
   - Default: true

4. **Enable Object Detection** (Optional)
   - Toggle: true/false
   - Default: true

5. Click **"Create"** to confirm the trigger

#### Step 5: Add Actions

After creating your trigger, you'll be back in the workflow builder:

1. **Add Image Analysis Action** (Recommended)
   - Click **"Add Action"**
   - Select **"AI Image Analysis Action"**
   - Configure the analysis parameters

2. **Add Follow-up Actions**
   - Email results: Choose "Send Gmail" action
   - Send to Telegram: Choose "Send Telegram" action
   - Save to file: Enable in the image analysis action

#### Step 6: Configure Actions

**For AI Image Analysis Action:**

1. **Image Source**
   - Usually select "Trigger Image Source"
   - Index 0 for the first image

2. **Analysis Type**
   - Comprehensive (recommended for general use)
   - OCR Only (for text extraction)
   - Object Detection (for item recognition)
   - People Detection (for counting people)

3. **Analysis Questions** (Optional)
   - Add specific questions like:
     - "What type of document is this?"
     - "Extract any amounts or dates"
     - "How many people are in the image?"

4. **Output Variable Name**
   - Choose a name like "analysis_result"
   - This creates variables you can use in other actions

5. **Additional Options**
   - Save Analysis to File: Toggle on/off
   - Include Visual Description: Toggle on/off

#### Step 7: Configure Follow-up Actions

**For Email Action:**
1. **Target User**: Select recipient
2. **Subject**: Use variables like "Analysis Complete: {{analysis_result_type}}"
3. **Body**: Include analysis results using variables:
   ```
   Analysis Results:
   {{analysis_result}}
   
   OCR Text:
   {{analysis_result_ocr}}
   
   Confidence: {{analysis_result_confidence}}%
   ```

**For Telegram Action:**
1. **Target User**: Select recipient
2. **Message**: Use variables like "📸 Image analyzed: {{analysis_result}}"

#### Step 8: Save and Test

1. **Workflow Information**
   - **Name**: Enter a descriptive workflow name
   - **Description**: Add details about what the workflow does

2. **Save Workflow**
   - Click **"Save Workflow"** button
   - The workflow is now active and ready to use

3. **Test the Workflow**
   - Use the **"Test Workflow"** button if available
   - Or trigger it manually by uploading an image

### UI Elements Reference

#### Trigger Configuration Screen

```
┌─────────────────────────────────────────┐
│ Image Analysis Trigger                   │
├─────────────────────────────────────────┤
│ Trigger Name: [________________]         │
│                                         │
│ Analysis Type:                          │
│ ○ Comprehensive ○ OCR Only ○ Objects   │
│ ○ People Detection ○ Quick Scan        │
│                                         │
│ Keywords: [________________________]   │
│                                         │
│ Trigger Options:                        │
│ ☑ Only trigger when keywords found     │
│                                         │
│ Minimum Confidence: [■■■■■□□□□□] 50%    │
│                                         │
│ Features: ☑ OCR ☑ Objects ☑ People    │
│                                         │
│ Image Attachments (0):                 │
│ [Add Images] ┌─ No images attached ─┐  │
│              │ Click 'Add Images'    │  │
│              │ to upload.           │  │
│              └──────────────────────┘  │
│                                         │
│ [Create Image Analysis Trigger]         │
└─────────────────────────────────────────┘
```

#### Action Configuration Screen

```
┌─────────────────────────────────────────┐
│ AI Image Analysis Action                │
├─────────────────────────────────────────┤
│ Image Source:                           │
│ ● Trigger Image (Index: 0)              │
│ ○ File Path ○ URI ○ Variable           │
│                                         │
│ Analysis Type:                          │
│ ● Comprehensive ○ OCR Only ○ Objects   │
│                                         │
│ Analysis Questions:                     │
│ [What type of document is this?]        │
│ [Extract any amounts or dates]          │
│ [+ Add Question]                        │
│                                         │
│ Output Variable: [analysis_result]      │
│                                         │
│ Options:                                │
│ ☑ Save Analysis to File                │
│ ☑ Include Visual Description           │
│ ☑ Enable OCR                           │
│ ☑ Enable Object Detection              │
│                                         │
│ [Create Action]                         │
└─────────────────────────────────────────┘
```

### Common UI Workflows

#### Basic Document Scanner
1. Create workflow → Image Analysis Trigger
2. Set keywords: "document, text, receipt"
3. Add AI Image Analysis Action
4. Add Send Gmail Action with results
5. Save and test

#### Security Monitor
1. Create workflow → Auto Image Analysis
2. Enable People Detection only
3. Add AI Image Analysis Action (People Detection)
4. Add Send Telegram Action for alerts
5. Save and activate

#### Batch Photo Processor
1. Create workflow → Auto Image Analysis
2. Set source directory
3. Add AI Batch Image Analysis Action
4. Add Send Gmail Action with summary
5. Save and monitor

### Troubleshooting UI Issues

#### Trigger Not Visible
- **Problem**: "Image Analysis" tab not showing in trigger selection
- **Solution**: Make sure you're using the latest version with the feature enabled

#### Configuration Not Saving
- **Problem**: Settings reset when navigating between screens
- **Solution**: Complete all required fields before proceeding

#### Images Not Uploading
- **Problem**: "Add Images" button not working
- **Solution**: Check file permissions and supported formats (JPG, PNG, WebP)

#### Variables Not Working
- **Problem**: Output variables not populating in actions
- **Solution**: Ensure variable names match exactly (case-sensitive)

### UI Tips and Tricks

1. **Use Auto-complete**: Start typing variable names and the UI will suggest available variables

2. **Preview Results**: Use the test function to see what variables will be created

3. **Copy Workflows**: Duplicate existing workflows and modify them for similar use cases

4. **Organize with Names**: Use descriptive names for triggers, actions, and variables

5. **Test Incrementally**: Create simple workflows first, then add complexity

6. **Save Frequently**: Save your workflow after each major configuration step

### Keyboard Shortcuts

- **Ctrl+S**: Save workflow
- **Ctrl+T**: Test workflow
- **Escape**: Close dialogs
- **Tab**: Navigate between form fields
- **Enter**: Confirm selections

---

*This guide covers the UI aspects of the Image Analysis Workflow feature. For technical details and API usage, see the main IMAGE_ANALYSIS_WORKFLOWS.md documentation.*