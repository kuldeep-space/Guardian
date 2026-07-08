package com.ai.guardian.ui.navigation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.navigation
import com.ai.guardian.GuardianApplication
import com.ai.guardian.ui.screens.DashboardScreen
import com.ai.guardian.ui.screens.BiometricSettingsScreen
import com.ai.guardian.ui.screens.GuardianLockScreen
import com.ai.guardian.ui.screens.SettingsScreen
import com.ai.guardian.ui.theme.*
import com.ai.guardian.viewmodel.MainViewModel
import com.ai.guardian.viewmodel.DeviceViewModel
import com.ai.guardian.ui.screens.device.PairDeviceScreen
import com.ai.guardian.ui.screens.device.RemoteDeviceScreen

private data class NavTab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val topLevelTabs = listOf(
    NavTab("dashboard",    "Home",     Icons.Default.Home),
    NavTab("face_security","Face",     Icons.Default.Face),
    NavTab("protected_apps","Apps",    Icons.Default.Lock),
    NavTab("settings",     "Settings", Icons.Default.Settings)
)

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val container = (context.applicationContext as GuardianApplication).container
    val pairedDevices by container.pairedDeviceDao.getAllPairedDevices().collectAsState(initial = emptyList())

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in topLevelTabs.map { it.route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    topLevelTabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    navController.navigate(tab.route) {
                                        // Pop up to the start destination of the current graph (main_tabs)
                                        val currentGraph = navController.currentBackStackEntry?.destination?.parent
                                        val startDestinationId = currentGraph?.startDestinationId
                                            ?: navController.graph.findStartDestination().id

                                        popUpTo(startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState    = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    tab.icon,
                                    contentDescription = tab.label,
                                    modifier = Modifier
                                )
                            },
                            label = {
                                Text(
                                    tab.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor   = MaterialTheme.colorScheme.primary,
                                selectedTextColor   = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor      = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->

        // ── Global PIN Verification Dialog ────────────────────────────────────────
        val pendingAction = viewModel.pinProtectedAction
        val pendingActionName = viewModel.pinProtectedActionName
        if (pendingAction != null) {
            var pinEntry by remember { mutableStateOf("") }
            var pinError by remember { mutableStateOf("") }
            val scope = rememberCoroutineScope()
            val ctx = LocalContext.current

            AlertDialog(
                onDismissRequest = {
                    viewModel.pinProtectedAction = null
                    pinEntry = ""
                    pinError = ""
                },
                title = { Text("Guardian PIN Required") },
                text = {
                    Column {
                        Text("Enter your Security PIN to $pendingActionName.")
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = pinEntry,
                            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) { pinEntry = it; pinError = "" } },
                            label = { Text("Security PIN") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            isError = pinError.isNotEmpty(),
                            supportingText = if (pinError.isNotEmpty()) {{ Text(pinError) }} else null
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            val settings = withContext(Dispatchers.IO) {
                                (ctx.applicationContext as com.ai.guardian.GuardianApplication).container.deviceSettingsDao.getSettings()
                            }
                            if (settings != null && settings.isPinConfigured) {
                                val pinManager = com.ai.guardian.security.SecurityPinManager(ctx)
                                val ok = withContext(Dispatchers.IO) {
                                    pinManager.verifyPin(
                                        pinEntry,
                                        settings.securityPinHash ?: "",
                                        settings.securityPinIv ?: "",
                                        settings.securityPinSalt ?: ""
                                    )
                                }
                                if (ok) {
                                    com.ai.guardian.security.MaintenanceModeManager.startMaintenanceMode()
                                    val action = viewModel.pinProtectedAction
                                    viewModel.pinProtectedAction = null
                                    pinEntry = ""
                                    action?.invoke()
                                } else {
                                    pinError = "Incorrect PIN"
                                    pinEntry = ""
                                    (ctx.applicationContext as com.ai.guardian.GuardianApplication).container
                                        .tamperDetectionManager
                                        .log(com.ai.guardian.security.AuditEvent.SECURITY_PIN_ATTEMPT_FAILED, "In-app PIN failure for: $pendingActionName")
                                }
                            }
                        }
                    }) { Text("Confirm") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        viewModel.pinProtectedAction = null
                        pinEntry = ""
                        pinError = ""
                    }) { Text("Cancel") }
                }
            )
        }
        NavHost(
            navController    = navController,
            startDestination = "routing",
            modifier         = Modifier.padding(innerPadding)
        ) {
            composable("routing") {
                val devicesState by container.pairedDeviceDao.getAllPairedDevices().collectAsState(initial = null)

                androidx.compose.runtime.LaunchedEffect(Unit) {
                    androidx.compose.runtime.snapshotFlow { devicesState }
                        .filterNotNull()
                        .first()
                        .let { devices ->
                            val targetRoute = when {
                                !hasAllPermissions(context) -> "permissions"
                                devices.any { it.isParentDevice } -> "guardian_lock"
                                else -> "main_tabs"
                            }
                            navController.navigate(targetRoute) {
                                popUpTo("routing") { inclusive = true }
                            }
                        }
                }
            }

            composable("permissions") {
                com.ai.guardian.ui.screens.PermissionsScreen(
                    onAllPermissionsGranted = {
                        navController.navigate("main_tabs") {
                            popUpTo("permissions") { inclusive = true }
                        }
                    }
                )
            }

            // Guardian lock screen — shown on every launch when a Parent is paired.
            // Child cannot proceed without a valid, non-expired Parent authorization token.
            composable("guardian_lock") {
                val parentDevice = pairedDevices.firstOrNull { it.isParentDevice }
                if (parentDevice != null) {
                    GuardianLockScreen(
                        parentUuid = parentDevice.uuid,
                        onAccessGranted = {
                            navController.navigate("main_tabs") {
                                popUpTo("guardian_lock") { inclusive = true }
                            }
                        }
                    )
                }
            }
            
            navigation(startDestination = "dashboard", route = "main_tabs") {
                composable("dashboard") {
                    DashboardScreen(viewModel = viewModel, navController = navController)
                }
                composable("face_security") {
                    val faces = viewModel.enrolledFaces.collectAsState().value
                    BiometricSettingsScreen(
                        enrolledFaces    = faces,
                        onRegisterClick  = {
                            viewModel.reenrollProfileId   = null
                            viewModel.reenrollProfileName = null
                            navController.navigate("face_registration")
                        },
                        onTestClick      = { navController.navigate("face_recognition_test") },
                        onReenrollClick  = { face ->
                            viewModel.reenrollProfileId   = face.profile.id
                            viewModel.reenrollProfileName = face.profile.name
                            navController.navigate("face_registration")
                        },
                        onRenameClick    = { face, name -> viewModel.renameFace(face.profile.id, name) },
                        onColorClick     = { face, color -> viewModel.updateAvatarColor(face.profile.id, color.toArgb()) },
                        onDeleteClick    = { face -> viewModel.deleteFaceById(face.profile.id) },
                        onBack           = { navController.popBackStack() }
                    )
                }
                composable("protected_apps") {
                    com.ai.guardian.ui.screens.AppSelectionScreen(
                        viewModel = viewModel,
                        onBack    = {
                            navController.navigate("dashboard") {
                                popUpTo("dashboard") { inclusive = false }
                            }
                        }
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        viewModel         = viewModel,
                        onNavigateToLogs  = { navController.navigate("security_logs") },
                        onNavigateToPairDevice = { navController.navigate("pair_device") },
                        onNavigateToRemoteDevice = { uuid, name ->
                            val safeName = java.net.URLEncoder.encode(name, "UTF-8")
                            navController.navigate("remote_device/$uuid/$safeName")
                        }
                    )
                }
            }
            composable("pair_device") {
                val deviceViewModel: DeviceViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                PairDeviceScreen(
                    deviceViewModel = deviceViewModel,
                    mainViewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "remote_device/{uuid}/{name}",
                arguments = listOf(
                    androidx.navigation.navArgument("uuid") { type = androidx.navigation.NavType.StringType },
                    androidx.navigation.navArgument("name") { type = androidx.navigation.NavType.StringType }
                )
            ) { backStackEntry ->
                val uuid = backStackEntry.arguments?.getString("uuid") ?: ""
                val name = backStackEntry.arguments?.getString("name")?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: "Remote Device"
                RemoteDeviceScreen(
                    deviceUuid = uuid,
                    deviceName = name,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("security_logs") {
                com.ai.guardian.ui.screens.SecurityLogsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("face_registration") {
                com.ai.guardian.ui.screens.FaceRegistrationScreen(
                    viewModel             = viewModel,
                    onRegistrationSuccess = {
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("face_recognition_test") {
                com.ai.guardian.ui.screens.FaceRecognitionTestScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

private fun hasAllPermissions(context: Context): Boolean {
    val hasCamera  = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val hasOverlay = Settings.canDrawOverlays(context)
    var hasA11y    = false
    val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
    if (enabledServices != null) {
        val splitter = android.text.TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            val c = splitter.next()
            if (c.equals("${context.packageName}/${context.packageName}.services.GuardianAccessibilityService", ignoreCase = true) ||
                c.equals("${context.packageName}/com.ai.guardian.services.GuardianAccessibilityService", ignoreCase = true)) {
                hasA11y = true; break
            }
        }
    }
    return hasCamera && hasOverlay && hasA11y
}
