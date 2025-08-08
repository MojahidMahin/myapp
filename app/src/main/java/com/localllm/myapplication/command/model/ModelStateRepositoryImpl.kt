package com.localllm.myapplication.command.model

import android.content.Context
import android.content.SharedPreferences

/**
 * Implementation of ModelStateRepository using SharedPreferences
 * Following Single Responsibility Principle - only handles persistence
 */
class ModelStateRepositoryImpl(context: Context) : ModelStateRepository {
    
    companion object {
        private const val PREFS_NAME = "model_state_prefs"
        private const val KEY_CURRENT_MODEL_PATH = "current_model_path"
        private const val KEY_MODEL_LOADED = "model_loaded"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    override fun saveModelState(modelPath: String?, isLoaded: Boolean) {
        prefs.edit()
            .apply {
                if (modelPath != null) {
                    putString(KEY_CURRENT_MODEL_PATH, modelPath)
                } else {
                    remove(KEY_CURRENT_MODEL_PATH)
                }
                putBoolean(KEY_MODEL_LOADED, isLoaded)
            }
            .apply()
    }
    
    override fun getLastModelPath(): String? {
        return prefs.getString(KEY_CURRENT_MODEL_PATH, null)
    }
    
    override fun wasModelLoaded(): Boolean {
        return prefs.getBoolean(KEY_MODEL_LOADED, false)
    }
    
    override fun clearModelState() {
        prefs.edit()
            .remove(KEY_CURRENT_MODEL_PATH)
            .putBoolean(KEY_MODEL_LOADED, false)
            .apply()
    }
}