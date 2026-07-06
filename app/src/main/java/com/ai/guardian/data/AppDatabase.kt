package com.ai.guardian.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ai.guardian.data.dao.AppLockDao
import com.ai.guardian.data.dao.DeviceSettingsDao
import com.ai.guardian.data.dao.FaceDao
import com.ai.guardian.data.dao.RecognitionHistoryDao
import com.ai.guardian.data.entity.AppLockEntity
import com.ai.guardian.data.entity.DeviceSettingsEntity
import com.ai.guardian.data.entity.FaceProfileEntity
import com.ai.guardian.data.entity.FaceTemplateEntity
import com.ai.guardian.data.entity.RecognitionHistoryEntity

@Database(
    entities = [
        FaceProfileEntity::class,
        FaceTemplateEntity::class,
        DeviceSettingsEntity::class,
        AppLockEntity::class,
        RecognitionHistoryEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun faceDao(): FaceDao
    abstract fun deviceSettingsDao(): DeviceSettingsDao
    abstract fun appLockDao(): AppLockDao
    abstract fun recognitionHistoryDao(): RecognitionHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "guardian_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
