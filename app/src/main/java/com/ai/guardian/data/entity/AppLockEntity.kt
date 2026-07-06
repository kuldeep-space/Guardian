package com.ai.guardian.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_lock_rules")
data class AppLockEntity(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val isProtected: Boolean = false,
    val todayUsageMs: Long = 0
)
