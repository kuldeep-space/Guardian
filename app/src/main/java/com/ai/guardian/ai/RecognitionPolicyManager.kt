package com.ai.guardian.ai

import android.os.SystemClock

object RecognitionPolicyManager {

    fun determineLightingState(emaLuminance: Float): LightingState {
        return when {
            emaLuminance >= FaceRecognitionConfig.LUM_GOOD -> LightingState.EXCELLENT
            emaLuminance >= FaceRecognitionConfig.LUM_NORMAL -> LightingState.GOOD
            emaLuminance >= FaceRecognitionConfig.LUM_DIM -> LightingState.NORMAL
            emaLuminance >= FaceRecognitionConfig.LUM_VERY_DARK -> LightingState.DIM
            else -> LightingState.VERY_DARK
        }
    }

    fun shouldSampleBrightness(lastSampleTime: Long): Boolean {
        return SystemClock.elapsedRealtime() - lastSampleTime >= FaceRecognitionConfig.BRIGHTNESS_SAMPLE_INTERVAL_MS
    }

    fun calculateTargetExposureIndex(state: LightingState, capability: CameraCapabilityManager): Int {
        if (!capability.isExposureSupported) return 0
        return when (state) {
            LightingState.DIM -> {
                // e.g. +20% of max exposure
                val step = (capability.maxExposureIndex * 0.2f).toInt().coerceAtLeast(1)
                step.coerceIn(capability.minExposureIndex, capability.maxExposureIndex)
            }
            LightingState.VERY_DARK -> {
                // e.g. +50% of max exposure
                val step = (capability.maxExposureIndex * 0.5f).toInt().coerceAtLeast(2)
                step.coerceIn(capability.minExposureIndex, capability.maxExposureIndex)
            }
            else -> 0 // Default exposure
        }
    }

    fun shouldUpdateExposure(
        targetIndex: Int,
        currentIndex: Int,
        lastUpdateTime: Long
    ): Boolean {
        if (targetIndex == currentIndex) return false
        return SystemClock.elapsedRealtime() - lastUpdateTime >= FaceRecognitionConfig.EXPOSURE_UPDATE_COOLDOWN_MS
    }

    fun shouldIncreaseScreenBrightness(state: LightingState): Boolean {
        return state == LightingState.VERY_DARK
    }

    fun shouldRunSecondInference(
        state: LightingState,
        similarity: Float,
        secondInferenceConsumed: Boolean,
        faceDetected: Boolean
    ): Boolean {
        if (secondInferenceConsumed || !faceDetected) return false
        if (state != LightingState.DIM) return false
        val threshold = FaceRecognitionConfig.MATCH_THRESHOLD
        val margin = FaceRecognitionConfig.CONFIDENCE_MARGIN
        return similarity in (threshold - margin)..(threshold + margin)
    }

    fun allowEnrollmentCapture(state: LightingState): Boolean {
        return state != LightingState.VERY_DARK
    }

    fun getTimeoutMs(state: LightingState): Long {
        return when (state) {
            LightingState.DIM -> FaceRecognitionConfig.AUTHENTICATION_TIMEOUT_DIM_MS
            LightingState.VERY_DARK -> FaceRecognitionConfig.AUTHENTICATION_TIMEOUT_VERY_DARK_MS
            else -> FaceRecognitionConfig.AUTHENTICATION_TIMEOUT_NORMAL_MS
        }
    }
}
