package com.example.ui.settings

import android.app.role.RoleManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ContactItem
import com.example.data.model.SimInfo
import com.example.data.repository.AutoRecordMode
import com.example.data.repository.CallStats
import com.example.data.repository.LaunchTab
import com.example.data.repository.RecordCallType
import com.example.data.repository.ThemeMode
import com.example.data.repository.UserSettings
import com.example.ui.theme.AccentColors
import com.example.ui.theme.AccentNames
import com.example.ui.theme.CallGreen
import com.example.ui.theme.CallOrange
import com.example.ui.theme.CallRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userSettings: UserSettings,
    isDefaultDialer: Boolean,
    hasContactsPermission: Boolean,
    hasCallLogPermission: Boolean,
    hasPhonePermission: Boolean,
    hasRecordAudioPermission: Boolean,
    availableSims: List<SimInfo>,
    callStats: CallStats,
    duplicateSuggestions: List<Pair<String, List<ContactItem>>>,
    onRequestDefaultDialer: () -> Unit,
    onUpdateSettings: ((UserSettings) -> UserSettings) -> Unit,
    onOpenRecordings: () -> Unit,
    onOpenSpeedDial: () -> Unit,
    onOpenDuplicates: () -> Unit,
    onExportSettings: () -> String,
    onImportSettings: (String) -> Boolean,
    onResetSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    val categories = listOf("Appearance", "Calling", "Contacts", "Privacy", "Backup")

    var showDiagnosticsDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importJsonText by remember { mutableStateOf("") }
    var showDuplicatesDialog by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        // Category Navigation Tabs
        ScrollableTabRow(
            selectedTabIndex = selectedCategoryIndex,
            edgePadding = 16.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            categories.forEachIndexed { index, title ->
                Tab(
                    selected = selectedCategoryIndex == index,
                    onClick = { selectedCategoryIndex = index },
                    text = { Text(title, fontWeight = if (selectedCategoryIndex == index) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }

        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (selectedCategoryIndex) {
                0 -> AppearanceSection(
                    userSettings = userSettings,
                    onUpdateSettings = onUpdateSettings
                )
                1 -> CallingSection(
                    userSettings = userSettings,
                    isDefaultDialer = isDefaultDialer,
                    availableSims = availableSims,
                    onRequestDefaultDialer = onRequestDefaultDialer,
                    onOpenSpeedDial = onOpenSpeedDial,
                    onUpdateSettings = onUpdateSettings
                )
                2 -> ContactsAndHistorySection(
                    userSettings = userSettings,
                    duplicateSuggestions = duplicateSuggestions,
                    onShowDuplicates = onOpenDuplicates,
                    onUpdateSettings = onUpdateSettings
                )
                3 -> PrivacySection(
                    userSettings = userSettings,
                    hasPhone = hasPhonePermission,
                    hasContacts = hasContactsPermission,
                    hasCallLog = hasCallLogPermission,
                    hasMic = hasRecordAudioPermission,
                    onUpdateSettings = onUpdateSettings
                )
                5 -> BackupAndDiagnosticsSection(
                    onShowDiagnostics = { showDiagnosticsDialog = true },
                    onExportSettings = {
                        val json = onExportSettings()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Pulse Dialer Settings", json))
                        Toast.makeText(context, "Settings copied to clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    onShowImport = { showImportDialog = true },
                    onShowReset = { showResetConfirmDialog = true },
                    onShowLicenses = { showLicensesDialog = true }
                )
            }
        }
    }

    // Diagnostics Dialog
    if (showDiagnosticsDialog) {
        val nonPiiDiagnostics = buildString {
            appendLine("=== Pulse Dialer Diagnostics ===")
            appendLine("App Version: 1.0 (Pulse Dialer for HyperOS)")
            appendLine("Android SDK: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
            appendLine("Device Model: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Default Dialer: $isDefaultDialer")
            appendLine("Phone Permission: $hasPhonePermission")
            appendLine("Contacts Permission: $hasContactsPermission")
            appendLine("Call Log Permission: $hasCallLogPermission")
            appendLine("Detected SIM Count: ${availableSims.size}")
            appendLine("Theme Mode: ${userSettings.themeMode.name}")
            appendLine("Timestamp: ${System.currentTimeMillis()}")
        }

        AlertDialog(
            onDismissRequest = { showDiagnosticsDialog = false },
            title = { Text("System Diagnostics") },
            text = {
                Column {
                    Text(
                        text = "Technical diagnostics without personal or contact data:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = nonPiiDiagnostics,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Diagnostics", nonPiiDiagnostics))
                        Toast.makeText(context, "Diagnostics copied to clipboard", Toast.LENGTH_SHORT).show()
                        showDiagnosticsDialog = false
                    }
                ) {
                    Text("Copy")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiagnosticsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Reset Confirmation Dialog
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text("Reset Settings") },
            text = { Text("Are you sure you want to restore all settings to default values? Your contacts and saved recordings will remain safe.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onResetSettings()
                        showResetConfirmDialog = false
                        Toast.makeText(context, "Settings restored to defaults", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Reset", color = CallRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Import Settings Dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Settings JSON") },
            text = {
                Column {
                    Text("Paste settings JSON below to import configuration:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importJsonText,
                        onValueChange = { importJsonText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        placeholder = { Text("{\"themeMode\": \"SYSTEM\", ...}") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val success = onImportSettings(importJsonText)
                        if (success) {
                            Toast.makeText(context, "Settings imported successfully", Toast.LENGTH_SHORT).show()
                            showImportDialog = false
                        } else {
                            Toast.makeText(context, "Invalid JSON structure", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Duplicate Contacts Scan Dialog
    if (showDuplicatesDialog) {
        AlertDialog(
            onDismissRequest = { showDuplicatesDialog = false },
            title = { Text("Duplicate Contacts Scan") },
            text = {
                Column {
                    if (duplicateSuggestions.isEmpty()) {
                        Text("No duplicate contacts detected. Your phonebook is clean!")
                    } else {
                        Text(
                            text = "Found ${duplicateSuggestions.size} duplicate suggestion groups:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        duplicateSuggestions.take(5).forEach { (reason, group) ->
                            Text(
                                text = "• $reason (${group.size} contacts)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDuplicatesDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    // About & Licenses Dialog
    if (showLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            title = { Text("About Pulse Dialer") },
            text = {
                Column {
                    Text("Pulse Dialer v1.0", fontWeight = FontWeight.Bold)
                    Text("High performance native Android Telecom dialer designed for Android and Xiaomi HyperOS.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Open Source Components:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    Text("• Android Jetpack Compose & Material 3 (Apache 2.0)\n• Coil Image Loader (Apache 2.0)\n• Kotlin Coroutines & Flow (Apache 2.0)", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicensesDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

// ==================== SECTION COMPOSABLES ====================

@Composable
private fun AppearanceSection(
    userSettings: UserSettings,
    onUpdateSettings: ((UserSettings) -> UserSettings) -> Unit
) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionTitle(icon = Icons.Default.DarkMode, title = "Theme & Colors")

            ThemeOptionRow(
                label = "System Default",
                selected = userSettings.themeMode == ThemeMode.SYSTEM,
                onSelect = { onUpdateSettings { it.copy(themeMode = ThemeMode.SYSTEM) } }
            )
            ThemeOptionRow(
                label = "Light Mode",
                selected = userSettings.themeMode == ThemeMode.LIGHT,
                onSelect = { onUpdateSettings { it.copy(themeMode = ThemeMode.LIGHT) } }
            )
            ThemeOptionRow(
                label = "Dark Mode",
                selected = userSettings.themeMode == ThemeMode.DARK,
                onSelect = { onUpdateSettings { it.copy(themeMode = ThemeMode.DARK) } }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Text("Accent Color Palette", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AccentColors.forEachIndexed { index, (light, dark) ->
                    val isSelected = userSettings.accentColorIndex == index
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (userSettings.themeMode == ThemeMode.DARK) dark else light)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { onUpdateSettings { it.copy(accentColorIndex = index) } },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Text("✓", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Text(
                text = "Selected: ${AccentNames.getOrElse(userSettings.accentColorIndex) { "Pulse Blue" }}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Layout & Text
            ToggleRow(
                title = "Compact Contact Layout",
                subtitle = "Reduces vertical padding for dense contact lists",
                checked = userSettings.isCompactLayout,
                onCheckedChange = { compact -> onUpdateSettings { it.copy(isCompactLayout = compact) } }
            )

            ToggleRow(
                title = "Show Contact Photos",
                subtitle = "Display caller avatars and initials in lists",
                checked = userSettings.showContactPhotos,
                onCheckedChange = { show -> onUpdateSettings { it.copy(showContactPhotos = show) } }
            )

            ToggleRow(
                title = "Haptic Keypad Feedback",
                subtitle = "Vibrate softly on number button presses",
                checked = userSettings.hapticFeedbackEnabled,
                onCheckedChange = { haptics -> onUpdateSettings { it.copy(hapticFeedbackEnabled = haptics) } }
            )
        }
    }
}

@Composable
private fun CallingSection(
    userSettings: UserSettings,
    isDefaultDialer: Boolean,
    availableSims: List<SimInfo>,
    onRequestDefaultDialer: () -> Unit,
    onOpenSpeedDial: () -> Unit,
    onUpdateSettings: ((UserSettings) -> UserSettings) -> Unit
) {
    val context = LocalContext.current

    // Default Dial Role Card
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDefaultDialer) CallGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isDefaultDialer) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isDefaultDialer) CallGreen else CallOrange,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isDefaultDialer) "Default Phone App" else "Not Set as Default Dialer",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isDefaultDialer) "Pulse Dialer handles all incoming and outgoing calls." else "Set Pulse Dialer as default to enable incoming call screens and full call controls on HyperOS.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!isDefaultDialer) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(onClick = onRequestDefaultDialer, modifier = Modifier.fillMaxWidth()) {
                    Text("Set as Default Dialer")
                }
            }
        }
    }

    // SIM & Call Confirmation
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionTitle(icon = Icons.Default.Phone, title = "Calling Preferences")

            if (availableSims.size > 1) {
                Text("Default Outgoing SIM", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                ThemeOptionRow(
                    label = "Always Ask Before Calling",
                    selected = userSettings.preferredSimSlot == -1,
                    onSelect = { onUpdateSettings { it.copy(preferredSimSlot = -1) } }
                )
                availableSims.forEachIndexed { index, sim ->
                    ThemeOptionRow(
                        label = "SIM ${index + 1} (${sim.carrierName.ifEmpty { sim.displayName }})",
                        selected = userSettings.preferredSimSlot == index,
                        onSelect = { onUpdateSettings { it.copy(preferredSimSlot = index) } }
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            }

            ToggleRow(
                title = "Confirm Unknown Numbers",
                subtitle = "Ask for confirmation before dialing numbers not in contacts",
                checked = userSettings.confirmUnknownNumbers,
                onCheckedChange = { c -> onUpdateSettings { it.copy(confirmUnknownNumbers = c) } }
            )

            ToggleRow(
                title = "Confirm International Calls",
                subtitle = "Prevent accidental roaming or international dialing",
                checked = userSettings.confirmInternationalNumbers,
                onCheckedChange = { c -> onUpdateSettings { it.copy(confirmInternationalNumbers = c) } }
            )

            ToggleRow(
                title = "Direct Dial from Contact Card",
                subtitle = "Tap a phone number to place call immediately",
                checked = userSettings.directDialingFromDetails,
                onCheckedChange = { c -> onUpdateSettings { it.copy(directDialingFromDetails = c) } }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            ActionRow(
                title = "Speed Dial Settings",
                subtitle = "Manage assignments for digits 2-9",
                icon = Icons.Default.Dialpad,
                onClick = onOpenSpeedDial
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            if (userSettings.perContactSimPreferences.isNotEmpty()) {
                ActionRow(
                    title = "Per-Contact SIM Preferences",
                    subtitle = "${userSettings.perContactSimPreferences.size} contacts have saved SIM preferences",
                    icon = Icons.Default.SimCard,
                    onClick = { /* Could open a detailed list if needed, but for now just show summary */ }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            }

            // System Shortcuts
            ActionRow(
                title = "System Call Settings",
                subtitle = "Open carrier call forwarding & waiting in Android settings",
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                onClick = {
                    val intent = Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try { context.startActivity(intent) } catch (_: Exception) {}
                }
            )

            ActionRow(
                title = "Voicemail Shortcut",
                subtitle = "Open voicemail dialer",
                icon = Icons.Default.Voicemail,
                onClick = {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("voicemail:")).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try { context.startActivity(intent) } catch (_: Exception) {}
                }
            )
        }
    }
}

@Composable
private fun ContactsAndHistorySection(
    userSettings: UserSettings,
    duplicateSuggestions: List<Pair<String, List<ContactItem>>>,
    onShowDuplicates: () -> Unit,
    onUpdateSettings: ((UserSettings) -> UserSettings) -> Unit
) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionTitle(icon = Icons.Default.People, title = "Contacts & History Options")

            ToggleRow(
                title = "Sort by First Name",
                subtitle = if (userSettings.contactSortByFirstName) "Alphabetical by First Name" else "Alphabetical by Last Name",
                checked = userSettings.contactSortByFirstName,
                onCheckedChange = { s -> onUpdateSettings { it.copy(contactSortByFirstName = s) } }
            )

            ToggleRow(
                title = "Highlight Missed Calls",
                subtitle = "Show bold red badge on missed call logs",
                checked = userSettings.showMissedCallsProminently,
                onCheckedChange = { s -> onUpdateSettings { it.copy(showMissedCallsProminently = s) } }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            ActionRow(
                title = "Scan for Duplicate Contacts",
                subtitle = if (duplicateSuggestions.isEmpty()) "No duplicates found" else "${duplicateSuggestions.size} duplicate suggestions detected",
                icon = Icons.AutoMirrored.Filled.ArrowForwardIos,
                onClick = onShowDuplicates
            )
        }
    }
}



@Composable
private fun PrivacySection(
    userSettings: UserSettings,
    hasPhone: Boolean,
    hasContacts: Boolean,
    hasCallLog: Boolean,
    hasMic: Boolean,
    onUpdateSettings: ((UserSettings) -> UserSettings) -> Unit
) {
    val context = LocalContext.current

    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionTitle(icon = Icons.Default.Security, title = "Privacy & Local Data")

            Surface(
                color = CallGreen.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = CallGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "100% On-Device Storage. Zero cloud tracking.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CallGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            ToggleRow(
                title = "App Lock Verification",
                subtitle = "Require biometric or device screen lock to open app",
                checked = userSettings.appLockEnabled,
                onCheckedChange = { l -> onUpdateSettings { it.copy(appLockEnabled = l) } }
            )

            ToggleRow(
                title = "Hide Phone Numbers in Recents",
                subtitle = "Mask numbers for sensitive environments",
                checked = userSettings.hideNumbersInRecents,
                onCheckedChange = { h -> onUpdateSettings { it.copy(hideNumbersInRecents = h) } }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Text("System Permissions Status", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            PermBadgeRow("Phone & Telecom", hasPhone)
            PermBadgeRow("Contacts Provider", hasContacts)
            PermBadgeRow("Call Log History", hasCallLog)

            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Manage in System Settings")
            }
        }
    }
}

@Composable
private fun BackupAndDiagnosticsSection(
    onShowDiagnostics: () -> Unit,
    onExportSettings: () -> Unit,
    onShowImport: () -> Unit,
    onShowReset: () -> Unit,
    onShowLicenses: () -> Unit
) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionTitle(icon = Icons.Default.Upload, title = "Backup & Maintenance")

            ActionRow(
                title = "Export Settings to Clipboard",
                subtitle = "Copy JSON representation of your configuration",
                icon = Icons.Default.ContentCopy,
                onClick = onExportSettings
            )

            ActionRow(
                title = "Import Settings JSON",
                subtitle = "Restore preferences from JSON snippet",
                icon = Icons.Default.Download,
                onClick = onShowImport
            )

            ActionRow(
                title = "System Diagnostics Report",
                subtitle = "View non-PII technical diagnostics and hardware info",
                icon = Icons.Default.Analytics,
                onClick = onShowDiagnostics
            )

            ActionRow(
                title = "About & Open Source",
                subtitle = "Version 1.0, build details and licenses",
                icon = Icons.Default.Info,
                onClick = onShowLicenses
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Button(
                onClick = onShowReset,
                colors = ButtonDefaults.buttonColors(containerColor = CallRed),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset All Settings to Defaults")
            }
        }
    }
}

// ==================== HELPER SUBCOMPONENTS ====================

@Composable
private fun SectionTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ThemeOptionRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun StatPill(title: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = color)
        Text(text = title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PermBadgeRow(name: String, granted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = name, style = MaterialTheme.typography.bodySmall)
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (granted) CallGreen.copy(alpha = 0.15f) else CallRed.copy(alpha = 0.15f)
        ) {
            Text(
                text = if (granted) "Granted" else "Missing",
                color = if (granted) CallGreen else CallRed,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}
