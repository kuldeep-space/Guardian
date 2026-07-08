package com.ai.guardian.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "audit_events")
data class AuditEventEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val eventType: String,
    val detail: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)
