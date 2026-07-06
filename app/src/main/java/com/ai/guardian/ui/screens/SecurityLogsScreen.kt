package com.ai.guardian.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.guardian.GuardianApplication
import com.ai.guardian.data.entity.RecognitionHistoryEntity
import com.ai.guardian.ui.theme.*
import com.ai.guardian.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar

@Composable
fun SecurityLogsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val logs by viewModel.recognitionHistory.collectAsState()
    val scope    = rememberCoroutineScope()
    var search   by remember { mutableStateOf("") }
    var filter   by remember { mutableStateOf("All") }
    var showClearDialog by remember { mutableStateOf(false) }
    val filters  = listOf("All", "Blocked", "Unlocked", "System")

    fun isBlocked(log: RecognitionHistoryEntity) = !log.authResult
    fun isUnlock(log: RecognitionHistoryEntity)  = log.authResult

    val filtered = remember(logs, search, filter) {
        logs.filter { log ->
            val description = if (!log.authResult) "Blocked: ${log.failureReason}" else "Unlocked ${log.protectedAppPackage}"
            val matchSearch = description.contains(search, ignoreCase = true) || log.recognitionType.contains(search, ignoreCase = true)
            val matchFilter = when (filter) {
                "Blocked"  -> isBlocked(log)
                "Unlocked" -> isUnlock(log)
                "System"   -> !isBlocked(log) && !isUnlock(log)
                else       -> true
            }
            matchSearch && matchFilter
        }
    }

    // Group by date label
    val today     = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
    val yesterday = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).let {
        val cal = Calendar.getInstance(); cal.add(Calendar.DAY_OF_YEAR, -1); it.format(cal.time)
    }
    fun dateLabel(ts: Long): String {
        val d = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(ts))
        return when (d) { today -> "Today"; yesterday -> "Yesterday"; else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(ts)) }
    }
    val grouped = remember(filtered) { filtered.groupBy { dateLabel(it.timestamp) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Text("Security Logs", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, lineHeight = 32.sp)
                    Text("${logs.size} event${if (logs.size != 1) "s" else ""} recorded", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
                }
                if (logs.isNotEmpty()) {
                    IconButton(
                        onClick  = { showClearDialog = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value         = search,
                onValueChange = { search = it },
                placeholder   = { Text("Search logs...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp) },
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

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filters) { f ->
                val sel = f == filter
                FilterChip(
                    selected = sel,
                    onClick  = { filter = f },
                    label    = { Text(f, fontSize = 13.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal) },
                    shape    = RoundedCornerShape(8.dp),
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor     = MaterialTheme.colorScheme.primary,
                        containerColor         = MaterialTheme.colorScheme.surface,
                        labelColor             = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border   = FilterChipDefaults.filterChipBorder(enabled = true, selected = sel, selectedBorderColor = Color.Transparent, borderColor = Color.Transparent)
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Shield, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(if (logs.isEmpty()) "No events recorded" else "No matching events", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, lineHeight = 24.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (logs.isEmpty()) "Security activity will appear here." else "Try adjusting your search or filters.",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp, textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                grouped.forEach { (dateLabel, dayLogs) ->
                    item(key = "header_$dateLabel") {
                        Text(dateLabel, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    item(key = "group_$dateLabel") {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                dayLogs.forEachIndexed { idx, log ->
                                    val blocked = isBlocked(log)
                                    val dotColor = when {
                                        blocked    -> MaterialTheme.colorScheme.error
                                        isUnlock(log) -> MaterialTheme.colorScheme.tertiary
                                        else       -> MaterialTheme.colorScheme.primary
                                    }
                                    val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(log.timestamp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor)
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            if (!log.authResult) "Blocked: ${log.failureReason}" else "Unlocked ${log.protectedAppPackage}",
                                            fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onBackground, lineHeight = 20.sp,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 2, overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(timeStr, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
                                    }
                                    if (idx < dayLogs.lastIndex) {
                                        HorizontalDivider(modifier = Modifier.padding(start = 36.dp), color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor   = MaterialTheme.colorScheme.surfaceVariant,
            shape            = RoundedCornerShape(20.dp),
            title = { Text("Clear All Logs", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) },
            text  = { Text("All security logs will be permanently deleted. This cannot be undone.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 20.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAllHistory()
                        showClearDialog = false
                    },
                    shape  = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Clear", color = Color.White, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        )
    }
}
