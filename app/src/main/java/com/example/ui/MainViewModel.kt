package com.example.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CallLogItem
import com.example.data.model.CallType
import com.example.data.model.ContactItem
import com.example.data.model.FavoriteItem
import com.example.data.model.SimInfo
import com.example.data.repository.BlockedNumbersRepository
import com.example.data.repository.CallLogRepository
import com.example.data.repository.CallStats
import com.example.data.repository.ContactNotesRepository
import com.example.data.repository.ContactsRepository
import com.example.data.repository.FavoritesRepository
import com.example.data.repository.LaunchTab
import com.example.data.repository.RecordingsRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.SpeedDialRepository
import com.example.data.repository.TelecomRepository
import com.example.data.repository.UserSettings
import com.example.ui.components.T9SearchHelper
import com.example.utils.PalestinianPhoneNumberUtils
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class NavigationTab {
    KEYPAD,
    RECENTS,
    CONTACTS,
    FAVORITES,
    SETTINGS,
    SPEED_DIAL,
    DUPLICATES,
    GROUPS
}

data class DialerUiState(
    val currentTab: NavigationTab = NavigationTab.KEYPAD,
    val dialedNumber: String = "",
    val searchQuery: String = "",
    val callLogFilter: CallType? = null,
    val isDefaultDialer: Boolean = false,
    val hasContactsPermission: Boolean = false,
    val hasCallLogPermission: Boolean = false,
    val hasPhonePermission: Boolean = false,
    val hasRecordAudioPermission: Boolean = false,
    val availableSims: List<SimInfo> = emptyList(),
    val selectedSim: SimInfo? = null,
    val isLoadingContacts: Boolean = false,
    val isLoadingRecents: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val contactsRepository = ContactsRepository(application)
    val callLogRepository = CallLogRepository(application)
    val telecomRepository = TelecomRepository(application)
    val favoritesRepository = FavoritesRepository(application, contactsRepository)
    val settingsRepository = SettingsRepository(application)
    val recordingsRepository = RecordingsRepository(application)
    val contactNotesRepository = ContactNotesRepository(application)
    val blockedNumbersRepository = BlockedNumbersRepository.getInstance(application)
    val speedDialRepository = SpeedDialRepository(application)

    private val _uiState = MutableStateFlow(DialerUiState())
    val uiState: StateFlow<DialerUiState> = _uiState.asStateFlow()

    private val _contacts = MutableStateFlow<List<ContactItem>>(emptyList())
    val contacts: StateFlow<List<ContactItem>> = _contacts.asStateFlow()

    private val _callLogs = MutableStateFlow<List<CallLogItem>>(emptyList())
    val callLogs: StateFlow<List<CallLogItem>> = _callLogs.asStateFlow()

    val favorites: StateFlow<List<FavoriteItem>> = favoritesRepository.favoritesFlow
    val blockedNumbers: StateFlow<List<com.example.data.model.BlockedNumberItem>> = blockedNumbersRepository.blockedNumbersFlow
    val userSettings: StateFlow<UserSettings> = settingsRepository.settingsFlow
    val contactNotes: StateFlow<Map<String, String>> = contactNotesRepository.notesFlow

    // Call stats
    val callStats: StateFlow<CallStats> = _callLogs.combine(userSettings) { logs, _ ->
        callLogRepository.calculateCallStats(logs)
    }.flowOn(Dispatchers.Default)
    .stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        CallStats(
            totalCalls = 0,
            incomingSeconds = 0L,
            outgoingSeconds = 0L,
            missedCallsCount = 0,
            rejectedCallsCount = 0,
            totalDurationSeconds = 0L,
            formattedIncomingDuration = "0s",
            formattedOutgoingDuration = "0s",
            totalDurationFormatted = "0s"
        )
    )

    // Duplicate contact suggestions
    val duplicateGroups: StateFlow<List<ContactsRepository.DuplicateGroup>> = _contacts.combine(userSettings) { allContacts, _ ->
        contactsRepository.findDuplicateSuggestionsAdvanced(allContacts)
    }.flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Contact Groups
    private val _contactGroups = MutableStateFlow<List<ContactsRepository.ContactGroup>>(emptyList())
    val contactGroups: StateFlow<List<ContactsRepository.ContactGroup>> = _contactGroups.asStateFlow()

    private val _selectedGroupId = MutableStateFlow<Long?>(null)
    val selectedGroupId: StateFlow<Long?> = _selectedGroupId.asStateFlow()

    // T9 Matches when dialing on keypad
    val t9Matches: StateFlow<List<ContactItem>> = combine(_uiState, _contacts) { state, allContacts ->
        val query = state.dialedNumber.trim()
        if (query.isBlank()) {
            emptyList()
        } else {
            allContacts.filter { contact ->
                T9SearchHelper.matches(query, contact.displayName, contact.phoneNumbers)
            }.take(10)
        }
    }.flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        // Set default initial tab from user settings
        val initialTab = when (userSettings.value.defaultLaunchTab) {
            LaunchTab.KEYPAD -> NavigationTab.KEYPAD
            LaunchTab.RECENTS -> NavigationTab.RECENTS
            LaunchTab.CONTACTS -> NavigationTab.CONTACTS
            LaunchTab.FAVORITES -> NavigationTab.FAVORITES
            LaunchTab.RECORDINGS -> NavigationTab.KEYPAD
        }
        _uiState.update { it.copy(currentTab = initialTab) }
        // Initial loading happens in the init block below the change-tracking fields, which
        // must be initialized first.
    }

    // --- Change tracking ----------------------------------------------------------------------
    // Contacts and the call log are only re-read when the system reports a change (or a
    // permission was just granted, or the day changed so "Today"/"Yesterday" labels are stale),
    // instead of on every return to the app.

    @Volatile private var callLogDirty = false
    @Volatile private var contactsDirty = false
    private var lastLoadDay = currentDay()
    private var contactsJob: Job? = null

    /** Full contact list (ignores the selected group); used for caller names in recents. */
    @Volatile private var allContactsCache: List<ContactItem> = emptyList()

    private val observerHandler = Handler(Looper.getMainLooper())
    private val callLogObserver = object : ContentObserver(observerHandler) {
        override fun onChange(selfChange: Boolean) { callLogDirty = true }
    }
    private val contactsObserver = object : ContentObserver(observerHandler) {
        override fun onChange(selfChange: Boolean) {
            contactsDirty = true
            callLogDirty = true // Caller names in recents come from contacts.
        }
    }

    private fun registerDataObservers() {
        val resolver = getApplication<Application>().contentResolver
        try {
            resolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, callLogObserver)
        } catch (_: Exception) {}
        try {
            resolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, contactsObserver)
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        val resolver = getApplication<Application>().contentResolver
        resolver.unregisterContentObserver(callLogObserver)
        resolver.unregisterContentObserver(contactsObserver)
    }

    private fun currentDay(): Int {
        val c = java.util.Calendar.getInstance()
        return c.get(java.util.Calendar.YEAR) * 1000 + c.get(java.util.Calendar.DAY_OF_YEAR)
    }

    init {
        registerDataObservers()
        refreshAll()
    }

    /** Loads everything. Used at startup and after a settings reset. */
    fun refreshAll() {
        checkPermissions()
        loadAvailableSims()
        loadContacts()
        loadCallLogs()
        loadFavorites()
    }

    /** Called from onResume: reloads only what changed while the app was in the background. */
    fun onAppResumed() {
        val before = _uiState.value
        checkPermissions()
        val after = _uiState.value

        val newDay = currentDay() != lastLoadDay
        val contactsGranted = !before.hasContactsPermission && after.hasContactsPermission
        val callLogGranted = !before.hasCallLogPermission && after.hasCallLogPermission
        val phoneGranted = !before.hasPhonePermission && after.hasPhonePermission

        if (phoneGranted || before.isDefaultDialer != after.isDefaultDialer) loadAvailableSims()
        if (contactsDirty || contactsGranted) {
            loadContacts()
            loadFavorites()
        }
        if (callLogDirty || callLogGranted || contactsGranted || newDay) loadCallLogs()
    }

    fun checkPermissions() {
        val hasContacts = contactsRepository.hasContactsPermission()
        val hasCallLog = callLogRepository.hasCallLogPermission()
        val hasPhone = telecomRepository.isCallPhonePermissionGranted()
        val isDefault = telecomRepository.isDefaultDialer()

        _uiState.update {
            it.copy(
                hasContactsPermission = hasContacts,
                hasCallLogPermission = hasCallLog,
                hasPhonePermission = hasPhone,
                isDefaultDialer = isDefault
            )
        }
    }

    fun selectTab(tab: NavigationTab) {
        if (_uiState.value.currentTab == tab) return
        _uiState.update { it.copy(currentTab = tab) }
        when (tab) {
            NavigationTab.RECENTS -> if (_callLogs.value.isEmpty()) loadCallLogs()
            NavigationTab.CONTACTS -> if (_contacts.value.isEmpty()) loadContacts()
            NavigationTab.FAVORITES -> if (favorites.value.isEmpty()) loadFavorites()
            else -> {}
        }
    }

    fun appendDigit(digit: Char) {
        _uiState.update { it.copy(dialedNumber = it.dialedNumber + digit) }
    }

    fun deleteDigit() {
        _uiState.update { it.copy(dialedNumber = it.dialedNumber.dropLast(1)) }
    }

    fun clearDigits() {
        _uiState.update { it.copy(dialedNumber = "") }
    }

    fun setDialedNumber(number: String) {
        _uiState.update { it.copy(dialedNumber = number) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setCallLogFilter(filter: CallType?) {
        _uiState.update { it.copy(callLogFilter = filter) }
        loadCallLogs()
    }

    fun setSelectedSim(sim: SimInfo?) {
        _uiState.update { it.copy(selectedSim = sim) }
    }

    fun loadAvailableSims() {
        viewModelScope.launch(Dispatchers.IO) {
            val sims = telecomRepository.getAvailableSims()
            val preferred = userSettings.value.preferredSimSlot
            val defaultSim = if (preferred in 0 until sims.size) sims[preferred] else sims.firstOrNull()
            _uiState.update { it.copy(availableSims = sims, selectedSim = defaultSim) }
        }
    }

    fun loadContacts() {
        // A newer request replaces one still running, so contacts are never read twice at once.
        contactsJob?.cancel()
        contactsDirty = false
        contactsJob = viewModelScope.launch(Dispatchers.IO) {
            if (_contacts.value.isEmpty()) {
                _uiState.update { it.copy(isLoadingContacts = true) }
            }

            // Load groups
            _contactGroups.value = contactsRepository.getContactGroups()

            val everyone = contactsRepository.getAllContacts()
            allContactsCache = everyone
            val groupId = _selectedGroupId.value
            var list = if (groupId != null) {
                val groupIds = contactsRepository.getContactsInGroup(groupId).toSet()
                everyone.filter { it.id in groupIds }
            } else {
                everyone
            }

            // Respect sort preference
            list = if (userSettings.value.contactSortByFirstName) {
                list.sortedBy { it.displayName.lowercase() }
            } else {
                list.sortedBy {
                    val parts = it.displayName.trim().split(" ")
                    if (parts.size > 1) parts.last().lowercase() else it.displayName.lowercase()
                }
            }
            _contacts.value = list
            _uiState.update { it.copy(isLoadingContacts = false) }
        }
    }

    fun setSelectedGroup(groupId: Long?) {
        _selectedGroupId.value = groupId
        loadContacts()
    }

    fun loadCallLogs() {
        callLogDirty = false
        lastLoadDay = currentDay()
        viewModelScope.launch(Dispatchers.IO) {
            if (!callLogRepository.hasCallLogPermission()) {
                _callLogs.value = emptyList()
                _uiState.update { it.copy(isLoadingRecents = false) }
                return@launch
            }
            if (_callLogs.value.isEmpty()) {
                _uiState.update { it.copy(isLoadingRecents = true) }
            }
            val list = callLogRepository.getCallLogs(_uiState.value.callLogFilter)
            // Reuse the contacts load that is already running instead of reading them again.
            contactsJob?.join()
            val allContacts = allContactsCache.ifEmpty { contactsRepository.getAllContacts() }

            // Build fast lookup index for phone numbers to resolve logs in O(N) rather than O(N*M)
            val contactNumberMap = mutableMapOf<String, String>()
            for (contact in allContacts) {
                for (num in contact.phoneNumbers) {
                    val normPal = PalestinianPhoneNumberUtils.normalizePalestinianPhoneNumber(num)
                    if (normPal != null) {
                        contactNumberMap.putIfAbsent(normPal, contact.displayName)
                    }
                    val normGen = PalestinianPhoneNumberUtils.normalizePhoneNumber(num)
                    if (normGen != null) {
                        contactNumberMap.putIfAbsent(normGen, contact.displayName)
                    }
                    val rawDigits = num.filter { it.isDigit() }
                    if (rawDigits.length >= 7) {
                        contactNumberMap.putIfAbsent(rawDigits.takeLast(7), contact.displayName)
                    }
                }
            }

            val resolvedLogs = list.map { log ->
                val normLogPal = PalestinianPhoneNumberUtils.normalizePalestinianPhoneNumber(log.number)
                var matchedName = if (normLogPal != null) contactNumberMap[normLogPal] else null
                if (matchedName == null) {
                    val normLogGen = PalestinianPhoneNumberUtils.normalizePhoneNumber(log.number)
                    matchedName = if (normLogGen != null) contactNumberMap[normLogGen] else null
                }
                if (matchedName == null) {
                    val logDigits = log.number.filter { it.isDigit() }
                    if (logDigits.length >= 7) {
                        matchedName = contactNumberMap[logDigits.takeLast(7)]
                    }
                }
                if (matchedName != null) log.copy(cachedName = matchedName) else log
            }
            _callLogs.value = resolvedLogs
            _uiState.update { it.copy(isLoadingRecents = false) }
        }
    }

    fun loadFavorites() {
        viewModelScope.launch {
            favoritesRepository.loadFavorites()
        }
    }

    fun loadRecordings() {
        viewModelScope.launch {
            recordingsRepository.loadRecordings()
        }
    }

    fun placeCall(number: String, explicitSim: SimInfo? = null) {
        if (number.isBlank()) {
            val lastCall = _callLogs.value.firstOrNull()?.number
            if (!lastCall.isNullOrBlank()) {
                setDialedNumber(lastCall)
            }
            return
        }

        // Resolve SIM to use based on priority
        var resolvedSimId: Int? = explicitSim?.subscriptionId

        if (resolvedSimId == null) {
            // Priority 2: Per-Contact Preference
            val contact = _contacts.value.firstOrNull { c ->
                c.phoneNumbers.any { PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers(it, number) }
            }
            if (contact != null) {
                resolvedSimId = userSettings.value.perContactSimPreferences[contact.lookupKey]
            }
        }

        if (resolvedSimId == null) {
            // Priority 3: Application Default SIM Preference
            val preferredSlot = userSettings.value.preferredSimSlot
            if (preferredSlot >= 0 && preferredSlot < uiState.value.availableSims.size) {
                resolvedSimId = uiState.value.availableSims[preferredSlot].subscriptionId
            }
        }

        // Check if resolved SIM is still available
        val isSimAvailable = uiState.value.availableSims.any { it.subscriptionId == resolvedSimId }
        val finalSimId = if (isSimAvailable) resolvedSimId else null

        // The in-call screen is opened by PulseInCallService once Telecom reports the real call.
        // When this app is not the default dialer (or the number is an emergency number) the
        // system dialer handles the call and shows its own UI.
        if (!telecomRepository.placeCall(number, finalSimId)) {
            android.widget.Toast.makeText(
                getApplication(),
                "Could not place the call. Check that the Phone permission is granted.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    fun deleteCallLog(callId: Long) {
        viewModelScope.launch {
            callLogRepository.deleteCallLog(callId)
            loadCallLogs()
        }
    }

    fun clearAllCallLogs() {
        viewModelScope.launch {
            callLogRepository.clearAllCallLogs()
            loadCallLogs()
        }
    }

    fun addCustomFavorite(name: String, number: String, photoUri: String? = null, contactId: Long? = null) {
        viewModelScope.launch {
            favoritesRepository.addCustomFavorite(name, number, contactId, photoUri)
        }
    }

    fun removeFavorite(favorite: FavoriteItem) {
        viewModelScope.launch {
            favoritesRepository.removeFavorite(favorite)
        }
    }

    fun saveContactNote(contactKey: String, note: String) {
        contactNotesRepository.saveNote(contactKey, note)
    }

    fun updateSettings(transform: (UserSettings) -> UserSettings) {
        settingsRepository.updateSettings(transform)
    }

    fun exportSettings(): String = settingsRepository.exportSettingsJson()

    fun importSettings(json: String): Boolean = settingsRepository.importSettingsJson(json)

    fun resetSettings() {
        settingsRepository.resetSettingsToDefault()
        refreshAll()
    }

    fun setPerContactSimPreference(contactLookupKey: String, subscriptionId: Int?) {
        settingsRepository.updateSettings { settings ->
            val newPrefs = settings.perContactSimPreferences.toMutableMap()
            if (subscriptionId == null || subscriptionId < 0) {
                newPrefs.remove(contactLookupKey)
            } else {
                newPrefs[contactLookupKey] = subscriptionId
            }
            settings.copy(perContactSimPreferences = newPrefs)
        }
    }
}
