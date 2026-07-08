package com.ai.guardian.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ai.guardian.data.entity.AppLockEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppLockDao {
    @Query("SELECT * FROM app_lock_rules")
    fun getAllAppsFlow(): Flow<List<AppLockEntity>>

    @Query("SELECT * FROM app_lock_rules")
    suspend fun getAllApps(): List<AppLockEntity>

    @Query("SELECT isProtected FROM app_lock_rules WHERE packageName = :packageName")
    suspend fun isAppProtected(packageName: String): Boolean?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApp(app: AppLockEntity)

    @Update
    suspend fun updateApp(app: AppLockEntity)

    @Query("DELETE FROM app_lock_rules")
    suspend fun deleteAll()

    @Query("DELETE FROM app_lock_rules WHERE packageName = :packageName")
    suspend fun deleteAppByPackage(packageName: String)
}
