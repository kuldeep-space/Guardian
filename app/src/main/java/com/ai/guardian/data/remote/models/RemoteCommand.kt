package com.ai.guardian.data.remote.models

enum class CommandType {
    LOCK_DEVICE,
    UNLOCK_DEVICE,
    ENABLE_PROTECTION,
    DISABLE_PROTECTION,
    PAUSE_PROTECTION,
    RESUME_PROTECTION,
    REQUEST_FULL_SYNC,
    REQUEST_APP_SYNC,
    REFRESH_CONFIGURATION,
    EMERGENCY_LOCK,
    EMERGENCY_UNLOCK,
    REMOVE_PAIRING,
    SET_PIN,
    CHANGE_PIN,
    RESET_PIN
}

enum class CommandStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}

data class RemoteCommand(
    val commandId: String = "",
    val commandType: CommandType = CommandType.REFRESH_CONFIGURATION,
    val parentDeviceId: String = "",
    val childDeviceId: String = "",
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L,
    val nonce: String = "",
    val status: CommandStatus = CommandStatus.PENDING,
    val authorizationToken: String = "",
    val result: String? = null,
    val retryCount: Int = 0,
    val maxRetryCount: Int = 3,
    val nextRetryAt: Long = 0L,
    val commandSequence: Long = 0L,
    val commandPriority: Int = 0,
    val completedAt: Long = 0L,
    val processedBy: String = "",
    val payload: String = ""
)

