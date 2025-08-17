package com.localllm.myapplication.data.database

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        WorkflowUserEntity::class,
        WorkflowEntity::class,
        WorkflowExecutionEntity::class,
        ProcessedEmailEntity::class,
        ContactEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class WorkflowDatabase : RoomDatabase() {
    abstract fun workflowUserDao(): WorkflowUserDao
    abstract fun workflowDao(): WorkflowDao
    abstract fun workflowExecutionDao(): WorkflowExecutionDao
    abstract fun processedEmailDao(): ProcessedEmailDao
    abstract fun contactDao(): ContactDao
    
    companion object {
        @Volatile
        private var INSTANCE: WorkflowDatabase? = null
        
        fun getDatabase(context: Context): WorkflowDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WorkflowDatabase::class.java,
                    "workflow_database"
                )
                    .fallbackToDestructiveMigration() // For development - remove in production
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// Note: Using JSON strings for complex objects in entities
// No type converters needed currently