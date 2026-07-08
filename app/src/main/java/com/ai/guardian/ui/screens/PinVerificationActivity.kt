package com.ai.guardian.ui.screens

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.guardian.GuardianApplication
import com.ai.guardian.security.AuditEvent
import com.ai.guardian.security.MaintenanceModeManager
import com.ai.guardian.security.SecurityPinManager
import com.ai.guardian.ui.theme.GuardianAITheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-screen PIN verification activity launched by:
 *   1. GuardianAccessibilityService when the child navigates to System Settings
 *      while a Security PIN is configured and maintenance mode is not active.
 *   2. Any future intercept point that needs an out-of-process PIN gate.
 *
 * On success:  calls MaintenanceModeManager.startMaintenanceMode() and returns RESULT_OK.
 * On cancel:   returns RESULT_CANCELED. Callers should redirect the user to Home.
 */
class PinVerificationActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val actionName = intent.getStringExtra("EXTRA_ACTION_NAME") ?: "perform this action"

        setContent {
            GuardianAITheme {
                PinVerificationScreen(actionName)
            }
        }
    }

    @Composable
    fun PinVerificationScreen(actionName: String) {
        var enteredPin by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf("") }
        val scope = rememberCoroutineScope()

        val container = (applicationContext as GuardianApplication).container

        fun submitPin() {
            val pin = enteredPin
            if (pin.length < 4) return
            scope.launch {
                val settings = withContext(Dispatchers.IO) {
                    container.deviceSettingsDao.getSettings()
                }

                if (settings == null || !settings.isPinConfigured) {
                    // No PIN configured: allow without restriction
                    MaintenanceModeManager.startMaintenanceMode()
                    setResult(RESULT_OK)
                    finish()
                    return@launch
                }

                val pinManager = SecurityPinManager(this@PinVerificationActivity)
                val isCorrect = withContext(Dispatchers.IO) {
                    pinManager.verifyPin(
                        pin,
                        settings.securityPinHash ?: "",
                        settings.securityPinIv ?: "",
                        settings.securityPinSalt ?: ""
                    )
                }

                if (isCorrect) {
                    MaintenanceModeManager.startMaintenanceMode()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@PinVerificationActivity,
                            "PIN verified. Maintenance mode active (60s).",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    setResult(RESULT_OK)
                    finish()
                } else {
                    errorMessage = "Incorrect Security PIN"
                    enteredPin = ""
                    // Audit the failed attempt
                    withContext(Dispatchers.IO) {
                        container.tamperDetectionManager.log(
                            AuditEvent.SECURITY_PIN_ATTEMPT_FAILED,
                            "Failed PIN attempt for action: $actionName"
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 40.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Guardian PIN Required",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Enter PIN to $actionName",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // PIN dot indicator
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 6) {
                        val active = i < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                        )
                    }
                }

                if (errorMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }

            // Numpad
            Column(
                modifier = Modifier.padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("Cancel", "0", "Back")
                )

                for (row in rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (key in row) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (key) {
                                            "Cancel" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                            "Back" -> Color.Transparent
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    )
                                    .clickable {
                                        when (key) {
                                            "Cancel" -> {
                                                // Redirect to Home on cancel
                                                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                                    addCategory(Intent.CATEGORY_HOME)
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                startActivity(homeIntent)
                                                setResult(RESULT_CANCELED)
                                                finish()
                                            }
                                            "Back" -> {
                                                if (enteredPin.isNotEmpty()) {
                                                    enteredPin = enteredPin.dropLast(1)
                                                    errorMessage = ""
                                                }
                                            }
                                            else -> {
                                                if (enteredPin.length < 6) {
                                                    errorMessage = ""
                                                    val newPin = enteredPin + key
                                                    enteredPin = newPin
                                                    // Auto-submit at 4 or 6 digits
                                                    if (newPin.length == 4 || newPin.length == 6) {
                                                        submitPin()
                                                    }
                                                }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                when (key) {
                                    "Back" -> Icon(
                                        imageVector = Icons.Default.Backspace,
                                        contentDescription = "Backspace",
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                    else -> Text(
                                        key,
                                        fontSize = if (key == "Cancel") 13.sp else 24.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (key == "Cancel") MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
