package com.localllm.myapplication.config

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.io.IOException

class ConfigLoader(private val context: Context) {
    
    private val gson = Gson()
    private var cachedConfig: AIModelConfig? = null
    
    suspend fun loadConfig(): AIModelConfig {
        cachedConfig?.let { return it }
        
        return try {
            val configJson = loadConfigFromAssets()
            val config = gson.fromJson(configJson, AIModelConfig::class.java)
            cachedConfig = config
            config
        } catch (e: Exception) {
            getDefaultConfig()
        }
    }
    
    private fun loadConfigFromAssets(): String {
        return try {
            context.assets.open("ai_model_config.json").bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            throw ConfigLoadException("Failed to load config from assets", e)
        }
    }
    
    private fun getDefaultConfig(): AIModelConfig {
        return AIModelConfig()
    }
    
    fun clearCache() {
        cachedConfig = null
    }
    
    class ConfigLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)
}