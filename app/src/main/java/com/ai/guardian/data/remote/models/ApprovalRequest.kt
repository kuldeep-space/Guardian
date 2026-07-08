package com.ai.guardian.data.remote.models

data class ApprovalRequest(
    val requestId: String = "",
    val childDeviceId: String = "",
    val parentDeviceId: String = "",
    val requestType: String = "",
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L,
    val nonce: String = "",
    val status: String = "PENDING", // PENDING, APPROVED, REJECTED, EXPIRED, CANCELLED
    val authorizationToken: String = "",
    val metadata: Map<String, String> = emptyMap()
)
