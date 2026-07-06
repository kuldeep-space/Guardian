package com.ai.guardian.ai

import androidx.camera.core.Camera

class CameraCapabilityManager(camera: Camera) {
    val isExposureSupported: Boolean
    val minExposureIndex: Int
    val maxExposureIndex: Int

    init {
        val exposureState = camera.cameraInfo.exposureState
        isExposureSupported = exposureState.isExposureCompensationSupported
        val range = exposureState.exposureCompensationRange
        minExposureIndex = range.lower
        maxExposureIndex = range.upper
    }
}
