package com.ai.guardian.ui.screens

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ai.guardian.data.entity.AppLockEntity
import com.ai.guardian.ui.theme.*
import com.ai.guardian.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val categoryKeywords = mapOf(
    "Social"  to listOf("whatsapp","facebook","instagram","twitter","messenger","snapchat","telegram","reddit","discord","tiktok"),
    "Banking" to listOf("bank","finance","pay","cash","wallet","upi","paytm","gpay","phonepe","bhim","razorpay"),
    "Work"    to listOf("office","work","slack","teams","meet","email","gmail","outlook","zoom","notion","jira"),
    "Games"   to listOf("game","play","arcade","puzzle","clash","pubg","freefire","roblox","minecraft")
)

private fun resolveCategory(pkg: String, isProtected: Boolean): String {
    if (isProtected) return "Favorites"
    val lower = pkg.lowercase()
    for ((cat, keys) in categoryKeywords) {
        if (keys.any { lower.contains(it) }) return cat
    }
    return "Others"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectionScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val context     = LocalContext.current
    val lockedApps  by viewModel.lockedApps.collectAsState()
    var allApps     by remember { mutableStateOf<List<ApplicationInfo>>(emptyList()) }
    var isLoading   by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCat by remember { mutableStateOf("All") }
    val categories  = listOf("All", "Favorites", "Social", "Banking", "Work", "Games", "Others")

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            allApps = context.packageManager
                .getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
                .sortedBy { context.packageManager.getApplicationLabel(it).toString() }
            isLoading = false
        }
    }

    val filteredApps = remember(allApps, searchQuery, selectedCat, lockedApps) {
        allApps.filter { info ->
            val name        = context.packageManager.getApplicationLabel(info).toString()
            val pkg         = info.packageName
            val isProtected = lockedApps.any { it.packageName == pkg && it.isProtected }
            val cat         = resolveCategory(pkg, isProtected)
            val matchSearch = name.contains(searchQuery, ignoreCase = true) ||
                              pkg.contains(searchQuery, ignoreCase = true)
            val matchCat    = selectedCat == "All" ||
                              (selectedCat == "Favorites" && isProtected) ||
                              cat == selectedCat
            matchSearch && matchCat
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(24.dp))
            Text(
                "Protected Apps",
                fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground, lineHeight = 32.sp
            )
            val selectedCount = lockedApps.count { it.isProtected }
            Text(
                if (selectedCount == 0) "No apps selected" else "$selectedCount app${if (selectedCount > 1) "s" else ""} protected",
                fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp
            )
            Spacer(Modifier.height(16.dp))

            // Search
            OutlinedTextField(
                value         = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder   = { Text("Search apps...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp) },
                leadingIcon   = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(10.dp),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedTextColor        = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor      = MaterialTheme.colorScheme.onBackground,
                    focusedContainerColor   = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor      = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor    = Color.Transparent
                )
            )
            Spacer(Modifier.height(12.dp))
        }

        // Category chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { cat ->
                val sel = cat == selectedCat
                FilterChip(
                    selected = sel,
                    onClick  = { selectedCat = cat },
                    label    = { Text(cat, fontSize = 13.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal) },
                    shape    = RoundedCornerShape(8.dp),
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor      = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor          = MaterialTheme.colorScheme.primary,
                        containerColor              = MaterialTheme.colorScheme.surface,
                        labelColor                  = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    border   = FilterChipDefaults.filterChipBorder(
                        enabled              = true,
                        selected             = sel,
                        selectedBorderColor  = Color.Transparent,
                        borderColor          = Color.Transparent
                    )
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                }
            }
            filteredApps.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("No apps match", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, lineHeight = 24.sp)
                        Text("Try adjusting your search or filter.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp, textAlign = TextAlign.Center)
                    }
                }
            }
            else -> {
                Surface(
                    modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                    shape    = RoundedCornerShape(14.dp),
                    color    = MaterialTheme.colorScheme.surface
                ) {
                    LazyColumn {
                        items(filteredApps, key = { it.packageName }) { info ->
                            val pkg         = info.packageName
                            val appName     = context.packageManager.getApplicationLabel(info).toString()
                            val isProtected = lockedApps.any { it.packageName == pkg && it.isProtected }

                            AppRow(
                                packageName = pkg,
                                appName     = appName,
                                isProtected = isProtected,
                                info        = info,
                                onToggle    = { checked ->
                                    viewModel.toggleAppLock(AppLockEntity(packageName = pkg, appName = appName, isProtected = checked))
                                }
                            )
                            if (info != filteredApps.last()) {
                                HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun AppRow(
    packageName: String,
    appName: String,
    isProtected: Boolean,
    info: ApplicationInfo,
    onToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            AndroidView(
                factory = { ctx ->
                    android.widget.ImageView(ctx).apply { scaleType = android.widget.ImageView.ScaleType.FIT_CENTER }
                },
                update = { iv ->
                    try { iv.setImageDrawable(context.packageManager.getApplicationIcon(info)) }
                    catch (_: Exception) { iv.setImageResource(android.R.drawable.sym_def_app_icon) }
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(appName, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground, lineHeight = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(packageName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked         = isProtected,
            onCheckedChange = onToggle,
            colors          = SwitchDefaults.colors(
                checkedThumbColor   = Color.White,
                checkedTrackColor   = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant
            )
        )
    }
}
