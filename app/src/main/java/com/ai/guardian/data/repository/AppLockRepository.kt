package com.ai.guardian.data.repository

import com.ai.guardian.data.dao.AppLockDao
import com.ai.guardian.data.entity.AppLockEntity
import com.ai.guardian.data.remote.SyncEngineManager
import kotlinx.coroutines.flow.Flow

class AppLockRepository(
    private val appLockDao: AppLockDao,
    private val syncEngineManager: SyncEngineManager
) {
    fun getAllAppsFlow(): Flow<List<AppLockEntity>> = appLockDao.getAllAppsFlow()

    suspend fun insertApp(app: AppLockEntity) {
        appLockDao.insertApp(app)
        syncEngineManager.requestLocalAppSync(listOf(app))
    }
}
