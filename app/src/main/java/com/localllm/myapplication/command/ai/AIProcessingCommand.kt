package com.localllm.myapplication.command.ai

import com.localllm.myapplication.command.Command
import com.localllm.myapplication.data.AIResult
import kotlinx.coroutines.runBlocking

abstract class AIProcessingCommand<T> : Command {
    abstract suspend fun process(): AIResult<T>
    
    var result: AIResult<T>? = null
        private set
    
    override fun execute() {
        result = runBlocking {
            try {
                process()
            } catch (e: Exception) {
                AIResult.Error(e.message ?: "Unknown error occurred")
            }
        }
    }
    
    suspend fun executeAsync(): AIResult<T> {
        return try {
            process()
        } catch (e: Exception) {
            AIResult.Error(e.message ?: "Unknown error occurred")
        }
    }
}