package com.ai.guardian.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Tracks a pending Parent authorization request from the Child side.
 * Every protected action creates one of these before being allowed to proceed.
 */
@Entity(tableName = "approval_requests")
data class ApprovalRequestEntity(
    @PrimaryKey val requestId: String = UUID.randomUUID().toString(),
    val action: String,             // ProtectedAction enum name
    val status: String = "PENDING", // PENDING, APPROVED, REJECTED, EXPIRED, CONSUMED
    val requestedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = requestedAt + (5 * 60 * 1000), // 5 minutes default
    val tokenNonce: String = UUID.randomUUID().toString() // Prevents token replay
)
