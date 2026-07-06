package com.ai.guardian.ai

object FaceRecognitionConfig {
    /**
     * Similarity threshold for matching face embeddings.
     * Note: This threshold is model-dependent and must be calibrated using
     * real device testing and target FAR/FRR metrics.
     * The value 0.55f is a temporary placeholder.
     */
    const val MATCH_THRESHOLD = 0.55f

    // Face size limits (percentage of image area)
    const val MIN_FACE_SIZE_RATIO = 0.20f
    const val MAX_FACE_SIZE_RATIO = 0.60f

    // Head rotation limits (in degrees)
    const val MAX_EULER_Y = 40.0f
    const val MAX_EULER_X = 20.0f
    const val MAX_EULER_Z = 20.0f

    // Pose stability threshold (in degrees)
    const val POSE_STABILITY_THRESHOLD = 2.0f

    // Timeouts (in milliseconds)
    const val ENROLLMENT_TIMEOUT_MS = 15000L
    const val AUTHENTICATION_TIMEOUT_MS = 7000L
}
