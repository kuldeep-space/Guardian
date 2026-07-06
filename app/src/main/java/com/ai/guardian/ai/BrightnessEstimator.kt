package com.ai.guardian.ai

import androidx.camera.core.ImageProxy

object BrightnessEstimator {

    /**
     * Estimates average luminance using the Y-plane of the ImageProxy.
     * Uses sparse grid sampling to keep execution under 1ms with O(1) allocations.
     * Returns a luminance value between 0 (dark) and 255 (bright).
     */
    fun estimateLuminance(imageProxy: ImageProxy): Int {
        val planes = imageProxy.planes
        if (planes.isEmpty()) return 0
        
        val yPlane = planes[0]
        val buffer = yPlane.buffer
        val rowStride = yPlane.rowStride
        val pixelStride = yPlane.pixelStride
        val width = imageProxy.width
        val height = imageProxy.height

        // Sample a sparse grid (e.g., 10x10 points)
        val sampleGridSize = 10
        val stepX = width / sampleGridSize
        val stepY = height / sampleGridSize

        if (stepX <= 0 || stepY <= 0) return 0

        var sumLuminance = 0L
        var count = 0

        try {
            buffer.position(0)

            for (y in 0 until height step stepY) {
                val rowOffset = y * rowStride
                for (x in 0 until width step stepX) {
                    val index = rowOffset + x * pixelStride
                    if (index < buffer.capacity()) {
                        val pixelValue = buffer.get(index).toInt() and 0xFF
                        sumLuminance += pixelValue
                        count++
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore bounds exceptions for robustness
        } finally {
            try {
                buffer.position(0)
            } catch (e: Exception) {}
        }

        return if (count > 0) (sumLuminance / count).toInt() else 0
    }
}
