package com.ai.guardian.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recognition_history")
data class RecognitionHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val profileId: Long? = null,
    val protectedAppPackage: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val authResult: Boolean, // true for Success, false for Failure
    val failureReason: String? = null,
    val similarityScore: Float? = null,
    val recognitionTimeMs: Long? = null,
    val deviceOrientation: Int? = null,
    val recognitionType: String // "APP_UNLOCK", "RECOGNITION_TEST", "ENROLLMENT_SELF_TEST"
)
