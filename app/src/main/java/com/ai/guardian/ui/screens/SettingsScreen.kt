package com.ai.guardian.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.guardian.ui.theme.*
import com.ai.guardian.viewmodel.DeviceViewModel
import com.ai.guardian.viewmodel.MainViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    deviceViewModel: DeviceViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onNavigateToLogs: () -> Unit,
    onNavigateToPairDevice: () -> Unit,
    onNavigateToRemoteDevice: (String, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(24.dp))
            Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, lineHeight = 32.sp)
            Spacer(Modifier.height(24.dp))
        }

        // Collect settings once for the whole screen
        val settings by viewModel.settings.collectAsState()
        
        val context = androidx.compose.ui.platform.LocalContext.current
        val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
        var isDeviceAdmin by remember { mutableStateOf(com.ai.guardian.security.DevicePolicyController(context).isDeviceAdmin) }

        DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    isDeviceAdmin = com.ai.guardian.security.DevicePolicyController(context).isDeviceAdmin
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        // ── Security ─────────────────────────────────────────────────────────
        SettingsGroup(label = "Security") {
            SettingsListItem(
                icon     = Icons.Default.History,
                title    = "Security Logs",
                subtitle = "View access events and blocked attempts",
                onClick  = onNavigateToLogs,
                trailing = { Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
            )
            SettingsDivider()
            SettingsListItem(
                icon     = Icons.Default.Lock,
                title    = "App Lock Timeout",
                subtitle = "Require re-scan after inactivity",
                trailing = { Text("7 s", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium) }
            )
            SettingsDivider()
            SettingsListItem(
                icon     = Icons.Default.AdminPanelSettings,
                title    = "Device Administrator",
                subtitle = if (isDeviceAdmin) "Active - Protection enabled" else "Inactive - Tap to enable",
                onClick  = {
                    if (!isDeviceAdmin) {
                        viewModel.runWithPinProtection("Enable Device Admin") {
                            val intent = android.content.Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, android.content.ComponentName(context, com.ai.guardian.services.GuardianDeviceAdminReceiver::class.java))
                                putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Guardian requires Device Administrator privileges to prevent unauthorized uninstallation by children.")
                            }
                            context.startActivity(intent)
                        }
                    } else {
                        viewModel.runWithPinProtection("Disable Device Admin") {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
                            context.startActivity(intent)
                        }
                    }
                },
                trailing = {
                    Switch(
                        checked = isDeviceAdmin,
                        onCheckedChange = null, // Handled by onClick
                        colors = SwitchDefaults.colors(
                            checkedThumbColor   = MaterialTheme.colorScheme.background,
                            checkedTrackColor   = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
            )
        }

        Spacer(Modifier.height(20.dp))

        // ── Guardian Security PIN ─────────────────────────────────────────────
        SettingsGroup(label = "Guardian Security PIN") {
            val pinConfigured = settings.isPinConfigured
            val pinVersion    = settings.pinVersion
            val pinUpdatedAt  = settings.pinUpdatedAt
            
            var showInitialPinDialog by remember { mutableStateOf(false) }

            val lastUpdatedText = remember(pinUpdatedAt) {
                if (pinUpdatedAt > 0L) {
                    val sdf = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
                    sdf.format(java.util.Date(pinUpdatedAt))
                } else "Never"
            }

            SettingsListItem(
                icon     = Icons.Default.Pin,
                title    = "Security PIN",
                subtitle = if (pinConfigured)
                    "Configured · v$pinVersion · Updated $lastUpdatedText"
                else
                    "Not configured — Tap to set initial PIN",
                onClick  = { if (!pinConfigured) showInitialPinDialog = true },
                trailing = {
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                        color = if (pinConfigured)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            if (pinConfigured) "Active" else "Not Set",
                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (pinConfigured)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            )
            
            if (showInitialPinDialog) {
                var newPin by remember { mutableStateOf("") }
                val context = androidx.compose.ui.platform.LocalContext.current
                AlertDialog(
                    onDismissRequest = { showInitialPinDialog = false },
                    title = { Text("Set Initial PIN") },
                    text = {
                        Column {
                            Text("Enter a 4-6 digit Guardian PIN. This will sync to the Parent device upon pairing.")
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = newPin,
                                onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) newPin = it },
                                label = { Text("Security PIN") },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword)
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.setInitialChildPin(newPin, context)
                                showInitialPinDialog = false
                            },
                            enabled = newPin.length in 4..6
                        ) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showInitialPinDialog = false }) { Text("Cancel") }
                    }
                )
            }
            SettingsDivider()
            SettingsListItem(
                icon     = Icons.Default.Info,
                title    = "How it works",
                subtitle = "Guardian PIN is set by the Parent remotely. It must be entered before changing any Guardian settings, disabling accessibility, or uninstalling Guardian.",
            )
        }

        Spacer(Modifier.height(20.dp))

        // ── Face Recognition ─────────────────────────────────────────────────
        SettingsGroup(label = "Face Recognition") {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text("Matching Threshold", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
                    Text(String.format("%.2f", settings.matchingThreshold), fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "Higher value = stricter matching. Recommended: 0.65–0.80",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp
                )
                Slider(
                    value         = settings.matchingThreshold,
                    onValueChange = { viewModel.updateMatchingThreshold(it) },
                    valueRange    = 0.65f..0.90f,
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = SliderDefaults.colors(
                        thumbColor        = MaterialTheme.colorScheme.primary,
                        activeTrackColor  = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor= MaterialTheme.colorScheme.outlineVariant
                    )
                )
            }
            
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)
            
            SettingsListItem(
                icon     = Icons.Default.Visibility,
                title    = "Show Lock Screen During Face Scan",
                subtitle = "When disabled, authentication happens silently in the background.",
                trailing = {
                    Switch(
                        checked = settings.showLockScreenOverlay,
                        onCheckedChange = { viewModel.updateShowLockScreenOverlay(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor   = MaterialTheme.colorScheme.background,
                            checkedTrackColor   = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
            )
            
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)
            
            SettingsListItem(
                icon     = Icons.Default.Timer,
                title    = "Smart Re-authentication Cache",
                subtitle = "Reduces repeated face scans. Trusted sessions expire randomly between 1 to 3 minutes.",
                trailing = {
                    Switch(
                        checked = settings.trustedAuthDurationMinutes > 0,
                        onCheckedChange = { viewModel.updateTrustedAuthDurationMinutes(if (it) 1 else 0) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor   = MaterialTheme.colorScheme.background,
                            checkedTrackColor   = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
            )

            HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)

            SettingsListItem(
                icon     = Icons.Default.FaceRetouchingNatural,
                title    = "Liveness Detection",
                subtitle = "Requires the face to blink or move. Prevents photo spoofing. Guardian PIN required to change.",
                trailing = {
                    Switch(
                        checked = settings.isLivenessDetectionEnabled,
                        onCheckedChange = { viewModel.updateLivenessDetection(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor   = MaterialTheme.colorScheme.background,
                            checkedTrackColor   = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
            )
        }

        Spacer(Modifier.height(20.dp))
        
        // ── Device Management ────────────────────────────────────────────────
        val pairedDevices by deviceViewModel.pairedDevices.collectAsState()
        val enrolledFaces by viewModel.enrolledFaces.collectAsState()
        val isDeviceManagementEnabled = enrolledFaces.isNotEmpty()
        
        SettingsGroup(label = "Device Management") {
            SettingsListItem(
                icon     = Icons.Default.QrCodeScanner,
                title    = "Connect / This Device",
                subtitle = if (isDeviceManagementEnabled) "Manage remote connection with other devices" else "Register a face first to enable",
                onClick  = { if (isDeviceManagementEnabled) onNavigateToPairDevice() },
                trailing = { Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
            )
            
            if (pairedDevices.isNotEmpty()) {
                SettingsDivider()
                pairedDevices.forEachIndexed { index, device ->
                    SettingsListItem(
                        icon     = Icons.Default.Smartphone,
                        title    = device.deviceName,
                        subtitle = "Connected via Guardian",
                        onClick  = { onNavigateToRemoteDevice(device.uuid, device.deviceName) },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    viewModel.runWithPinProtection("unpair ${device.deviceName}") {
                                        deviceViewModel.removePairedDevice(device)
                                    }
                                }) {
                                    Icon(Icons.Default.Delete, "Remove Device", tint = MaterialTheme.colorScheme.error)
                                }
                                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            }
                        }
                    )
                    if (index < pairedDevices.lastIndex) {
                        SettingsDivider()
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Appearance ────────────────────────────────────────────────────────
        SettingsGroup(label = "Appearance") {
            val themeMode by viewModel.themeMode.collectAsState()
            val options = listOf(
                com.ai.guardian.ui.theme.ThemeMode.SYSTEM to "System Default",
                com.ai.guardian.ui.theme.ThemeMode.LIGHT  to "Light",
                com.ai.guardian.ui.theme.ThemeMode.DARK   to "Dark"
            )
            Column {
                options.forEachIndexed { idx, (mode, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setThemeMode(mode) }
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            label,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground,
                            lineHeight = 20.sp
                        )
                        RadioButton(
                            selected = themeMode == mode,
                            onClick  = { viewModel.setThemeMode(mode) },
                            colors   = RadioButtonDefaults.colors(
                                selectedColor   = MaterialTheme.colorScheme.primary,
                                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                    if (idx < options.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        SettingsGroup(label = "Privacy & Advanced") {
            var failSecure by remember { mutableStateOf(true) }
            SettingsListItem(
                icon     = Icons.Default.SecurityUpdate,
                title    = "Fail Secure",
                subtitle = "Lock on invalid face detection",
                trailing = {
                    Switch(
                        checked         = failSecure,
                        onCheckedChange = { failSecure = it },
                        colors          = SwitchDefaults.colors(
                            checkedThumbColor   = MaterialTheme.colorScheme.background,
                            checkedTrackColor   = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
            )
            SettingsDivider()
            SettingsListItem(
                icon     = Icons.Default.CleaningServices,
                title    = "Clear Cache",
                subtitle = "Re-index model and database",
                onClick  = {}
            )
        }

        Spacer(Modifier.height(20.dp))

        // ── About ─────────────────────────────────────────────────────────────
        SettingsGroup(label = "About") {
            SettingsListItem(
                icon     = Icons.Default.Info,
                title    = "Version",
                subtitle = "Guardian AI",
                trailing = { Text("1.0.0", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            )
            SettingsDivider()
            SettingsListItem(
                icon     = Icons.Default.Description,
                title    = "Open Source Licenses",
                subtitle = "Third-party libraries used",
                onClick  = {},
                trailing = { Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
            )
        }

        Spacer(Modifier.height(40.dp))
    }
}

// ── Reusable composables ─────────────────────────────────────────────────────

@Composable
fun SettingsGroup(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(
            label,
            fontSize = 12.sp, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
        )
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)
}

@Composable
fun SettingsListItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val clickMod = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickMod)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground, lineHeight = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

// Keep legacy names so old callers still compile during migration
@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) = SettingsGroup(label = title, content = content)

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) = SettingsListItem(icon = icon, title = title, subtitle = subtitle, trailing = trailing, onClick = onClick)
