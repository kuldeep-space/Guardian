package com.ai.guardian.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.guardian.GuardianApplication
import com.ai.guardian.security.ProtectedAction

/**
 * GuardianLockScreen — shown on every launch of the Guardian app.
 *
 * The Child CANNOT access any Guardian settings or administration UI
 * until the paired Parent authorizes access via this screen.
 *
 * No local override exists. No PIN, no biometric, no emergency bypass.
 * Only a valid, non-expired, cryptographically-verified Parent token
 * grants access.
 */
@Composable
fun GuardianLockScreen(
    parentUuid: String,
    onAccessGranted: () -> Unit
) {
    val context = LocalContext.current
    val container = (context.applicationContext as GuardianApplication).container
    val gate = container.protectedActionGate

    var isWaiting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var activeRequestId by remember { mutableStateOf("") }
    var expiresAtTimestamp by remember { mutableStateOf(0L) }
    var remainingSeconds by remember { mutableStateOf(60) }

    // Recover pending request if it exists in Firestore/Room on launch
    LaunchedEffect(parentUuid) {
        gate.recoverPendingRequests(
            parentUuid = parentUuid,
            onRequestRecovered = { reqId, expiresAt ->
                activeRequestId = reqId
                expiresAtTimestamp = expiresAt
                isWaiting = true
                statusMessage = "Waiting for Parent Approval..."
            },
            onApproved = {
                isWaiting = false
                onAccessGranted()
            },
            onDenied = { reason ->
                isWaiting = false
                statusMessage = "Access denied: $reason"
            }
        )
    }

    // Live countdown timer for the waiting state
    LaunchedEffect(isWaiting, expiresAtTimestamp) {
        if (isWaiting && expiresAtTimestamp > 0L) {
            remainingSeconds = ((expiresAtTimestamp - System.currentTimeMillis()) / 1000).coerceAtLeast(0).toInt()
            while (remainingSeconds > 0 && isWaiting) {
                kotlinx.coroutines.delay(1000)
                remainingSeconds = ((expiresAtTimestamp - System.currentTimeMillis()) / 1000).coerceAtLeast(0).toInt()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Lock Icon
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(Modifier.height(28.dp))

        Text(
            "Guardian is Locked",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        Text(
            "This device is protected by Guardian. Only the paired device can authorize access to Guardian settings.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(40.dp))

        if (!isWaiting) {
            // Request access button
            Button(
                onClick = {
                    isWaiting = true
                    statusMessage = "Waiting for approval…"
                    gate.request(
                        action = ProtectedAction.OPEN_GUARDIAN_SETTINGS,
                        parentUuid = parentUuid,
                        onRequestCreated = { reqId, expiresAt ->
                            activeRequestId = reqId
                            expiresAtTimestamp = expiresAt
                        },
                        onApproved = {
                            isWaiting = false
                            onAccessGranted()
                        },
                        onDenied = { reason ->
                            isWaiting = false
                            statusMessage = "Access denied: $reason"
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.PhoneAndroid, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Request Access", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        } else {
            // Waiting state
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            statusMessage,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "${remainingSeconds}s",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            TextButton(onClick = {
                if (activeRequestId.isNotEmpty()) {
                    gate.cancelRequest(activeRequestId)
                }
                isWaiting = false
                statusMessage = ""
            }) {
                Text("Cancel", color = MaterialTheme.colorScheme.error)
            }
        }

        if (statusMessage.isNotEmpty() && !isWaiting) {
            Spacer(Modifier.height(16.dp))
            Text(
                statusMessage,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
    }
}
