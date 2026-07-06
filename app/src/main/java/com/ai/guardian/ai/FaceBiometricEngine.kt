package com.ai.guardian.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.media.Image
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.face.Face
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt
import com.ai.guardian.data.entity.FaceProfileWithTemplates
import com.ai.guardian.data.entity.FaceTemplateEntity
import com.ai.guardian.data.entity.FaceProfileEntity

class FaceBiometricEngine(context: Context) {
    private val faceDetector = FaceDetectorHelper()
    private val mobileFaceNet = MobileFaceNet(context)

    // Memory Cache for recognition
    private val templateCache = mutableMapOf<FaceProfileEntity, List<FloatArray>>()

    /**
     * Loads all templates for the given profiles into memory.
     * This prevents querying the database on every frame.
     */
    fun loadTemplates(profiles: List<FaceProfileWithTemplates>) {
        templateCache.clear()
        for (profileWithTemplates in profiles) {
            val floatArrays = profileWithTemplates.templates.mapNotNull { template ->
                val arr = template.embeddingData.split(",").mapNotNull { it.toFloatOrNull() }.toFloatArray()
                if (arr.size == 192) arr else null
            }
            templateCache[profileWithTemplates.profile] = floatArrays
        }
    }

    /**
     * Clears the template cache from memory.
     */
    fun clearCache() {
        templateCache.clear()
    }

    /**
     * Matches the given embedding against all cached templates across all profiles.
     * Returns a pair of the matched FaceProfileEntity and the best similarity score, or null if no match exceeds threshold.
     */
    fun matchAgainstCache(liveEmbedding: FloatArray): Pair<FaceProfileEntity, Float>? {
        var bestOverallScore = 0f
        var bestProfile: FaceProfileEntity? = null

        for ((profile, embeddings) in templateCache) {
            var bestProfileScore = 0f
            for (cachedEmbedding in embeddings) {
                val score = calculateCosineSimilarity(liveEmbedding, cachedEmbedding)
                if (score > bestProfileScore) {
                    bestProfileScore = score
                }
            }
            if (bestProfileScore > bestOverallScore) {
                bestOverallScore = bestProfileScore
                bestProfile = profile
            }
        }

        if (bestOverallScore > FaceRecognitionConfig.MATCH_THRESHOLD && bestProfile != null) {
            return Pair(bestProfile, bestOverallScore)
        }
        return null
    }

    /**
     * Helper to validate quality of a detected face.
     * Returns null if quality checks pass, or a String describing the failure reason.
     */
    fun checkFaceQuality(face: Face, width: Int, height: Int): String? {
        val imageArea = (width * height).toFloat()
        if (imageArea <= 0f) return "Invalid image area"

        val faceArea = (face.boundingBox.width() * face.boundingBox.height()).toFloat()
        val faceRatio = faceArea / imageArea

        if (faceRatio < FaceRecognitionConfig.MIN_FACE_SIZE_RATIO) {
            return "Move closer"
        }
        if (faceRatio > FaceRecognitionConfig.MAX_FACE_SIZE_RATIO) {
            return "Move back"
        }

        // Relaxed movement thresholds for natural micro-movements
        val rotY = Math.abs(face.headEulerAngleY)
        val rotX = Math.abs(face.headEulerAngleX)
        val rotZ = Math.abs(face.headEulerAngleZ)

        // Using relaxed thresholds (e.g., 30 degrees instead of strictly looking straight for general detection)
        // Pose strictness is handled in the UI for enrollment.
        if (rotZ > 35f) {
            return "Straighten your head"
        }

        val leftEyeOpen = face.leftEyeOpenProbability ?: 1.0f
        val rightEyeOpen = face.rightEyeOpenProbability ?: 1.0f
        if (leftEyeOpen < 0.2f || rightEyeOpen < 0.2f) {
            return "Open eyes"
        }

        return null
    }

