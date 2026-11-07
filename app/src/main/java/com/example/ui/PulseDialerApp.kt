package com.example.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.contacts.ContactsScreen
import com.example.ui.contacts.DuplicateManagementScreen
import com.example.ui.dialer.KeypadScreen
import com.example.ui.favorites.FavoritesScreen
import com.example.ui.recents.RecentsScreen
import com.example.ui.recordings.RecordingsScreen
import com.example.ui.settings.SettingsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulseDialerApp(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val callLogs by viewModel.callLogs.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val blockedNumbers by viewModel.blockedNumbers.collectAsStateWithLifecycle()
    val t9Matches by viewModel.t9Matches.collectAsStateWithLifecycle()
    val userSettings by viewModel.userSettings.collectAsStateWithLifecycle()
    val callStats by viewModel.callStats.collectAsStateWithLifecycle()
    val duplicateGroups by viewModel.duplicateGroups.collectAsStateWithLifecycle()
    val contactNotes by viewModel.contactNotes.collectAsStateWithLifecycle()
    val speedDialMap by viewModel.speedDialRepository.speedDialMapFlow.collectAsStateWithLifecycle(emptyMap())

    var pendingCallNumber by remember { mutableStateOf<String?>(null) }
    var confirmCallPromptText by remember { mutableStateOf<String?>(null) }

    // Permissions Request Launcher
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.refreshAll()
    }

    // Role Manager Launcher
    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        viewModel.checkPermissions()
    }

    // Handle back button on secondary tabs
    BackHandler(enabled = uiState.currentTab != NavigationTab.KEYPAD) {
        viewModel.selectTab(NavigationTab.KEYPAD)
    }

    fun requestAllPermissions() {
        val list = mutableListOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionsLauncher.launch(list.toTypedArray())
    }

    fun initiateCallWithChecks(number: String) {
        if (!uiState.hasPhonePermission) {
            requestAllPermissions()
            return
        }

        // Check unknown numbers confirmation
        if (userSettings.confirmUnknownNumbers) {
            val isKnown = contacts.any { c -> c.phoneNumbers.any { it.filter { ch -> ch.isDigit() } == number.filter { ch -> ch.isDigit() } } }
            if (!isKnown) {
                pendingCallNumber = number
                confirmCallPromptText = "Call unknown number '$number'?"
                return
            }
        }

        // Check international numbers confirmation
        if (userSettings.confirmInternationalNumbers && number.startsWith("+")) {
            pendingCallNumber = number
            confirmCallPromptText = "Call international destination '$number'?"
            return
        }

        viewModel.placeCall(number)
    }

    androidx.compose.foundation.layout.BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = this.maxWidth > 600.dp
        
        Scaffold(
            topBar = {
                if (uiState.currentTab != NavigationTab.SPEED_DIAL) {
                    TopAppBar(
                        title = {
                            Text(
                                text = when (uiState.currentTab) {
                                    NavigationTab.KEYPAD -> "Pulse Dialer"
                                    NavigationTab.RECENTS -> "Recent Calls"
                                    NavigationTab.CONTACTS -> "Contacts"
                                    NavigationTab.FAVORITES -> "Favorites"
                                    NavigationTab.SETTINGS -> "Settings"
                                    NavigationTab.SPEED_DIAL -> "Speed Dial"
                                    NavigationTab.DUPLICATES -> "Duplicate Contacts"
                                    NavigationTab.GROUPS -> "Contact Groups"
                                },
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        actions = {
                            if (uiState.currentTab != NavigationTab.CONTACTS && uiState.currentTab != NavigationTab.SETTINGS) {
                                IconButton(
                                    onClick = { viewModel.selectTab(NavigationTab.CONTACTS) },
                                    modifier = Modifier.testTag("top_search_contacts_button")
                                ) {
                                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search Contacts")
                                }
                            }
                            IconButton(
                                onClick = {
                                    if (uiState.currentTab == NavigationTab.SETTINGS) {
                                        viewModel.selectTab(NavigationTab.KEYPAD)
                                    } else {
                                        viewModel.selectTab(NavigationTab.SETTINGS)
                                    }
                                },
                                modifier = Modifier.testTag("top_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = if (uiState.currentTab == NavigationTab.SETTINGS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            titleContentColor = MaterialTheme.colorScheme.onBackground
                        )
                    )
                }
            },
            bottomBar = {
                if (uiState.currentTab != NavigationTab.SPEED_DIAL && !isWide) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.testTag("bottom_nav_bar")
                    ) {
                        NavigationBarItem(
                            selected = uiState.currentTab == NavigationTab.KEYPAD,
                            onClick = { viewModel.selectTab(NavigationTab.KEYPAD) },
                            icon = { Icon(Icons.Default.Dialpad, contentDescription = "Keypad") },
                            label = { NavLabel("Keypad") },
                            modifier = Modifier.testTag("nav_keypad")
                        )
                        NavigationBarItem(
                            selected = uiState.currentTab == NavigationTab.RECENTS,
                            onClick = { viewModel.selectTab(NavigationTab.RECENTS) },
                            icon = {
                                val missedCount = callStats.missedCallsCount
                                if (missedCount > 0 && userSettings.showMissedCallsProminently) {
                                    BadgedBox(badge = { Badge { Text(missedCount.toString()) } }) {
                                        Icon(Icons.Default.History, contentDescription = "Recents")
                                    }
                                } else {
                                    Icon(Icons.Default.History, contentDescription = "Recents")
                                }
                            },
                            label = { NavLabel("Recents") },
                            modifier = Modifier.testTag("nav_recents")
                        )
                        NavigationBarItem(
                            selected = uiState.currentTab == NavigationTab.CONTACTS,
                            onClick = { viewModel.selectTab(NavigationTab.CONTACTS) },
                            icon = { Icon(Icons.Default.Contacts, contentDescription = "Contacts") },
                            label = { NavLabel("Contacts") },
                            modifier = Modifier.testTag("nav_contacts")
                        )
                        NavigationBarItem(
                            selected = uiState.currentTab == NavigationTab.FAVORITES,
                            onClick = { viewModel.selectTab(NavigationTab.FAVORITES) },
                            icon = { Icon(Icons.Default.Star, contentDescription = "Favorites") },
                            label = { NavLabel("Favorites") },
                            modifier = Modifier.testTag("nav_favorites")
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWide && uiState.currentTab != NavigationTab.SPEED_DIAL) {
                    androidx.compose.material3.NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        header = {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    ) {
                        androidx.compose.material3.NavigationRailItem(
                            selected = uiState.currentTab == NavigationTab.KEYPAD,
                            onClick = { viewModel.selectTab(NavigationTab.KEYPAD) },
                            icon = { Icon(Icons.Default.Dialpad, contentDescription = "Keypad") },
                            label = { Text("Keypad") }
                        )
                        androidx.compose.material3.NavigationRailItem(
                            selected = uiState.currentTab == NavigationTab.RECENTS,
                            onClick = { viewModel.selectTab(NavigationTab.RECENTS) },
                            icon = { Icon(Icons.Default.History, contentDescription = "Recents") },
                            label = { Text("Recents") }
                        )
                        androidx.compose.material3.NavigationRailItem(
                            selected = uiState.currentTab == NavigationTab.CONTACTS,
                            onClick = { viewModel.selectTab(NavigationTab.CONTACTS) },
                            icon = { Icon(Icons.Default.Contacts, contentDescription = "Contacts") },
                            label = { Text("Contacts") }
                        )
                        androidx.compose.material3.NavigationRailItem(
                            selected = uiState.currentTab == NavigationTab.FAVORITES,
                            onClick = { viewModel.selectTab(NavigationTab.FAVORITES) },
                            icon = { Icon(Icons.Default.Star, contentDescription = "Favorites") },
                            label = { Text("Favorites") }
                        )
                    }
                }

                val tabModifier = Modifier.weight(1f).fillMaxSize()

                AnimatedContent(
                    targetState = uiState.currentTab,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(40)) togetherWith fadeOut(animationSpec = tween(25))
                    },
                    label = "TabTransition",
                    modifier = tabModifier
                ) { targetTab ->
            when (targetTab) {
                NavigationTab.KEYPAD -> {
                    KeypadScreen(
                        dialedNumber = uiState.dialedNumber,
                        t9Matches = t9Matches,
                        availableSims = uiState.availableSims,
                        selectedSim = uiState.selectedSim,
                        hapticsEnabled = userSettings.hapticFeedbackEnabled,
                        speedDialMap = speedDialMap,
                        onAppendDigit = { viewModel.appendDigit(it) },
                        onDeleteDigit = { viewModel.deleteDigit() },
                        onClearDigits = { viewModel.clearDigits() },
                        onSelectSim = { viewModel.setSelectedSim(it) },
                        onPlaceCall = { initiateCallWithChecks(it) },
                        onAddContact = { number ->
                            val intent = viewModel.contactsRepository.createAddContactIntent(phoneNumber = number)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(intent)
                        },
                        onConfigureSpeedDial = { viewModel.selectTab(NavigationTab.SPEED_DIAL) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                NavigationTab.RECENTS -> {
                    RecentsScreen(
                        callLogs = callLogs,
                        currentFilter = uiState.callLogFilter,
                        hasPermission = uiState.hasCallLogPermission,
                        isLoading = uiState.isLoadingRecents,
                        hideNumbers = userSettings.hideNumbersInRecents,
                        favorites = favorites,
                        blockedNumbers = blockedNumbers,
                        favoritesRepository = viewModel.favoritesRepository,
                        blockedNumbersRepository = viewModel.blockedNumbersRepository,
                        onFilterChange = { viewModel.setCallLogFilter(it) },
                        onRequestPermission = { requestAllPermissions() },
                        onPlaceCall = { initiateCallWithChecks(it) },
                        onDeleteLog = { viewModel.deleteCallLog(it) },
                        onClearAllLogs = { viewModel.clearAllCallLogs() },
                        onAddContact = { number ->
                            val intent = viewModel.contactsRepository.createAddContactIntent(phoneNumber = number)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                NavigationTab.CONTACTS -> {
                    ContactsScreen(
                        contacts = contacts,
                        hasPermission = uiState.hasContactsPermission,
                        isLoading = uiState.isLoadingContacts,
                        isCompactLayout = userSettings.isCompactLayout,
                        showContactPhotos = userSettings.showContactPhotos,
                        contactNotes = contactNotes,
                        groups = viewModel.contactGroups.collectAsStateWithLifecycle().value,
                        selectedGroupId = viewModel.selectedGroupId.collectAsStateWithLifecycle().value,
                        availableSims = uiState.availableSims,
                        perContactSimPrefs = userSettings.perContactSimPreferences,
                        onRequestPermission = { requestAllPermissions() },
                        onPlaceCall = { initiateCallWithChecks(it) },
                        directDialFromDetails = userSettings.directDialingFromDetails,
                        onOpenInKeypad = { number ->
                            viewModel.setDialedNumber(number)
                            viewModel.selectTab(NavigationTab.KEYPAD)
                        },
                        onAddNewContact = {
                            val intent = viewModel.contactsRepository.createAddContactIntent()
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(intent)
                        },
                        onEditContact = { contact ->
                            val intent = viewModel.contactsRepository.createEditContactIntent(contact.id, contact.lookupKey)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(intent)
                        },
                        onAddToFavorites = { contact ->
                            val num = contact.phoneNumbers.firstOrNull() ?: ""
                            if (num.isNotBlank()) {
                                viewModel.addCustomFavorite(
                                    name = contact.displayName,
                                    number = num,
                                    photoUri = contact.photoUri,
                                    contactId = contact.id
                                )
                            }
                        },
                        onSelectGroup = { viewModel.setSelectedGroup(it) },
                        onManageDuplicates = { viewModel.selectTab(NavigationTab.DUPLICATES) },
                        onSetSimPreference = { key, simId -> viewModel.setPerContactSimPreference(key, simId) },
                        onSaveNote = { key, note -> viewModel.saveContactNote(key, note) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                NavigationTab.FAVORITES -> {
                    FavoritesScreen(
                        favorites = favorites,
                        onPlaceCall = { initiateCallWithChecks(it) },
                        onRemoveFavorite = { viewModel.removeFavorite(it) },
                        onAddCustomFavorite = { name, number ->
                            viewModel.addCustomFavorite(name = name, number = number)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                NavigationTab.SETTINGS -> {
                    SettingsScreen(
                        userSettings = userSettings,
                        isDefaultDialer = uiState.isDefaultDialer,
                        hasContactsPermission = uiState.hasContactsPermission,
                        hasCallLogPermission = uiState.hasCallLogPermission,
                        hasPhonePermission = uiState.hasPhonePermission,
                        hasRecordAudioPermission = uiState.hasRecordAudioPermission,
                        availableSims = uiState.availableSims,
                        callStats = callStats,
                        duplicateSuggestions = emptyList(),
                        onRequestDefaultDialer = {
                            val intent = viewModel.telecomRepository.createRequestDefaultDialerIntent()
                            if (intent != null) {
                                roleLauncher.launch(intent)
                            }
                        },
                        onUpdateSettings = { transform -> viewModel.updateSettings(transform) },
                        onOpenRecordings = {},
                        onOpenSpeedDial = { viewModel.selectTab(NavigationTab.SPEED_DIAL) },
                        onOpenDuplicates = { viewModel.selectTab(NavigationTab.DUPLICATES) },
                        onExportSettings = { viewModel.exportSettings() },
                        onImportSettings = { json -> viewModel.importSettings(json) },
                        onResetSettings = { viewModel.resetSettings() },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                NavigationTab.SPEED_DIAL -> {
                    com.example.ui.settings.SpeedDialSettingsScreen(
                        speedDialMap = speedDialMap,
                        contacts = contacts,
                        onSetSpeedDial = { digit, num, name -> viewModel.speedDialRepository.setSpeedDial(digit, num, name) },
                        onRemoveSpeedDial = { digit -> viewModel.speedDialRepository.removeSpeedDial(digit) },
                        onBack = { viewModel.selectTab(NavigationTab.SETTINGS) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                NavigationTab.DUPLICATES -> {
                    DuplicateManagementScreen(
                        duplicateGroups = duplicateGroups,
                        onViewContact = { contact ->
                            val intent = viewModel.contactsRepository.createViewContactIntent(contact.id, contact.lookupKey)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(intent)
                        },
                        onBack = { viewModel.selectTab(NavigationTab.CONTACTS) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                NavigationTab.GROUPS -> {
                    // Handled within ContactsScreen
                    viewModel.selectTab(NavigationTab.CONTACTS)
                }
            }
        }
    }
}

    // Call Confirmation Prompt Dialog (Unknown or International numbers)
    confirmCallPromptText?.let { prompt ->
        AlertDialog(
            onDismissRequest = {
                confirmCallPromptText = null
                pendingCallNumber = null
            },
            title = { Text("Call Confirmation") },
            text = { Text(prompt) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val num = pendingCallNumber
                        confirmCallPromptText = null
                        pendingCallNumber = null
                        if (num != null) viewModel.placeCall(num)
                    }
                ) {
                    Text("Call")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        confirmCallPromptText = null
                        pendingCallNumber = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    }
}

/** Bottom-bar label that always stays on a single line, even with larger system font scales. */
@Composable
private fun NavLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.sp),
        fontSize = 11.sp,
        maxLines = 1,
        softWrap = false,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
    )
}
