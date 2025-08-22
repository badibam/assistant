package com.assistant.tools.tracking.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.assistant.tools.tracking.entities.TrackingData

/**
 * Standalone database for tracking data
 * Each tool type has its own database for discovery isolation
 */
@Database(
    entities = [TrackingData::class],
    version = 1,
    exportSchema = false
)
abstract class TrackingDatabase : RoomDatabase() {
    abstract fun trackingDao(): TrackingDao

    companion object {
        @Volatile
        private var INSTANCE: TrackingDatabase? = null

        fun getDatabase(context: Context): TrackingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TrackingDatabase::class.java,
                    "tracking_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}