package com.assistant.core.ai.database

import androidx.room.*
import androidx.room.TypeConverters
import android.content.Context

/**
 * AI Database following standalone pattern like other tool databases
 */
@Database(
    entities = [
        AISessionEntity::class,
        SessionMessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(
    AITypeConverters::class,
    MessageTypeConverters::class
)
abstract class AIDatabase : RoomDatabase() {
    abstract fun aiDao(): AIDao

    companion object {
        @Volatile
        private var INSTANCE: AIDatabase? = null

        fun getDatabase(context: Context): AIDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AIDatabase::class.java,
                    "ai_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}