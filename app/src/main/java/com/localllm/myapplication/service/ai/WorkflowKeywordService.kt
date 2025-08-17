package com.localllm.myapplication.service.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service responsible for extracting keywords and patterns from text for workflow automation
 * Simplified implementation to avoid naming conflicts
 */
class WorkflowKeywordService {
    
    companion object {
        private const val TAG = "WorkflowKeywordService"
        
        private val ACTION_KEYWORDS = mapOf(
            "reply" to listOf("reply", "respond", "answer", "get back", "follow up"),
            "forward" to listOf("forward", "share", "send to", "pass along", "distribute"),
            "urgent" to listOf("urgent", "asap", "immediately", "emergency", "critical"),
            "support" to listOf("help", "support", "assist", "issue", "problem", "bug")
        )
    }
    
    private val stopWords = setOf(
        "the", "is", "at", "which", "on", "and", "a", "to", "as", "are",
        "was", "will", "be", "been", "have", "has", "had", "do", "does",
        "did", "can", "could", "should", "would", "may", "might", "must",
        "this", "that", "these", "those", "i", "you", "he", "she",
        "it", "we", "they", "me", "him", "her", "us", "them", "my", "your",
        "his", "her", "its", "our", "their", "in", "of", "for", "with",
        "by", "from", "about", "into", "through", "during", "before",
        "after", "above", "below", "up", "down", "out", "off", "over",
        "under", "again", "further", "then", "once"
    )
    
    suspend fun extractKeywords(text: String, maxKeywords: Int = 10): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Extracting keywords from text of length: ${text.length}")
            
            if (text.isBlank()) {
                return@withContext Result.success(emptyList())
            }
            
            val words = preprocessText(text)
            val keywordCandidates = findKeywordCandidates(words)
            val rankedKeywords = rankKeywords(keywordCandidates, text)
            
            val keywords = rankedKeywords
                .take(maxKeywords)
                .map { it.first }
            
            Log.d(TAG, "Extracted ${keywords.size} keywords")
            Result.success(keywords)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting keywords", e)
            Result.failure(e)
        }
    }
    
    suspend fun findKeywordMatches(
        text: String,
        targetKeywords: List<String>,
        fuzzyMatch: Boolean = true
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Finding matches for ${targetKeywords.size} target keywords")
            
            val matches = mutableListOf<String>()
            val processedText = text.lowercase()
            
            for (targetKeyword in targetKeywords) {
                if (fuzzyMatch) {
                    // Simple fuzzy matching
                    if (processedText.contains(targetKeyword.lowercase()) || 
                        findSimilarWords(processedText, targetKeyword.lowercase()).isNotEmpty()) {
                        matches.add(targetKeyword)
                    }
                } else {
                    // Exact matching
                    if (processedText.contains(targetKeyword.lowercase())) {
                        matches.add(targetKeyword)
                    }
                }
            }
            
            Log.d(TAG, "Found ${matches.size} keyword matches")
            Result.success(matches)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error finding keyword matches", e)
            Result.failure(e)
        }
    }
    
    suspend fun extractEmailActions(subject: String, body: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Extracting email actions")
            
            val fullText = "$subject $body".lowercase()
            val actions = mutableListOf<String>()
            
            // Check for action patterns
            for ((actionType, keywords) in ACTION_KEYWORDS) {
                for (keyword in keywords) {
                    if (fullText.contains(keyword)) {
                        actions.add(actionType)
                        break // Only add each action type once
                    }
                }
            }
            
            Log.d(TAG, "Extracted ${actions.size} email actions")
            Result.success(actions.distinct())
            
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting email actions", e)
            Result.failure(e)
        }
    }
    
    private fun preprocessText(text: String): List<String> {
        return text
            .lowercase()
            .replace(Regex("[^a-zA-Z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 }
            .filterNot { it in stopWords }
    }
    
    private fun findKeywordCandidates(words: List<String>): Map<String, Int> {
        val frequency = mutableMapOf<String, Int>()
        
        // Single words
        for (word in words) {
            frequency[word] = frequency.getOrDefault(word, 0) + 1
        }
        
        // Bigrams
        for (i in 0 until words.size - 1) {
            val bigram = "${words[i]} ${words[i + 1]}"
            frequency[bigram] = frequency.getOrDefault(bigram, 0) + 1
        }
        
        return frequency.filter { it.value > 1 }
    }
    
    private fun rankKeywords(candidates: Map<String, Int>, fullText: String): List<Pair<String, Double>> {
        return candidates.map { (word, frequency) ->
            val score = calculateKeywordScore(word, frequency, fullText)
            word to score
        }.sortedByDescending { it.second }
    }
    
    private fun calculateKeywordScore(word: String, frequency: Int, fullText: String): Double {
        val textLength = fullText.length.toDouble()
        val wordLength = word.length.toDouble()
        
        val tf = frequency / textLength * 1000
        val lengthBonus = if (wordLength > 5) 1.5 else 1.0
        val positionBonus = if (fullText.take(100).contains(word)) 1.3 else 1.0
        
        return tf * lengthBonus * positionBonus
    }
    
    private fun findSimilarWords(text: String, target: String): List<String> {
        val words = text.split(Regex("\\s+"))
        return words.filter { word ->
            calculateStringSimilarity(word, target) > 0.7
        }
    }
    
    private fun calculateStringSimilarity(str1: String, str2: String): Double {
        val longer = if (str1.length > str2.length) str1 else str2
        val shorter = if (str1.length > str2.length) str2 else str1
        
        if (longer.isEmpty()) return 1.0
        
        val editDistance = levenshteinDistance(longer, shorter)
        return (longer.length - editDistance) / longer.length.toDouble()
    }
    
    private fun levenshteinDistance(str1: String, str2: String): Int {
        val dp = Array(str1.length + 1) { IntArray(str2.length + 1) }
        
        for (i in 0..str1.length) dp[i][0] = i
        for (j in 0..str2.length) dp[0][j] = j
        
        for (i in 1..str1.length) {
            for (j in 1..str2.length) {
                val cost = if (str1[i - 1] == str2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        
        return dp[str1.length][str2.length]
    }
}