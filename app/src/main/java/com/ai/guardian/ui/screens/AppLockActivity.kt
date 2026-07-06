package com.ai.guardian.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.ai.guardian.data.entity.RecognitionHistoryEntity
import com.ai.guardian.ai.FaceBiometricEngine
import com.ai.guardian.ai.VerificationResult
import com.ai.guardian.services.GuardianForegroundService
import com.ai.guardian.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors


class AppLockActivity : ComponentActivity() {
    private var engine: FaceBiometricEngine? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val packageName = intent.getStringExtra("EXTRA_PACKAGE_NAME") ?: "Unknown App"
        android.util.Log.d("GuardianAI_Debug", "[Lock] onCreate() pkg=$packageName")

        engine = FaceBiometricEngine(this)

        setContent {
            GuardianAITheme {
                InvisibleLockScreen(packageName = packageName, engine = engine)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        android.util.Log.d("GuardianAI_Debug", "[Lock] onDestroy() — camera will be released by ProcessCameraProvider lifecycle binding")
        engine?.close()
    }

    @Composable
    fun InvisibleLockScreen(packageName: String, engine: FaceBiometricEngine?) {
        var showIntruderBlock by remember { mutableStateOf(false) }
        var showDebugLogs by remember { mutableStateOf(false) }
        val context = this@AppLockActivity
        val coroutineScope = rememberCoroutineScope()
        var hasProfiles by remember { mutableStateOf(false) }
        // AtomicBoolean for isProcessing: the analyzer runs on a camera executor thread
        // and reads/writes this flag concurrently with coroutine callbacks.
        val isProcessingRef = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
        var debugLogs by remember { mutableStateOf(listOf<String>("[System] Initiating Guardian Lock...")) }
        var timeoutJob by remember { mutableStateOf<Job?>(null) }
        val isCleanedUp = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

        var isUnlocked by remember { mutableStateOf(false) }
        var isScreenLocked by remember { mutableStateOf(false) }

        fun addLog(msg: String) {
            android.util.Log.d("GuardianAI_Debug", msg)
            debugLogs = (debugLogs + msg).takeLast(6)
        }

        fun cleanupAndSecure(shouldLock: Boolean, logReason: String, logType: String = "SECURITY") {
            if (isCleanedUp.getAndSet(true)) return
            
            timeoutJob?.cancel()
            
            if (shouldLock && !isUnlocked) {
                isScreenLocked = true
                addLog("[Security] $logReason. Locking.")
                
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val logDao = (application as GuardianApplication).container.recognitionHistoryDao
                        logDao.insertHistory(
                            com.ai.guardian.data.entity.RecognitionHistoryEntity(
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
                    } catch (e: Exception) {
                        android.util.Log.e("GuardianAI_Debug", "[Lock] Failed to write security log", e)
                    }
                }
                
                com.ai.guardian.services.GuardianAccessibilityService.lockDeviceScreen(context)
            }
            
            // Release CameraX and close activity
            finish()
        }

        fun resetTimeout() {
            timeoutJob?.cancel()
            timeoutJob = coroutineScope.launch {
                delay(com.ai.guardian.ai.FaceRecognitionConfig.AUTHENTICATION_TIMEOUT_MS)
                cleanupAndSecure(shouldLock = true, logReason = "Face scan timed out (no face detected for 7s)", logType = "TIMEOUT")
            }
        }

        // Camera setup and timeout live in a single LaunchedEffect.
        LaunchedEffect(Unit) {
            resetTimeout()

            // Step 1: Load face embeddings from the database ONCE.
            try {
                val dao = (application as GuardianApplication).container.faceDao
                val profiles = dao.getAllProfilesWithTemplates()
                if (profiles.isNotEmpty()) {
                    engine?.loadTemplates(profiles)
                    hasProfiles = true
                    addLog("[System] Loaded ${profiles.size} profile(s)")
                } else {
                    addLog("[System] WARNING: No profiles found!")
                    android.util.Log.w("GuardianAI_Debug", "[Lock] No face profiles found in database!")
                }
            } catch (e: Exception) {
                android.util.Log.e("GuardianAI_Debug", "[Lock] Failed to load faces", e)
                cleanupAndSecure(shouldLock = true, logReason = "Database failure during initialization")
                return@LaunchedEffect
            }

            // Step 2: Bind ImageAnalysis to the camera.
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED && engine != null
            ) {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                val executor = Executors.newSingleThreadExecutor()

                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setTargetResolution(android.util.Size(640, 480))
                            .build()

                        imageAnalysis.setAnalyzer(executor) { imageProxy: ImageProxy ->
                            // Guard: drop frame if already processing, done, or no embeddings
                            if (isProcessingRef.getAndSet(true) ||
                                !hasProfiles ||
                                isUnlocked ||
                                isScreenLocked ||
                                isCleanedUp.get()
                            ) {
                                imageProxy.close()
                                isProcessingRef.set(false)
                                return@setAnalyzer
                            }

                            coroutineScope.launch {
                                try {
                                    val currentEngine = engine
                                    if (currentEngine == null || isCleanedUp.get()) {
                                        return@launch
                                    }

                                    val result = currentEngine.analyzeFrame(imageProxy)
                                    
                                    if (isUnlocked || isScreenLocked || isCleanedUp.get()) return@launch

                                    when (result) {
                                        is VerificationResult.Success -> {
                                            // Progress made: reset timeout
                                            resetTimeout()

                                            addLog("[AI] Face detected.")
                                            
                                            val matchResult = currentEngine.matchAgainstCache(result.embedding)

                                            if (matchResult != null) {
                                                addLog("[AI] Match! Score: ${matchResult.second}. Unlocking.")
                                                isUnlocked = true
                                                
                                                withContext(Dispatchers.Main) {
                                                    com.ai.guardian.services.GuardianAccessibilityService.whitelistPackage(packageName)
                                                    val whitelistIntent = Intent(context, GuardianForegroundService::class.java).apply {
                                                        action = GuardianForegroundService.ACTION_WHITELIST_PACKAGE
                                                        putExtra(GuardianForegroundService.EXTRA_PACKAGE_NAME, packageName)
                                                    }
                                                    ContextCompat.startForegroundService(context, whitelistIntent)
                                                    cleanupAndSecure(shouldLock = false, logReason = "Unlocked")
                                                }
                                            } else {
                                                // Lock immediately for confirmed unknown face
                                                cleanupAndSecure(shouldLock = true, logReason = "Unknown face detected", logType = "UNAUTHORIZED_ACCESS")
                                            }
                                        }

                                        is VerificationResult.MultipleFaces -> {
                                            // Pause timeout & display warning
                                            timeoutJob?.cancel()
                                            addLog("Only one face should be visible.")
                                        }

                                        is VerificationResult.PoorQuality -> {
                                            // Progress made (face is being tracked/autofocused): reset timeout
                                            resetTimeout()
                                            addLog("Align Face: ${result.reason}")
                                        }

                                        is VerificationResult.NoFace -> {
                                            // No progress: do not reset timeout
                                            addLog("Position face in view...")
                                        }

                                        is VerificationResult.Error -> {
                                            android.util.Log.e("GuardianAI_Debug", "[Lock] Analyzer error: ${result.reason}")
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("GuardianAI_Debug", "[Lock] Error during frame analysis", e)
                                } finally {
                                    imageProxy.close()
                                    isProcessingRef.set(false)
                                }
                            }
                        }

                        android.util.Log.d("GuardianAI_Debug", "[Camera] unbindAll() called")
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            context,
                            CameraSelector.DEFAULT_FRONT_CAMERA,
                            imageAnalysis
                        )
                        android.util.Log.d("GuardianAI_Debug", "[Camera] bindToLifecycle() SUCCESS — FRONT camera + ImageAnalysis bound")
                    } catch (e: Exception) {
                        addLog("[System] Camera error: ${e.message}")
                        android.util.Log.e("GuardianAI_Debug", "[Lock] Camera bind error", e)
                        cleanupAndSecure(shouldLock = true, logReason = "Camera initialization failed")
                    }
                }, ContextCompat.getMainExecutor(context))
            } else {
                addLog("[System] Camera permission missing or engine null!")
                android.util.Log.e("GuardianAI_Debug", "[Lock] Camera permission denied or engine=null")
                cleanupAndSecure(shouldLock = true, logReason = "Camera permission missing or engine null")
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
        } else {
            // Clean verification overlay — no decorative pulse
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
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
                        "Verifying identity...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, lineHeight = 20.sp
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

                // Debug logs — hidden, revealed by long-press on lock icon
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
        // Block back — send user home instead of back into locked app
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }
}
