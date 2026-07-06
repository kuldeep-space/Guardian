package com.ai.guardian.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ai.guardian.GuardianApplication
import com.ai.guardian.data.entity.RecognitionHistoryEntity
import com.ai.guardian.ui.theme.*
import com.ai.guardian.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(viewModel: MainViewModel, navController: NavController) {
    val context = LocalContext.current
    val settings  by viewModel.settings.collectAsState()
    val enrolledFaces by viewModel.enrolledFaces.collectAsState()
    val lockedApps    by viewModel.lockedApps.collectAsState()

    val logs by viewModel.recognitionHistory.collectAsState()

    val greetingName       = remember(enrolledFaces) { enrolledFaces.firstOrNull()?.profile?.name ?: "there" }
    val appsProtectedCount = remember(lockedApps)  { lockedApps.count { it.isProtected } }
    val todayUnlocks       = remember(logs) { logs.count { it.authResult } }
    val blockedAttempts    = remember(logs) { logs.count { !it.authResult } }

    // Greeting based on hour
    val greeting = remember {
        when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
            in 0..11  -> "Good morning"
            in 12..17 -> "Good afternoon"
            else      -> "Good evening"
        }
    }
    val dayLabel = remember {
        SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {

        // ── 1. Header ────────────────────────────────────────────────────────
        item {
            Column {
                Text(
                    text = "Guardian AI",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$greeting, $greetingName",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    lineHeight = 32.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = dayLabel,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }
        }

        // ── 2. Hero Protection Card ──────────────────────────────────────────
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (settings.isProtectionEnabled) Icons.Default.Security else Icons.Default.GppBad,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (settings.isProtectionEnabled) "Protection Active" else "Protection Disabled",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    lineHeight = 22.sp
                                )
                                Text(
                                    text = if (enrolledFaces.isEmpty()) "Enroll a profile to begin" else "Face recognition enabled",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                        Switch(
                            checked = settings.isProtectionEnabled,
                            onCheckedChange = { enabled ->
                                if (enrolledFaces.isNotEmpty()) {
                                    viewModel.toggleGlobalProtection(enabled)
                                }
                            },
                            enabled = enrolledFaces.isNotEmpty(),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor     = Color.White,
                                checkedTrackColor     = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor   = MaterialTheme.colorScheme.onSurfaceVariant,
                                uncheckedTrackColor   = MaterialTheme.colorScheme.surfaceVariant,
                                disabledCheckedTrackColor = MaterialTheme.colorScheme.outlineVariant,
                                disabledUncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                    }

                    if (enrolledFaces.isEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "No face profiles enrolled. Add one from the Face Security tab.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            lineHeight = 18.sp
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        HeroStat(label = "Apps protected", value = "$appsProtectedCount", modifier = Modifier.weight(1f))
                        HeroStat(label = "Profiles enrolled", value = "${enrolledFaces.size}", modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // ── 3. Quick Actions ─────────────────────────────────────────────────
        item {
            Column {
                Text("Quick Actions", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, lineHeight = 24.sp)
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionButton(
                        label = "Add Face",
                        icon  = Icons.Default.PersonAdd,
                        modifier = Modifier.weight(1f),
                        onClick = { navController.navigate("face_registration") }
                    )
                    QuickActionButton(
                        label = "Test Face",
                        icon  = Icons.Default.Radar,
                        modifier = Modifier.weight(1f),
                        onClick = { navController.navigate("face_recognition_test") }
                    )
                }
            }
        }

        // ── 4. Security Summary ──────────────────────────────────────────────
        item {
            Column {
                Text("Security Summary", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, lineHeight = 24.sp)
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SummaryRow(
                            icon  = Icons.Default.Lock,
                            label = "Protected Apps",
                            value = "$appsProtectedCount"
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)
                        SummaryRow(
                            icon  = Icons.Default.Face,
                            label = "Registered Profiles",
                            value = "${enrolledFaces.size}"
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)
                        SummaryRow(
                            icon  = Icons.Default.Verified,
                            label = "Today's Unlocks",
                            value = "$todayUnlocks"
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)
                        SummaryRow(
                            icon  = Icons.Default.Block,
                            label = "Blocked Attempts",
                            value = "$blockedAttempts",
                            valueColor = if (blockedAttempts > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }

        // ── 5. Recent Activity ───────────────────────────────────────────────
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recent Activity", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, lineHeight = 24.sp)
                    if (logs.isNotEmpty()) {
                        Text(
                            text = "View all",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { navController.navigate("security_logs") }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (logs.isEmpty()) {
                    DashboardEmptyActivity()
                } else {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            logs.take(3).forEachIndexed { index, log ->
                                val isBlocked = !log.authResult
                                val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
                                ActivityLogRow(
                                    description = if (isBlocked) "Blocked: ${log.failureReason}" else "Unlocked ${log.protectedAppPackage}",
                                    time        = sdf.format(Date(log.timestamp)),
                                    isBlocked   = isBlocked
                                )
                                if (index < minOf(2, logs.size - 1)) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ── Private composables ──────────────────────────────────────────────────────

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, lineHeight = 28.sp)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
    }
}

@Composable
private fun QuickActionButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SummaryRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onBackground
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground, lineHeight = 20.sp)
        }
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            lineHeight = 26.sp
        )
    }
}

@Composable
private fun ActivityLogRow(description: String, time: String, isBlocked: Boolean) {
    val dotColor = if (isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = description,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            lineHeight = 20.sp,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(8.dp))
        Text(time, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
    }
}

@Composable
private fun DashboardEmptyActivity() {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "All Clear",
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground, lineHeight = 22.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "No security events recorded yet.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp, textAlign = TextAlign.Center
            )
        }
    }
}
