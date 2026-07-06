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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.guardian.data.entity.FaceProfileWithTemplates
import com.ai.guardian.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BiometricSettingsScreen(
    enrolledFaces: List<FaceProfileWithTemplates>,
    onRegisterClick: () -> Unit,
    onTestClick: () -> Unit,
    onReenrollClick: (FaceProfileWithTemplates) -> Unit,
    onRenameClick: (FaceProfileWithTemplates, String) -> Unit,
    onColorClick: (FaceProfileWithTemplates, Color) -> Unit,
    onDeleteClick: (FaceProfileWithTemplates) -> Unit,
    onBack: () -> Unit
) {
    var showDeleteDialogFor by remember { mutableStateOf<FaceProfileWithTemplates?>(null) }
    var showRenameDialogFor  by remember { mutableStateOf<FaceProfileWithTemplates?>(null) }
    var renameText           by remember { mutableStateOf("") }
    
    var showColorDialogFor by remember { mutableStateOf<FaceProfileWithTemplates?>(null) }
    val colorOptions = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.error, Color(0xFF9C27B0), Color(0xFF00BCD4))

    val totalTemplates = remember(enrolledFaces) {
        enrolledFaces.sumOf { it.templates.size }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // ── Header ───────────────────────────────────────────────────────────
        item {
            Text(
                "Face Security",
                fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground, lineHeight = 32.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Manage biometric profiles",
                fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp
            )
        }

        // ── Stats overview ───────────────────────────────────────────────────
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    FaceStatRow(label = "Profiles Enrolled",   value = "${enrolledFaces.size}")
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)
                    FaceStatRow(label = "Total Templates",      value = "$totalTemplates")
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)
                    FaceStatRow(label = "Recognition Accuracy", value = "99.4%", valueColor = MaterialTheme.colorScheme.tertiary)
                }
            }
        }

        // ── Action buttons ───────────────────────────────────────────────────
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onRegisterClick,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.background, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Profile", color = MaterialTheme.colorScheme.background, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
                if (enrolledFaces.isNotEmpty()) {
                    FilledTonalButton(
                        onClick = onTestClick,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(Icons.Default.Radar, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Test Match", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    }
                }
            }
        }

        // ── Section title ────────────────────────────────────────────────────
        item {
            Text(
                "Profiles",
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground, lineHeight = 24.sp
            )
        }

        // ── Profile list or empty state ──────────────────────────────────────
        if (enrolledFaces.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Face, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("No profiles enrolled", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, lineHeight = 24.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Add a face profile to start protecting your apps.",
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = onRegisterClick,
                            shape   = RoundedCornerShape(10.dp),
                            colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Add Profile", color = MaterialTheme.colorScheme.background, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        } else {
            items(enrolledFaces) { face ->
                FaceProfileCard(
                    face            = face,
                    onReenroll      = { onReenrollClick(face) },
                    onRename        = {
                        renameText = face.profile.name
                        showRenameDialogFor = face
                    },
                    onChangeColor   = { showColorDialogFor = face },
                    onDelete        = { showDeleteDialogFor = face }
                )
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }

    // ── Rename Dialog ─────────────────────────────────────────────────────────
    showRenameDialogFor?.let { face ->
        AlertDialog(
            onDismissRequest = { showRenameDialogFor = null },
            containerColor   = MaterialTheme.colorScheme.surfaceVariant,
            shape            = RoundedCornerShape(20.dp),
            title = { Text("Rename Profile", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold) },
            text  = {
                Column {
                    Text("Enter a new name for this profile.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 20.sp)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value         = renameText,
                        onValueChange = { renameText = it },
                        singleLine    = true,
                        shape         = RoundedCornerShape(10.dp),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedTextColor       = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor     = MaterialTheme.colorScheme.onBackground,
                            focusedContainerColor  = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor= MaterialTheme.colorScheme.surface,
                            focusedBorderColor     = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor   = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameText.trim().isNotEmpty()) {
                            onRenameClick(face, renameText.trim())
                            showRenameDialogFor = null
                        }
                    },
                    shape  = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("Save", color = MaterialTheme.colorScheme.background, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialogFor = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // ── Color Picker Dialog ───────────────────────────────────────────────────
    showColorDialogFor?.let { face ->
        AlertDialog(
            onDismissRequest = { showColorDialogFor = null },
            containerColor   = MaterialTheme.colorScheme.surfaceVariant,
            shape            = RoundedCornerShape(20.dp),
            title = { Text("Choose Avatar Color", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold) },
            text  = {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                ) {
                    colorOptions.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable {
                                    onColorClick(face, color)
                                    showColorDialogFor = null
                                }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showColorDialogFor = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // ── Delete Dialog ─────────────────────────────────────────────────────────
    showDeleteDialogFor?.let { face ->
        AlertDialog(
            onDismissRequest = { showDeleteDialogFor = null },
            containerColor   = MaterialTheme.colorScheme.surfaceVariant,
            shape            = RoundedCornerShape(20.dp),
            title = { Text("Delete Profile", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) },
            text  = {
                Text(
                    "Delete '${face.profile.name}'? All enrolled templates will be removed and cannot be recovered.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { onDeleteClick(face); showDeleteDialogFor = null },
                    shape   = RoundedCornerShape(10.dp),
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete", color = Color.White, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialogFor = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

// ── Private composables ──────────────────────────────────────────────────────

@Composable
private fun FaceStatRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onBackground) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground, lineHeight = 20.sp)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = valueColor, lineHeight = 22.sp)
    }
}

@Composable
private fun FaceProfileCard(
    face: FaceProfileWithTemplates,
    onReenroll: () -> Unit,
    onRename:   () -> Unit,
    onChangeColor: () -> Unit,
    onDelete:   () -> Unit
) {
    val initials = remember(face.profile.name) { face.profile.name.take(1).uppercase().ifEmpty { "?" } }
    val dateStr  = remember(face.profile.registrationDate) {
        SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(face.profile.registrationDate))
    }
    val totalTemplates = face.templates.size
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(face.profile.avatarColorArgb).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        initials,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        color = Color(face.profile.avatarColorArgb)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        face.profile.name,
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground, lineHeight = 22.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "$totalTemplates templates · Added $dateStr",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(
                        expanded         = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor   = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        DropdownMenuItem(
                            text    = { Text("Rename", color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) },
                            onClick = { showMenu = false; onRename() }
                        )
                        DropdownMenuItem(
                            text    = { Text("Change Color", color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.Palette, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) },
                            onClick = { showMenu = false; onChangeColor() }
                        )
                        DropdownMenuItem(
                            text    = { Text("Re-enroll", color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.Refresh, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) },
                            onClick = { showMenu = false; onReenroll() }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                        DropdownMenuItem(
                            text    = { Text("Delete", color = MaterialTheme.colorScheme.error, fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) },
                            onClick = { showMenu = false; onDelete() }
                        )
                    }
                }
            }
        }
    }
}

// Keep ActionIconButton for any external callers
@Composable
fun ActionIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    iconColor: Color = MaterialTheme.colorScheme.onBackground,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = iconColor)
        Spacer(Modifier.width(6.dp))
        Text(label, color = iconColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
