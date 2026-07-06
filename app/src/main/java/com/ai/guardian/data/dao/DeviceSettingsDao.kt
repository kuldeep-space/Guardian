package com.ai.guardian.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ai.guardian.data.entity.DeviceSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceSettingsDao {
    @Query("SELECT * FROM device_settings WHERE id = 1")
    fun getSettingsFlow(): Flow<DeviceSettingsEntity?>

    @Query("SELECT * FROM device_settings WHERE id = 1")
    suspend fun getSettings(): DeviceSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSettings(settings: DeviceSettingsEntity)
}
