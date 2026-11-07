package com.example.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ContactItem
import com.example.data.repository.SpeedDialEntry
import com.example.ui.theme.CallRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedDialSettingsScreen(
    speedDialMap: Map<Int, SpeedDialEntry>,
    contacts: List<ContactItem>,
    onSetSpeedDial: (Int, String, String?) -> Unit,
    onRemoveSpeedDial: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var editingDigit by remember { mutableStateOf<Int?>(null) }
    var numberInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf<Int?>(null) }
    var showContactPicker by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Speed Dial") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Dialpad, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Assign contacts to digits 2-9 for quick calling from the keypad.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            items((2..9).toList()) { digit ->
                val entry = speedDialMap[digit]
                SpeedDialDigitCard(
                    digit = digit,
                    entry = entry,
                    onEdit = {
                        editingDigit = digit
                        numberInput = entry?.number ?: ""
                        nameInput = entry?.name ?: ""
                    },
                    onDelete = { showDeleteConfirm = digit },
                    onPickContact = { showContactPicker = digit }
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Edit Dialog
    editingDigit?.let { digit ->
        AlertDialog(
            onDismissRequest = { editingDigit = null },
            title = { Text("Set Speed Dial for $digit") },
            text = {
                Column {
                    OutlinedTextField(
                        value = numberInput,
                        onValueChange = { numberInput = it },
                        label = { Text("Phone Number") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Name (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (numberInput.isNotBlank()) {
                            onSetSpeedDial(digit, numberInput, nameInput.ifBlank { null })
                            editingDigit = null
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingDigit = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation
    showDeleteConfirm?.let { digit ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Remove Speed Dial") },
            text = { Text("Are you sure you want to remove the speed dial assignment for digit $digit?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveSpeedDial(digit)
                        showDeleteConfirm = null
                    }
                ) {
                    Text("Remove", color = CallRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Simple Contact Picker Dialog
    showContactPicker?.let { digit ->
        AlertDialog(
            onDismissRequest = { showContactPicker = null },
            title = { Text("Choose Contact") },
            text = {
                Column(modifier = Modifier.height(400.dp)) {
                    val filtered = remember(contacts) { contacts.filter { it.phoneNumbers.isNotEmpty() } }
                    if (filtered.isEmpty()) {
                        Text("No contacts with phone numbers found.")
                    } else {
                        LazyColumn {
                            items(filtered, key = { it.id }) { contact ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val number = contact.phoneNumbers.firstOrNull() ?: ""
                                            onSetSpeedDial(digit, number, contact.displayName)
                                            showContactPicker = null
                                        }
                                        .padding(vertical = 12.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.size(32.dp),
                                        shape = androidx.compose.foundation.shape.CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                contact.initial.toString(),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(contact.displayName, style = MaterialTheme.typography.bodyMedium)
                                        Text(contact.phoneNumbers.firstOrNull() ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showContactPicker = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SpeedDialDigitCard(
    digit: Int,
    entry: SpeedDialEntry?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPickContact: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("speed_dial_card_$digit"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (entry != null) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Digit Badge
            Surface(
                modifier = Modifier.size(48.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                color = if (entry != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                contentColor = if (entry != null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = digit.toString(),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                if (entry != null) {
                    Text(
                        text = entry.name ?: "Saved Number",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = entry.number,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Unassigned",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            if (entry != null) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CallRed)
                }
            } else {
                IconButton(onClick = onPickContact) {
                    Icon(Icons.Default.Add, contentDescription = "Assign Contact", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