    /**
     * Analyzes a CameraX ImageProxy frame directly.
     * Checks multiple faces, performs quality verification, and processes embedding if checks pass.
     */
    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    suspend fun analyzeFrame(imageProxy: ImageProxy): VerificationResult = withContext(Dispatchers.Default) {
        try {
            val mediaImage = imageProxy.image
                ?: return@withContext VerificationResult.Error("mediaImage is null")

            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            // Step 1: Detect faces
            val faces = faceDetector.detectFaces(mediaImage, rotationDegrees)
            if (faces.isEmpty()) {
                return@withContext VerificationResult.NoFace
            }
            if (faces.size > 1) {
                return@withContext VerificationResult.MultipleFaces
            }

            val face = faces[0]

            // Step 2: Quality validation before inference
            val qualityError = checkFaceQuality(face, imageProxy.width, imageProxy.height)
            if (qualityError != null) {
                return@withContext VerificationResult.PoorQuality(qualityError)
            }

            // Step 3: Run MobileFaceNet inference
            val bitmap: Bitmap = imageProxy.toBitmap()
            val embedding = mobileFaceNet.getFaceEmbedding(bitmap, face.boundingBox, rotationDegrees)
                ?: return@withContext VerificationResult.Error("Failed to generate embedding")

            return@withContext VerificationResult.Success(embedding, face)
        } catch (e: Exception) {
            android.util.Log.e("GuardianAI_Debug", "[AI] analyzeFrame error: ${e.message}", e)
            return@withContext VerificationResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Legacy frame analysis helper.
     */
    suspend fun analyzeFrame(image: Image, rotationDegrees: Int, bitmap: Bitmap): VerificationResult = withContext(Dispatchers.Default) {
        try {
            val faces = faceDetector.detectFaces(image, rotationDegrees)
            if (faces.isEmpty()) {
                return@withContext VerificationResult.NoFace
            }
            if (faces.size > 1) {
                return@withContext VerificationResult.MultipleFaces
            }

            val face = faces[0]

            val qualityError = checkFaceQuality(face, bitmap.width, bitmap.height)
            if (qualityError != null) {
                return@withContext VerificationResult.PoorQuality(qualityError)
            }

            val embedding = mobileFaceNet.getFaceEmbedding(bitmap, face.boundingBox, rotationDegrees)
                ?: return@withContext VerificationResult.Error("Failed to generate embedding")

            return@withContext VerificationResult.Success(embedding, face)
        } catch (e: Exception) {
            return@withContext VerificationResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Analyzes static bitmap.
     */
    suspend fun analyzeStaticImage(bitmap: Bitmap): VerificationResult = withContext(Dispatchers.Default) {
        try {
            val faces = faceDetector.detectFacesFromBitmap(bitmap)
            if (faces.isEmpty()) {
                return@withContext VerificationResult.NoFace
            }
            if (faces.size > 1) {
                return@withContext VerificationResult.MultipleFaces
            }

            val face = faces[0]

            val qualityError = checkFaceQuality(face, bitmap.width, bitmap.height)
            if (qualityError != null) {
                return@withContext VerificationResult.PoorQuality(qualityError)
            }

            val embedding = mobileFaceNet.getFaceEmbedding(bitmap, face.boundingBox, 0, isFrontCamera = false)
                ?: return@withContext VerificationResult.Error("Failed to generate embedding")

            return@withContext VerificationResult.Success(embedding, face)
        } catch (e: Exception) {
            return@withContext VerificationResult.Error(e.message ?: "Unknown error")
        }
    }

    fun close() {
        clearCache()
        faceDetector.close()
        mobileFaceNet.close()
    }

    companion object {
        @JvmField
        @Deprecated("Use FaceRecognitionConfig.MATCH_THRESHOLD")
        val MATCH_THRESHOLD = FaceRecognitionConfig.MATCH_THRESHOLD

        fun calculateCosineSimilarity(emb1: FloatArray, emb2: FloatArray): Float {
            var dot = 0f
            var norm1 = 0f
            var norm2 = 0f
            for (i in emb1.indices) {
                dot += emb1[i] * emb2[i]
                norm1 += emb1[i] * emb1[i]
                norm2 += emb2[i] * emb2[i]
            }
            if (norm1 <= 0f || norm2 <= 0f) return 0f
            return dot / (sqrt(norm1) * sqrt(norm2))
        }

        fun calculateL2Distance(emb1: FloatArray, emb2: FloatArray): Float {
            var sum = 0f
            for (i in emb1.indices) {
                val diff = emb1[i] - emb2[i]
                sum += diff * diff
            }
            return sqrt(sum)
        }
    }
}

open class VerificationResult {
    open class NoFaceDetected : VerificationResult()
    object NoFace : NoFaceDetected()

    object MultipleFaces : VerificationResult()

    open class Failed(val reason: String) : VerificationResult()
    class PoorQuality(reasonStr: String) : Failed(reasonStr)
    class Error(val reasonStr: String) : Failed(reasonStr)

    data class Success(val embedding: FloatArray, val mlkitFace: Face) : VerificationResult()
}
