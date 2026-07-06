package com.ai.guardian.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ai.guardian.GuardianApplication
import com.ai.guardian.ai.*
import com.ai.guardian.data.entity.FaceProfileEntity
import com.ai.guardian.data.entity.RecognitionHistoryEntity
import com.ai.guardian.services.GuardianForegroundService
import com.ai.guardian.ui.theme.GuardianAITheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AppLockActivity : ComponentActivity() {
    private var engine: FaceBiometricEngine? = null
    private var cameraExecutor: ExecutorService? = null
    private var camera: Camera? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val packageName = intent.getStringExtra("EXTRA_PACKAGE_NAME") ?: "Unknown App"
        val showOverlay = intent.getBooleanExtra("EXTRA_SHOW_OVERLAY", true)
        android.util.Log.d("GuardianAI_Debug", "[Lock] onCreate() pkg=$packageName showOverlay=$showOverlay")
        
        if (!showOverlay) {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        engine = FaceBiometricEngine(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        setContent {
            GuardianAITheme {
                InvisibleLockScreen(packageName = packageName, engine = engine, initialShowOverlay = showOverlay)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        engine?.close()
        cameraExecutor?.shutdown()
    }

    @Composable
    fun InvisibleLockScreen(packageName: String, engine: FaceBiometricEngine?, initialShowOverlay: Boolean) {
        var showIntruderBlock by remember { mutableStateOf(false) }
        var showDebugLogs by remember { mutableStateOf(false) }
        val context = this@AppLockActivity
        val coroutineScope = rememberCoroutineScope()
        var hasProfiles by remember { mutableStateOf(false) }
        
        val isProcessingRef = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
        var debugLogs by remember { mutableStateOf(listOf<String>("[System] Initiating Guardian Lock...")) }
        val isCleanedUp = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

        var isUnlocked by remember { mutableStateOf(false) }
        var isScreenLocked by remember { mutableStateOf(false) }
        var showLockScreenOverlay by remember { mutableStateOf(initialShowOverlay) }

        // Session State
        val session = remember { AuthenticationSession() }
        var capabilityManager: CameraCapabilityManager? by remember { mutableStateOf(null) }
        var authState by remember { mutableStateOf(AuthenticationState.INITIALIZING) }
        
        // Second inference state
        var pendingBestScore = 0f
        var pendingBestProfile: FaceProfileEntity? = null

        fun addLog(msg: String) {
            android.util.Log.d("GuardianAI_Debug", msg)
            debugLogs = (debugLogs + msg).takeLast(6)
        }

        fun restoreDeviceState() {
            try {
                // Restore Exposure
                if (session.currentExposureIndex != 0 && capabilityManager?.isExposureSupported == true) {
                    camera?.cameraControl?.setExposureCompensationIndex(0)
                    session.currentExposureIndex = 0
                }
                // Restore Brightness
                if (session.isScreenBrightened && session.originalScreenBrightness >= 0f) {
                    val attrs = window.attributes
                    attrs.screenBrightness = session.originalScreenBrightness
                    window.attributes = attrs
                    session.isScreenBrightened = false
                }
            } catch (e: Exception) {
                addLog("[Lock] Failed to restore device state: ${e.message}")
            }
        }

        fun cleanupAndSecure(shouldLock: Boolean, logReason: String, logType: String = "SECURITY") {
            if (isCleanedUp.getAndSet(true)) return
            
            authState = AuthenticationState.CLEANUP
            session.destroy()
            restoreDeviceState()
            
            if (shouldLock && !isUnlocked) {
                isScreenLocked = true
                addLog("[Security] $logReason. Locking.")
                
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val logDao = (application as GuardianApplication).container.recognitionHistoryDao
                        logDao.insertHistory(
                            RecognitionHistoryEntity(
                                profileId = null,
                                protectedAppPackage = packageName,
                                timestamp = System.currentTimeMillis(),
                                authResult = false,
                                failureReason = logReason,
                                similarityScore = null,
                                recognitionTimeMs = null,
                                deviceOrientation = 0,
                                recognitionType = "APP_UNLOCK"
                            )
                        )
                    } catch (e: Exception) {}
                }
                
                com.ai.guardian.services.GuardianAccessibilityService.lockDeviceScreen(context)
            }
            
            finish()
        }

        fun resetTimeout(state: LightingState) {
            session.timeoutJob?.cancel()
            val timeoutMs = RecognitionPolicyManager.getTimeoutMs(state)
            session.timeoutJob = coroutineScope.launch {
                delay(timeoutMs)
                cleanupAndSecure(shouldLock = true, logReason = "Face scan timed out", logType = "TIMEOUT")
            }
        }

        LaunchedEffect(Unit) {
            resetTimeout(LightingState.NORMAL)

            try {
                val dao = (application as GuardianApplication).container.faceDao

                val profiles = withContext(Dispatchers.IO) {
                    dao.getAllProfilesWithTemplates()
                }
                if (profiles.isNotEmpty()) {
                    engine?.loadTemplates(profiles)
                    hasProfiles = true
                    addLog("[System] Loaded ${profiles.size} profile(s)")
                } else {
                    addLog("[System] WARNING: No profiles found!")
                    cleanupAndSecure(shouldLock = true, logReason = "No profiles enrolled")
                    return@LaunchedEffect
                }
            } catch (e: Exception) {
                cleanupAndSecure(shouldLock = true, logReason = "Database failure")
                return@LaunchedEffect
            }

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED && engine != null && cameraExecutor != null) {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setTargetResolution(android.util.Size(640, 480))
                            .build()

                        imageAnalysis.setAnalyzer(cameraExecutor!!) { imageProxy: ImageProxy ->
                            if (isProcessingRef.getAndSet(true) || !hasProfiles || isUnlocked || isScreenLocked || isCleanedUp.get()) {
                                imageProxy.close()
                                isProcessingRef.set(false)
                                return@setAnalyzer
                            }

                            coroutineScope.launch {
                                try {
                                    val currentEngine = engine
                                    if (currentEngine == null || isCleanedUp.get()) return@launch

                                    // 1. WARM-UP Phase
                                    session.framesAnalyzed++
                                    val timeSinceStart = SystemClock.elapsedRealtime() - session.sessionStartTime
                                    if (session.framesAnalyzed <= FaceRecognitionConfig.WARMUP_FRAMES_TO_SKIP && timeSinceStart < FaceRecognitionConfig.MAXIMUM_WARMUP_TIME_MS) {
                                        authState = AuthenticationState.WARMING_UP
                                        return@launch
                                    }

                                    // 2. BRIGHTNESS_ANALYSIS Phase
                                    if (RecognitionPolicyManager.shouldSampleBrightness(session.lastBrightnessSampleTime)) {
                                        authState = AuthenticationState.BRIGHTNESS_ANALYSIS
                                        val luminance = BrightnessEstimator.estimateLuminance(imageProxy)
                                        session.updateEma(luminance)
                                        session.lastBrightnessSampleTime = SystemClock.elapsedRealtime()
                                        
                                        val proposedState = RecognitionPolicyManager.determineLightingState(session.emaLuminance)
                                        if (proposedState == session.pendingLightingState) {
                                            session.consecutiveLightingStateMatches++
                                            if (session.consecutiveLightingStateMatches >= FaceRecognitionConfig.STABLE_STATE_COUNT_REQUIRED) {
                                                if (session.lightingState != proposedState) {
                                                    session.lightingState = proposedState
                                                    resetTimeout(session.lightingState)
                                                }
                                            }
                                        } else {
                                            session.pendingLightingState = proposedState
                                            session.consecutiveLightingStateMatches = 1
                                        }
                                    }

                                    // 3. APPLY POLICIES (Screen Brightness & Camera Exposure)
                                    withContext(Dispatchers.Main) {
                                        if (RecognitionPolicyManager.shouldIncreaseScreenBrightness(session.lightingState)) {
                                            if (!session.isScreenBrightened) {
                                                val attrs = window.attributes
                                                session.originalScreenBrightness = attrs.screenBrightness
                                                if (attrs.screenBrightness != 1.0f) {
                                                    attrs.screenBrightness = 1.0f
                                                    window.attributes = attrs
                                                }
                                                session.isScreenBrightened = true
                                            }
                                        } else if (session.isScreenBrightened) {
                                            restoreDeviceState() // Restore if lighting improves
                                        }
                                    }

                                    capabilityManager?.let { cap ->
                                        val targetExposure = RecognitionPolicyManager.calculateTargetExposureIndex(session.lightingState, cap)
                                        if (RecognitionPolicyManager.shouldUpdateExposure(targetExposure, session.currentExposureIndex, session.lastExposureUpdateTime)) {
                                            camera?.cameraControl?.setExposureCompensationIndex(targetExposure)
                                            session.currentExposureIndex = targetExposure
                                            session.lastExposureUpdateTime = SystemClock.elapsedRealtime()
                                        }
                                    }

                                    // 4. FACE_SEARCHING & RECOGNIZING
                                    authState = AuthenticationState.FACE_SEARCHING
                                    val result = currentEngine.analyzeFrame(imageProxy)
                                    
                                    if (isUnlocked || isScreenLocked || isCleanedUp.get()) return@launch

                                    when (result) {
                                        is VerificationResult.Success -> {
                                            authState = AuthenticationState.RECOGNIZING
                                            addLog("[AI] Face detected.")
                                            
                                            val matchResult = currentEngine.matchAgainstCache(result.embedding)

                                            if (matchResult != null) {
                                                val score = matchResult.second
                                                val profile = matchResult.first

                                                val needsSecondInference = RecognitionPolicyManager.shouldRunSecondInference(
                                                    session.lightingState, score, session.secondInferenceConsumed, true
                                                )

                                                if (needsSecondInference) {
                                                    authState = AuthenticationState.SECOND_INFERENCE
                                                    session.secondInferenceConsumed = true
                                                    pendingBestScore = score
                                                    pendingBestProfile = profile
                                                    addLog("[AI] Borderline score ($score). Triggering second inference.")
                                                    return@launch
                                                }

                                                // Final Decision
                                                val finalScore = maxOf(score, pendingBestScore)
                                                val finalProfile = if (finalScore == pendingBestScore && pendingBestProfile != null) pendingBestProfile else profile

                                                if (finalScore >= FaceRecognitionConfig.MATCH_THRESHOLD) {
                                                    authState = AuthenticationState.SUCCESS
                                                    addLog("[AI] Match! Score: $finalScore. Unlocking.")
                                                    isUnlocked = true
                                                    
                                                    withContext(Dispatchers.Main) {
                                                        com.ai.guardian.services.GuardianAccessibilityService.reportSuccessfulAuthentication()
                                                        com.ai.guardian.services.GuardianAccessibilityService.whitelistPackage(packageName)
                                                        val whitelistIntent = Intent(context, GuardianForegroundService::class.java).apply {
                                                            action = GuardianForegroundService.ACTION_WHITELIST_PACKAGE
                                                            putExtra(GuardianForegroundService.EXTRA_PACKAGE_NAME, packageName)
                                                        }
                                                        ContextCompat.startForegroundService(context, whitelistIntent)
                                                        cleanupAndSecure(shouldLock = false, logReason = "Unlocked")
                                                    }
                                                } else {
                                                    authState = AuthenticationState.FAILED
                                                    cleanupAndSecure(shouldLock = true, logReason = "Match failed on second inference", logType = "UNAUTHORIZED_ACCESS")
                                                }
                                            } else {
                                                // Handle no match but we might have a pending score from first inference that is borderline?
                                                // If we had a pending score but this frame gives no match at all, we should still evaluate the pending score.
                                                if (pendingBestScore >= FaceRecognitionConfig.MATCH_THRESHOLD) {
                                                    // This case shouldn't happen because pending score is by definition borderline (so it could be just below threshold, or just above).
                                                    // If pending score was above threshold but within margin, we could use it.
                                                }
                                                authState = AuthenticationState.FAILED
                                                cleanupAndSecure(shouldLock = true, logReason = "Unknown face detected", logType = "UNAUTHORIZED_ACCESS")
                                            }
                                        }
                                        is VerificationResult.MultipleFaces -> {
                                            session.timeoutJob?.cancel()
                                            addLog("Only one face should be visible.")
                                        }
                                        is VerificationResult.PoorQuality -> {
                                            resetTimeout(session.lightingState)
                                            val msg = when (result.quality) {
                                                com.ai.guardian.ai.FaceQuality.TOO_FAR -> "Move closer"
                                                com.ai.guardian.ai.FaceQuality.TOO_CLOSE -> "Move back"
                                                com.ai.guardian.ai.FaceQuality.NOT_STRAIGHT -> "Straighten your head"
                                                com.ai.guardian.ai.FaceQuality.EYES_CLOSED -> "Open eyes"
                                                com.ai.guardian.ai.FaceQuality.INVALID_AREA -> "Invalid image area"
                                                else -> "Poor quality"
                                            }
                                            addLog("Align Face: $msg")
                                        }
                                        is VerificationResult.NoFace -> {
                                            // Passive scanning
                                        }
                                        is VerificationResult.Error -> {
                                            android.util.Log.e("GuardianAI_Debug", "[Lock] Analyzer error: ${result.reason}")
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("GuardianAI_Debug", "[Lock] Error during frame analysis", e)
                                    cleanupAndSecure(shouldLock = true, logReason = "Fatal analyzer error")
                                } finally {
                                    imageProxy.close()
                                    isProcessingRef.set(false)
                                }
                            }
                        }

                        cameraProvider.unbindAll()
                        camera = cameraProvider.bindToLifecycle(context, CameraSelector.DEFAULT_FRONT_CAMERA, imageAnalysis)
                        capabilityManager = CameraCapabilityManager(camera!!)
                        
                        android.util.Log.d("GuardianAI_Debug", "[Camera] bindToLifecycle() SUCCESS")
                    } catch (e: Exception) {
                        addLog("[System] Camera error: ${e.message}")
                        cleanupAndSecure(shouldLock = true, logReason = "Camera initialization failed")
                    }
                }, ContextCompat.getMainExecutor(context))
            } else {
                cleanupAndSecure(shouldLock = true, logReason = "Camera permission missing")
            }
        }

        // Intruder block overlay
        if (showIntruderBlock) {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Text("Access Denied", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("This app is protected.", color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
        } else if (!showLockScreenOverlay) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                // Completely transparent UI. Authentication runs silently in the background.
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .pointerInput(Unit) {
                                detectTapGestures(onLongPress = { showDebugLogs = !showDebugLogs })
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Secured",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "Guardian AI",
                        color = MaterialTheme.colorScheme.onBackground, fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold, lineHeight = 28.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (session.lightingState == LightingState.VERY_DARK) "Lighting Too Dark" else "Verifying identity...",
                        color = if (session.lightingState == LightingState.VERY_DARK) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, 
                        fontSize = 14.sp, lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    LinearProgressIndicator(
                        color      = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier   = Modifier
                            .width(100.dp)
                            .height(3.dp)
                            .clip(CircleShape)
                    )
                }

                if (showDebugLogs) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f))
                            .padding(24.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Security Engine", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(
                                    "Close",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .clickable { showDebugLogs = false }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("State: $authState", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            Text("Lighting: ${session.lightingState}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            Text("Exposure: ${session.currentExposureIndex}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            debugLogs.forEach { log ->
                                Text(log, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 16.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }
}
