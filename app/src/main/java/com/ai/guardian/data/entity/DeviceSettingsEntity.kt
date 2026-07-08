package com.ai.guardian.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_settings")
data class DeviceSettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    val isProtectionEnabled: Boolean = false,
    val isLivenessDetectionEnabled: Boolean = false,
    val isNotificationMaskingEnabled: Boolean = false,
    val isRemoteTrackingEnabled: Boolean = false,
    val livenessStrictness: String = "Normal", // Relaxed, Normal, Strict
    val showLockScreenOverlay: Boolean = true,
    val trustedAuthDurationMinutes: Int = 1, // 0 means Off
    val matchingThreshold: Float = 0.75f,
    val configurationVersion: Long = 1L,
    val isRemotelyLocked: Boolean = false,
    val securityPinHash: String? = null,
    val securityPinIv: String? = null,
    val securityPinSalt: String? = null,
    val isPinConfigured: Boolean = false,
    val pinVersion: Int = 0,
    val pinUpdatedAt: Long = 0L,
    val pinResetRequired: Boolean = false
)

