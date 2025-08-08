package com.localllm.myapplication.data

enum class PromptTemplateType(
    val id: String,
    val label: String,
    val description: String
) {
    FREE_FORM(
        id = "free_form",
        label = "Free Form", 
        description = "Open-ended conversation and questions"
    ),
    REWRITE_TONE(
        id = "rewrite_tone",
        label = "Rewrite Tone",
        description = "Rewrite text with different tone"
    ),
    SUMMARIZE_TEXT(
        id = "summarize_text", 
        label = "Summarize Text",
        description = "Summarize text in different formats"
    ),
    CODE_SNIPPET(
        id = "code_snippet",
        label = "Code Snippet", 
        description = "Generate code in various programming languages"
    ),
    TRANSLATE_TEXT(
        id = "translate_text",
        label = "Translate Text",
        description = "Translate text between languages"
    )
}

enum class ToneType(val label: String) {
    FORMAL("Formal"),
    CASUAL("Casual"), 
    FRIENDLY("Friendly"),
    POLITE("Polite"),
    ENTHUSIASTIC("Enthusiastic"),
    CONCISE("Concise")
}

enum class SummaryType(val label: String) {
    KEY_BULLET_POINTS("Key bullet points (3-5)"),
    SHORT_PARAGRAPH("Short paragraph (1-2 sentences)"), 
    CONCISE_SUMMARY("Concise summary (~50 words)"),
    HEADLINE_TITLE("Headline / title"),
    ONE_SENTENCE_SUMMARY("One-sentence summary")
}

enum class CodeLanguage(val label: String) {
    KOTLIN("Kotlin"),
    JAVA("Java"),
    PYTHON("Python"),
    JAVASCRIPT("JavaScript"),
    TYPESCRIPT("TypeScript"),
    CPP("C++"),
    SWIFT("Swift")
}

enum class LanguageCode(val label: String, val code: String) {
    ENGLISH("English", "en"),
    SPANISH("Spanish", "es"),
    FRENCH("French", "fr"), 
    GERMAN("German", "de"),
    ITALIAN("Italian", "it"),
    PORTUGUESE("Portuguese", "pt"),
    CHINESE("Chinese", "zh"),
    JAPANESE("Japanese", "ja"),
    KOREAN("Korean", "ko")
}

data class PromptTemplate(
    val type: PromptTemplateType,
    val template: String,
    val parameters: List<TemplateParameter> = emptyList(),
    val exampleInputs: List<String> = emptyList()
) {
    fun buildPrompt(userInput: String, parameterValues: Map<String, String>): String {
        var prompt = template.replace("{input}", userInput)
        
        parameterValues.forEach { (key, value) ->
            prompt = prompt.replace("{$key}", value)
        }
        
        return prompt
    }
}

data class TemplateParameter(
    val key: String,
    val label: String,
    val type: ParameterType,
    val defaultValue: String,
    val options: List<String> = emptyList()
)

enum class ParameterType {
    SINGLE_SELECT,
    TEXT_INPUT
}

object PromptTemplateFactory {
    
    fun getAllTemplates(): List<PromptTemplate> {
        return listOf(
            createFreeFormTemplate(),
            createRewriteToneTemplate(),
            createSummarizeTextTemplate(), 
            createCodeSnippetTemplate(),
            createTranslateTextTemplate()
        )
    }
    
    private fun createFreeFormTemplate(): PromptTemplate {
        return PromptTemplate(
            type = PromptTemplateType.FREE_FORM,
            template = "{input}",
            exampleInputs = listOf(
                "Suggest 3 topics for a podcast about \"Friendships in your 20s\".",
                "Outline the key sections needed in a basic logo design brief.",
                "List 3 pros and 3 cons to consider before buying a smart watch.",
                "Write a short, optimistic quote about the future of technology.",
                "Generate 3 potential names for a mobile app that helps users identify plants."
            )
        )
    }
    
    private fun createRewriteToneTemplate(): PromptTemplate {
        return PromptTemplate(
            type = PromptTemplateType.REWRITE_TONE,
            template = "Rewrite the following text using a {tone} tone: {input}",
            parameters = listOf(
                TemplateParameter(
                    key = "tone",
                    label = "Tone",
                    type = ParameterType.SINGLE_SELECT,
                    defaultValue = ToneType.FORMAL.label,
                    options = ToneType.entries.map { it.label }
                )
            ),
            exampleInputs = listOf(
                "Hey team, just wanted to remind everyone about the meeting tomorrow @ 10. Be there!",
                "Our new software update includes several bug fixes and performance improvements.",
                "Due to the fact that the weather was bad, we decided to postpone the event."
            )
        )
    }
    
    private fun createSummarizeTextTemplate(): PromptTemplate {
        return PromptTemplate(
            type = PromptTemplateType.SUMMARIZE_TEXT,
            template = "Please summarize the following in {style}: {input}",
            parameters = listOf(
                TemplateParameter(
                    key = "style", 
                    label = "Style",
                    type = ParameterType.SINGLE_SELECT,
                    defaultValue = SummaryType.KEY_BULLET_POINTS.label,
                    options = SummaryType.entries.map { it.label }
                )
            ),
            exampleInputs = listOf(
                "The new Pixel phone features an advanced camera system with improved low-light performance and AI-powered editing tools. The display is brighter and more energy-efficient. It runs on the latest Tensor chip, offering faster processing and enhanced security features."
            )
        )
    }
    
    private fun createCodeSnippetTemplate(): PromptTemplate {
        return PromptTemplate(
            type = PromptTemplateType.CODE_SNIPPET,
            template = "Write a {language} code snippet to {input}",
            parameters = listOf(
                TemplateParameter(
                    key = "language",
                    label = "Language", 
                    type = ParameterType.SINGLE_SELECT,
                    defaultValue = CodeLanguage.KOTLIN.label,
                    options = CodeLanguage.entries.map { it.label }
                )
            ),
            exampleInputs = listOf(
                "Create an alert box that says \"Hello, World!\"",
                "Declare an immutable variable named 'appName' with the value \"AI Gallery\"",
                "Print the numbers from 1 to 5 using a for loop.",
                "Write a function that returns the square of an integer input."
            )
        )
    }
    
    private fun createTranslateTextTemplate(): PromptTemplate {
        return PromptTemplate(
            type = PromptTemplateType.TRANSLATE_TEXT,
            template = "Translate the following text to {target_language}: {input}",
            parameters = listOf(
                TemplateParameter(
                    key = "target_language",
                    label = "Target Language",
                    type = ParameterType.SINGLE_SELECT, 
                    defaultValue = LanguageCode.SPANISH.label,
                    options = LanguageCode.entries.map { it.label }
                )
            ),
            exampleInputs = listOf(
                "Hello, how are you today?",
                "Thank you for your help with this project.",
                "I would like to schedule a meeting for next week."
            )
        )
    }
}