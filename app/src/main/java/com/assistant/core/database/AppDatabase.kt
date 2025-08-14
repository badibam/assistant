package com.assistant.core.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.assistant.core.database.dao.ZoneDao
import com.assistant.core.database.dao.ToolInstanceDao
import com.assistant.core.database.entities.Zone
import com.assistant.core.database.entities.ToolInstance

@Database(
    entities = [
        Zone::class,
        ToolInstance::class
        // Note: Les entités des outils seront ajoutées dynamiquement
        // via le système de build et ToolTypeRegistry
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun zoneDao(): ZoneDao
    abstract fun toolInstanceDao(): ToolInstanceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "assistant_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}