package com.example.ui.recents

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CallLogItem
import com.example.data.model.CallType
import com.example.data.model.GroupedCallLogItem
import com.example.ui.components.PermissionCard
import com.example.utils.CallLogGroupingUtils
import com.example.utils.PalestinianPhoneNumberUtils
import com.example.ui.theme.CallGreen
import com.example.ui.theme.CallOrange
import com.example.ui.theme.CallRed
import kotlinx.coroutines.launch

private val WHITESPACE = Regex("\\s+")

@Composable
fun RecentsScreen(
    callLogs: List<CallLogItem>,
    currentFilter: CallType?,
    hasPermission: Boolean,
    isLoading: Boolean,
    hideNumbers: Boolean = false,
    favorites: List<com.example.data.model.FavoriteItem> = emptyList(),
    blockedNumbers: List<com.example.data.model.BlockedNumberItem> = emptyList(),
    favoritesRepository: com.example.data.repository.FavoritesRepository? = null,
    blockedNumbersRepository: com.example.data.repository.BlockedNumbersRepository? = null,
    onFilterChange: (CallType?) -> Unit,
    onRequestPermission: () -> Unit,
    onPlaceCall: (String) -> Unit,
    onDeleteLog: (Long) -> Unit,
    onClearAllLogs: () -> Unit,
    onAddContact: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var selectedLogItem by remember { mutableStateOf<CallLogItem?>(null) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showBlockConfirmForNumber by remember { mutableStateOf<String?>(null) }

    // Expanded state map for grouped call log items keyed by primary log id
    var expandedGroupIds by remember { mutableStateOf(setOf<Long>()) }

    // If permission is denied, show proper permission-required state without demo/fake logs
    if (!hasPermission) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            PermissionCard(
                title = "Call Log Permission Required",
                description = "Pulse Dialer requires access to your call history to display recent calls, track incoming/outgoing activity, and show caller details. Please grant permission to continue.",
                buttonText = "Grant Permission",
                onRequestPermission = onRequestPermission
            )
        }
        return
    }

    // Normalized caller names, computed once per call-log list (lazily, on the search thread).
    val nameIndex = remember(callLogs) {
        lazy {
            val cache = HashMap<String, String>()
            callLogs.associate { log ->
                val name = log.cachedName ?: ""
                log.id to cache.getOrPut(name) { PalestinianPhoneNumberUtils.normalizeArabicText(name) }
            }
        }
    }

    // Search filtering and grouping run off the main thread so typing and scrolling stay smooth.
    val groupedLogs by produceState(
        initialValue = remember { CallLogGroupingUtils.groupConsecutiveCalls(callLogs) },
        callLogs,
        searchQuery
    ) {
        value = withContext(Dispatchers.Default) {
            val filtered = if (searchQuery.isBlank()) {
                callLogs
            } else {
                val normalizedQuery = PalestinianPhoneNumberUtils.normalizeArabicText(searchQuery)
                val queryTokens = normalizedQuery.split(WHITESPACE).filter { it.isNotBlank() }
                val index = nameIndex.value

                callLogs.filter { log ->
                    val normalizedName = index[log.id]
                        ?: PalestinianPhoneNumberUtils.normalizeArabicText(log.cachedName ?: "")

                    val nameMatch = if (queryTokens.isNotEmpty()) {
                        queryTokens.all { token -> normalizedName.contains(token) }
                    } else {
                        normalizedName.contains(normalizedQuery)
                    }

                    nameMatch || PalestinianPhoneNumberUtils.matchesQuery(log.number, searchQuery)
                }
            }
            CallLogGroupingUtils.groupConsecutiveCalls(filtered)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Search Bar and Clear Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag("recents_search_input"),
                placeholder = {
                    val count = if (searchQuery.isNotBlank()) "${groupedLogs.sumOf { it.subLogs.size }} calls" else "Search recent calls…"
                    Text(count)
                },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search Calls")
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.testTag("clear_recents_search")
                        ) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            if (callLogs.isNotEmpty()) {
                IconButton(
                    onClick = { showClearConfirmDialog = true },
                    modifier = Modifier.padding(start = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear Call History",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Advanced Filter Chips Row (All, Incoming, Outgoing, Missed, Rejected)
        // Horizontally scrollable so chips keep their natural size instead of being squeezed.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = currentFilter == null,
                onClick = { onFilterChange(null) },
                label = { Text("All") }
            )
            FilterChip(
                selected = currentFilter == CallType.INCOMING,
                onClick = { onFilterChange(if (currentFilter == CallType.INCOMING) null else CallType.INCOMING) },
                label = { Text("Incoming") }
            )
            FilterChip(
                selected = currentFilter == CallType.OUTGOING,
                onClick = { onFilterChange(if (currentFilter == CallType.OUTGOING) null else CallType.OUTGOING) },
                label = { Text("Outgoing") }
            )
            FilterChip(
                selected = currentFilter == CallType.MISSED,
                onClick = { onFilterChange(if (currentFilter == CallType.MISSED) null else CallType.MISSED) },
                label = { Text("Missed") }
            )
            FilterChip(
                selected = currentFilter == CallType.REJECTED,
                onClick = { onFilterChange(if (currentFilter == CallType.REJECTED) null else CallType.REJECTED) },
                label = { Text("Rejected") }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Content Area
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (groupedLogs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No call history",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Calls you make or receive will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(groupedLogs, key = { it.primaryLog.id }) { group ->
                    val isExpanded = expandedGroupIds.contains(group.primaryLog.id)
                    RecentsGroupedCallItem(
                        group = group,
                        isExpanded = isExpanded,
                        hideNumber = hideNumbers,
                        favoritesRepository = favoritesRepository,
                        blockedNumbersRepository = blockedNumbersRepository,
                        onClick = { selectedLogItem = group.primaryLog },
                        onToggleExpand = {
                            expandedGroupIds = if (isExpanded) {
                                expandedGroupIds - group.primaryLog.id
                            } else {
                                expandedGroupIds + group.primaryLog.id
                            }
                        },
                        onCallClick = { onPlaceCall(group.primaryLog.number) },
                        onSubItemClick = { subItem -> selectedLogItem = subItem },
                        onSubItemCall = { subNum -> onPlaceCall(subNum) },
                        onAddContact = { num -> onAddContact(num) },
                        onBlockNumber = { num -> showBlockConfirmForNumber = num }
                    )
                }
            }
        }
    }

    // Detailed Call Information Dialog with Quick Actions (Call, SMS, Favorites, Block/Unblock, Save Contact, Call Details)
    selectedLogItem?.let { log ->
        val isFavorite = favorites.any { PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers(it.phoneNumber, log.number) }
        val isBlocked = blockedNumbers.any { PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers(it.number, log.number) }

        AlertDialog(
            onDismissRequest = { selectedLogItem = null },
            title = {
                Text(
                    text = log.cachedName ?: log.number,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (log.cachedName != null && log.cachedName != log.number) {
                        Text(text = log.number, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    Text(
                        text = "Type: ${log.type.name.lowercase().replaceFirstChar { it.uppercase() }}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Date & Time: ${log.formattedDate}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Duration: ${log.formattedDuration}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!log.simDisplayName.isNullOrBlank()) {
                        Text(
                            text = "SIM: ${log.simDisplayName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        // Quick Action 1: Send SMS
                        IconButton(
                            onClick = {
                                val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${log.number}"))
                                smsIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(smsIntent)
                                selectedLogItem = null
                            },
                            modifier = Modifier.testTag("dialog_quick_sms")
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.Message, contentDescription = "SMS", tint = MaterialTheme.colorScheme.primary)
                        }

                        // Quick Action 2: Favorite (Add / Remove)
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    if (isFavorite) {
                                        val fav = favoritesRepository?.favoritesFlow?.value?.firstOrNull {
                                            PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers(it.phoneNumber, log.number)
                                        }
                                        if (fav != null) {
                                            favoritesRepository.removeFavorite(fav)
                                        }
                                    } else {
                                        favoritesRepository?.addCustomFavorite(
                                            name = log.cachedName ?: log.number,
                                            number = log.number
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.testTag("dialog_quick_favorite")
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = if (isFavorite) "Remove Favorite" else "Add Favorite",
                                tint = CallOrange
                            )
                        }

                        // Quick Action 3: Save Contact (if unsaved) or View Contact (if saved)
                        if (log.cachedName == null || log.cachedName == log.number) {
                            IconButton(
                                onClick = {
                                    onAddContact(log.number)
                                    selectedLogItem = null
                                },
                                modifier = Modifier.testTag("dialog_quick_add_contact")
                            ) {
                                Icon(imageVector = Icons.Default.PersonAdd, contentDescription = "Save Contact", tint = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    val uri = Uri.parse("content://contacts/lookup")
                                    val intent = Intent(Intent.ACTION_VIEW, uri)
                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    try {
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        onAddContact(log.number)
                                    }
                                    selectedLogItem = null
                                },
                                modifier = Modifier.testTag("dialog_quick_view_contact")
                            ) {
                                Icon(imageVector = Icons.Default.Info, contentDescription = "View Contact", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // Quick Action 4: Block / Unblock
                        IconButton(
                            onClick = {
                                if (isBlocked) {
                                    blockedNumbersRepository?.unblockNumber(log.number)
                                    selectedLogItem = null
                                } else {
                                    showBlockConfirmForNumber = log.number
                                    selectedLogItem = null
                                }
                            },
                            modifier = Modifier.testTag("dialog_quick_block")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = if (isBlocked) "Unblock" else "Block",
                                tint = if (isBlocked) CallGreen else CallRed
                            )
                        }

                        // Delete log
                        IconButton(
                            onClick = {
                                onDeleteLog(log.id)
                                selectedLogItem = null
                            },
                            modifier = Modifier.testTag("dialog_quick_delete")
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Log", tint = CallRed)
                        }
                    }

                    // Quick Action 5: Call button
                    IconButton(
                        onClick = {
                            onPlaceCall(log.number)
                            selectedLogItem = null
                        },
                        modifier = Modifier.testTag("dialog_quick_call")
                    ) {
                        Icon(imageVector = Icons.Default.Call, contentDescription = "Call", tint = CallGreen)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedLogItem = null }) {
                    Text("Close")
                }
            }
        )
    }


    // Block Confirmation Dialog
    showBlockConfirmForNumber?.let { numberToBlock ->
        AlertDialog(
            onDismissRequest = { showBlockConfirmForNumber = null },
            title = { Text("Block Number") },
            text = { Text("Are you sure you want to block $numberToBlock? You will no longer receive calls or messages from this number.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        blockedNumbersRepository?.blockNumber(numberToBlock)
                        showBlockConfirmForNumber = null
                    }
                ) {
                    Text("Block", color = CallRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirmForNumber = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Clear All Confirmation Dialog
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear Call History") },
            text = { Text("Are you sure you want to clear your entire call log? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAllLogs()
                        showClearConfirmDialog = false
                    }
                ) {
                    Text("Clear All", color = CallRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun RecentsGroupedCallItem(
    group: GroupedCallLogItem,
    isExpanded: Boolean,
    hideNumber: Boolean,
    favoritesRepository: com.example.data.repository.FavoritesRepository?,
    blockedNumbersRepository: com.example.data.repository.BlockedNumbersRepository?,
    onClick: () -> Unit,
    onToggleExpand: () -> Unit,
    onCallClick: () -> Unit,
    onSubItemClick: (CallLogItem) -> Unit,
    onSubItemCall: (String) -> Unit,
    onAddContact: (String) -> Unit,
    onBlockNumber: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val item = group.primaryLog
    val subCount = group.subLogs.size

    val displayedTitle = if (hideNumber && item.cachedName == null) {
        val last4 = item.number.takeLast(4)
        "•••• $last4"
    } else {
        item.cachedName ?: item.number
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .testTag("recents_item_${item.id}"),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Call Type Icon
                val (icon, tint) = when (item.type) {
                    CallType.MISSED -> Icons.AutoMirrored.Filled.CallMissed to CallRed
                    CallType.INCOMING -> Icons.AutoMirrored.Filled.CallReceived to MaterialTheme.colorScheme.primary
                    CallType.OUTGOING -> Icons.AutoMirrored.Filled.CallMade to CallGreen
                    CallType.REJECTED -> Icons.Default.PhoneDisabled to CallRed
                    else -> Icons.Default.Call to MaterialTheme.colorScheme.onSurfaceVariant
                }

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(tint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = item.type.name,
                        tint = tint,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Caller name/number, badge, and timestamp
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = displayedTitle,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = if (item.type == CallType.MISSED) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (item.type == CallType.MISSED) CallRed else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (subCount > 1) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "($subCount)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.formattedDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (item.formattedDuration.isNotBlank()) {
                            Text(
                                text = " • ${item.formattedDuration}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Expand / Collapse button if grouped
                if (subCount > 1) {
                    IconButton(onClick = onToggleExpand) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand group",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Quick Call Button
                IconButton(
                    onClick = onCallClick,
                    modifier = Modifier.testTag("recents_call_back_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Call back",
                        tint = CallGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Expanded Sub-items
        if (isExpanded && subCount > 1) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 32.dp, top = 4.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (subLog in group.subLogs) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSubItemClick(subLog) },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val (subIcon, subTint) = when (subLog.type) {
                                CallType.MISSED -> Icons.AutoMirrored.Filled.CallMissed to CallRed
                                CallType.INCOMING -> Icons.AutoMirrored.Filled.CallReceived to MaterialTheme.colorScheme.primary
                                CallType.OUTGOING -> Icons.AutoMirrored.Filled.CallMade to CallGreen
                                CallType.REJECTED -> Icons.Default.PhoneDisabled to CallRed
                                else -> Icons.Default.Call to MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Icon(imageVector = subIcon, contentDescription = null, tint = subTint, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = subLog.formattedDate,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (subLog.formattedDuration.isNotBlank()) {
                                Text(
                                    text = subLog.formattedDuration,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
