package com.ai.guardian.di

import android.content.Context
import com.ai.guardian.data.AppDatabase
import com.ai.guardian.data.dao.AppLockDao
import com.ai.guardian.data.dao.DeviceSettingsDao
import com.ai.guardian.data.dao.FaceDao
import com.ai.guardian.data.dao.RecognitionHistoryDao

interface AppContainer {
    val faceDao: FaceDao
    val deviceSettingsDao: DeviceSettingsDao
    val appLockDao: AppLockDao
    val recognitionHistoryDao: RecognitionHistoryDao
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    private val database by lazy { AppDatabase.getDatabase(context) }

    override val faceDao: FaceDao by lazy { database.faceDao() }
    override val deviceSettingsDao: DeviceSettingsDao by lazy { database.deviceSettingsDao() }
    override val appLockDao: AppLockDao by lazy { database.appLockDao() }
    override val recognitionHistoryDao: RecognitionHistoryDao by lazy { database.recognitionHistoryDao() }
}
