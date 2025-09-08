package com.assistant.core.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.assistant.core.database.dao.ZoneDao
import com.assistant.core.database.dao.ToolInstanceDao
import com.assistant.core.database.dao.BaseToolDataDao
import com.assistant.core.database.dao.AppSettingsCategoryDao
import com.assistant.core.database.entities.Zone
import com.assistant.core.database.entities.ToolInstance
import com.assistant.core.database.entities.ToolDataEntity
import com.assistant.core.database.entities.AppSettingsCategory
import com.assistant.core.versioning.MigrationOrchestrator

@Database(
    entities = [
        Zone::class,
        ToolInstance::class,
        ToolDataEntity::class,
        AppSettingsCategory::class
        // Note: Les entités des outils seront ajoutées dynamiquement
        // via le système de build et ToolTypeRegistry
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun zoneDao(): ZoneDao
    abstract fun toolInstanceDao(): ToolInstanceDao
    abstract fun toolDataDao(): BaseToolDataDao
    abstract fun appSettingsCategoryDao(): AppSettingsCategoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val migrationOrchestrator = MigrationOrchestrator(context)
                
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "assistant_database"
                )
                .addMigrations(*migrationOrchestrator.getAllMigrations())
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // Base de données ouverte avec succès
                        println("AppDatabase opened successfully - version ${db.version}")
                    }
                })
                .build()
                
                INSTANCE = instance
                instance
            }
        }
    }
}