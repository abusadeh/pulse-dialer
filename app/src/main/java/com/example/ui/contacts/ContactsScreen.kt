package com.example.ui.contacts

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import com.example.ui.components.T9SearchHelper
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ContactItem
import com.example.ui.components.PermissionCard
import com.example.utils.PalestinianPhoneNumberUtils
import com.example.ui.theme.CallGreen
import com.example.ui.theme.CallOrange
import androidx.compose.material3.HorizontalDivider
import com.example.data.model.SimInfo
import com.example.data.repository.ContactsRepository
import com.example.data.repository.UserSettings

@Composable
fun ContactsScreen(
    contacts: List<ContactItem>,
    hasPermission: Boolean,
    isLoading: Boolean,
    isCompactLayout: Boolean = false,
    showContactPhotos: Boolean = true,
    contactNotes: Map<String, String> = emptyMap(),
    groups: List<ContactsRepository.ContactGroup> = emptyList(),
    selectedGroupId: Long? = null,
    availableSims: List<SimInfo> = emptyList(),
    perContactSimPrefs: Map<String, Int> = emptyMap(),
    onRequestPermission: () -> Unit,
    onPlaceCall: (String) -> Unit,
    onAddNewContact: () -> Unit,
    onEditContact: (ContactItem) -> Unit,
    onAddToFavorites: (ContactItem) -> Unit,
    onSelectGroup: (Long?) -> Unit = {},
    onManageDuplicates: () -> Unit = {},
    onSetSimPreference: (String, Int?) -> Unit = { _, _ -> },
    onSaveNote: (String, String) -> Unit = { _, _ -> },
    /** "Direct Dial from Contact Card": tapping a number calls it instead of opening the keypad. */
    directDialFromDetails: Boolean = true,
    onOpenInKeypad: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedContact by remember { mutableStateOf<ContactItem?>(null) }
    var noteInput by remember { mutableStateOf("") }
    var isEditingNote by remember { mutableStateOf(false) }

    // Per-contact lowercase / Arabic-normalized names, computed once per contact list (lazily, on
    // the background search thread) instead of for every contact on every keystroke.
    val searchIndex = remember(contacts) {
        lazy {
            contacts.associate { c ->
                c.id to (c.displayName.lowercase() to PalestinianPhoneNumberUtils.normalizeArabicText(c.displayName))
            }
        }
    }

    // Filtering runs off the main thread so typing never stutters; the previous result stays
    // on screen until the new one is ready.
    val filteredContacts by produceState(initialValue = contacts, contacts, searchQuery, contactNotes) {
        if (searchQuery.isBlank()) {
            value = contacts
            return@produceState
        }
        value = withContext(Dispatchers.Default) {
            val rawQuery = searchQuery.trim()
            val asciiQuery = PalestinianPhoneNumberUtils.convertArabicDigitsToAscii(rawQuery)
            val normArabicQuery = PalestinianPhoneNumberUtils.normalizeArabicText(rawQuery)
            val digitsOnly = asciiQuery.filter { it in '0'..'9' }
            val queryWords = rawQuery.lowercase().split(" ").filter { it.isNotBlank() }
            val normQueryWords = normArabicQuery.split(" ").filter { it.isNotBlank() }
            val index = searchIndex.value

            contacts.filter { contact ->
                val (normName, normContactName) = index[contact.id]
                    ?: (contact.displayName.lowercase() to
                        PalestinianPhoneNumberUtils.normalizeArabicText(contact.displayName))

                // 1. Direct name match (case-insensitive and multiword)
                if (queryWords.all { normName.contains(it) }) {
                    return@filter true
                }

                // 2. Arabic normalized name match (including multiword)
                if (normQueryWords.all { normContactName.contains(it) }) {
                    return@filter true
                }

                // 3. Phone numbers intelligent matching (local, Palestinian prefixes, Arabic digits)
                val phoneMatch = contact.phoneNumbers.any { num ->
                    PalestinianPhoneNumberUtils.matchesQuery(num, asciiQuery) ||
                            PalestinianPhoneNumberUtils.matchesQuery(num, rawQuery)
                }
                if (phoneMatch) return@filter true

                // 4. T9 match if query has digits
                if (digitsOnly.isNotEmpty()) {
                    if (T9SearchHelper.matches(digitsOnly, contact.displayName, contact.phoneNumbers)) {
                        return@filter true
                    }
                }

                // 5. Contact Note match
                val note = contactNotes[contact.lookupKey] ?: contactNotes[contact.id.toString()] ?: ""
                if (note.isNotBlank() && note.contains(rawQuery, ignoreCase = true)) {
                    return@filter true
                }

                false
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {

            // Permission Banner (non-blocking)
            if (!hasPermission) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Contacts permission needed",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Grant permission to sync device contacts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        TextButton(onClick = onRequestPermission) {
                            Text("Grant", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("contacts_search_input"),
                placeholder = {
                    val countText = if (searchQuery.isNotBlank()) {
                        "${filteredContacts.size} of ${contacts.size} contacts"
                    } else {
                        "Search ${contacts.size} contacts…"
                    }
                    Text(countText)
                },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search Contacts")
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.testTag("clear_contacts_search")
                        ) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (filteredContacts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No contacts found" else "No contacts yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "Try searching with a different name or number" else "Add your first contact with the + button below.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(if (isCompactLayout) 2.dp else 6.dp)
                ) {
                    items(filteredContacts, key = { it.id }) { contact ->
                        ContactListItem(
                            contact = contact,
                            isCompact = isCompactLayout,
                            showPhotos = showContactPhotos,
                            onClick = {
                                selectedContact = contact
                                noteInput = contactNotes[contact.lookupKey] ?: contactNotes[contact.id.toString()] ?: ""
                                isEditingNote = false
                            },
                            onCallClick = {
                                val number = contact.phoneNumbers.firstOrNull() ?: ""
                                if (number.isNotBlank()) onPlaceCall(number)
                            }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onAddNewContact,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("add_contact_fab")
        ) {
            Icon(imageVector = Icons.Default.PersonAdd, contentDescription = "Add Contact")
        }
    }

    // Contact Detail Dialog with Notes
    selectedContact?.let { contact ->
        val currentNote = contactNotes[contact.lookupKey] ?: contactNotes[contact.id.toString()] ?: ""

        AlertDialog(
            onDismissRequest = { selectedContact = null },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    if (showContactPhotos) {
                        ContactAvatar(contact = contact, size = 80)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    Text(
                        text = contact.displayName,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center
                    )
                    if (contact.isStarred) {
                        Surface(
                            color = CallOrange.copy(alpha = 0.1f),
                            shape = CircleShape,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = CallOrange, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Favorite", style = MaterialTheme.typography.labelSmall, color = CallOrange)
                            }
                        }
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    
                    Text(
                        text = "Phone Numbers",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    for (num in contact.phoneNumbers) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    if (directDialFromDetails) onPlaceCall(num) else onOpenInKeypad(num)
                                    selectedContact = null
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = num,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = PalestinianPhoneNumberUtils.formatPalestinianNumber(num),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row {
                                    IconButton(
                                        onClick = {
                                            val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$num"))
                                            smsIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            context.startActivity(smsIntent)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Message,
                                            contentDescription = "SMS",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            onPlaceCall(num)
                                            selectedContact = null
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Call,
                                            contentDescription = "Call",
                                            tint = CallGreen
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Spacer(modifier = Modifier.height(16.dp))

                    // Per-Contact SIM Preference
                    if (availableSims.size > 1) {
                        val currentSimId = perContactSimPrefs[contact.lookupKey]
                        var showSimPicker by remember { mutableStateOf(false) }

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth().clickable { showSimPicker = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.SimCard, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("Preferred SIM", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
                                        val simLabel = if (currentSimId != null) {
                                            availableSims.find { it.subscriptionId == currentSimId }?.let { "SIM ${it.slotIndex + 1}: ${it.displayName}" } ?: "System Default"
                                        } else "System Default"
                                        Text(simLabel, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        if (showSimPicker) {
                            AlertDialog(
                                onDismissRequest = { showSimPicker = false },
                                title = { Text("Preferred SIM for ${contact.displayName}") },
                                text = {
                                    Column {
                                        availableSims.forEach { sim ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        onSetSimPreference(contact.lookupKey, sim.subscriptionId)
                                                        showSimPicker = false
                                                    }
                                                    .padding(vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                androidx.compose.material3.RadioButton(
                                                    selected = currentSimId == sim.subscriptionId,
                                                    onClick = {
                                                        onSetSimPreference(contact.lookupKey, sim.subscriptionId)
                                                        showSimPicker = false
                                                    }
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("SIM ${sim.slotIndex + 1} (${sim.displayName})")
                                            }
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    onSetSimPreference(contact.lookupKey, null)
                                                    showSimPicker = false
                                                }
                                                .padding(vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            androidx.compose.material3.RadioButton(
                                                selected = currentSimId == null,
                                                onClick = {
                                                    onSetSimPreference(contact.lookupKey, null)
                                                    showSimPicker = false
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("System Default (Always Ask)")
                                        }
                                    }
                                },
                                confirmButton = {},
                                dismissButton = {
                                    TextButton(onClick = { showSimPicker = false }) { Text("Cancel") }
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Contact Specific Local Note Section
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.NoteAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Private Local Note", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                }
                                TextButton(
                                    onClick = {
                                        if (isEditingNote) {
                                            onSaveNote(contact.lookupKey.ifEmpty { contact.id.toString() }, noteInput)
                                            isEditingNote = false
                                        } else {
                                            isEditingNote = true
                                        }
                                    }
                                ) {
                                    Text(text = if (isEditingNote) "Save" else if (currentNote.isNotBlank()) "Edit" else "Add")
                                }
                            }

                            if (isEditingNote) {
                                OutlinedTextField(
                                    value = noteInput,
                                    onValueChange = { noteInput = it },
                                    placeholder = { Text("Add personal note (stored offline)…") },
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 3,
                                    textStyle = MaterialTheme.typography.bodySmall
                                )
                            } else if (currentNote.isNotBlank()) {
                                Text(
                                    text = currentNote,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            } else {
                                Text(
                                    text = "No private notes for this contact.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row {
                    // Add to favorites button
                    IconButton(
                        onClick = {
                            onAddToFavorites(contact)
                            selectedContact = null
                        }
                    ) {
                        Icon(
                            imageVector = if (contact.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favorite",
                            tint = CallOrange
                        )
                    }

                    // Edit contact button
                    IconButton(
                        onClick = {
                            onEditContact(contact)
                            selectedContact = null
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Contact", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedContact = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun ContactListItem(
    contact: ContactItem,
    isCompact: Boolean,
    showPhotos: Boolean,
    onClick: () -> Unit,
    onCallClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("contact_item_${contact.id}"),
        shape = RoundedCornerShape(if (isCompact) 8.dp else 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = if (isCompact) 4.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showPhotos) {
                ContactAvatar(contact = contact, size = if (isCompact) 36 else 44)
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.displayName,
                    style = if (isCompact) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium) else MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!isCompact) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = contact.phoneNumbers.firstOrNull() ?: "No number",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (contact.isStarred) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Starred",
                    tint = CallOrange,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(end = 4.dp)
                )
            }

            IconButton(onClick = onCallClick) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Call",
                    tint = CallGreen,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun ContactAvatar(
    contact: ContactItem,
    size: Int = 44,
    modifier: Modifier = Modifier
) {
    if (!contact.photoUri.isNullOrBlank()) {
        AsyncImage(
            model = contact.photoUri,
            contentDescription = contact.displayName,
            modifier = modifier
                .size(size.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = contact.initial.toString(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = (size * 0.45).sp
                ),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
