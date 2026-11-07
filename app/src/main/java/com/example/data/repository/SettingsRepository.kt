package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class AutoRecordMode {
    OFF,
    ASK_BEFORE_CALL,
    AUTO_RECORD
}

enum class RecordCallType {
    BOTH,
    INCOMING_ONLY,
    OUTGOING_ONLY
}

enum class LaunchTab {
    KEYPAD,
    RECENTS,
    CONTACTS,
    FAVORITES,
    RECORDINGS
}

data class UserSettings(
    // Appearance
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentColorIndex: Int = 0, // 0: Pulse Blue, 1: Emerald, 2: Violet, 3: Crimson, 4: Amber
    val textSizeScale: Float = 1.0f,
    val isCompactLayout: Boolean = false,
    val showContactPhotos: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true,
    val dialTonesEnabled: Boolean = true,
    val animationsEnabled: Boolean = true,

    // Calling
    val preferredSimSlot: Int = -1, // -1: Always ask / default
    val askSimEveryCall: Boolean = false,
    val confirmUnknownNumbers: Boolean = false,
    val confirmInternationalNumbers: Boolean = false,
    val directDialingFromDetails: Boolean = true,
    val confirmDeleteCallHistory: Boolean = true,
    val confirmDeleteRecording: Boolean = true,

    // Contacts & Recents
    val contactSortByFirstName: Boolean = true,
    val showMissedCallsProminently: Boolean = true,
    val defaultLaunchTab: LaunchTab = LaunchTab.KEYPAD,

    // Call Recording
    val callRecordingEnabled: Boolean = true,
    val autoRecordMode: AutoRecordMode = AutoRecordMode.OFF,
    val recordCallType: RecordCallType = RecordCallType.BOTH,
    val recordingAudioFormat: String = "M4A",
    val maxStorageLimitMb: Int = 1000,
    val autoCleanupRetentionDays: Int = 0, // 0: Never
    val excludeFavoritesFromRecording: Boolean = false,

    // Privacy & Security
    val appLockEnabled: Boolean = false,
    val hideNumbersInRecents: Boolean = false,
    val clearSearchHistoryOnExit: Boolean = false,

    // Per-contact SIM preferences: Map of contact lookup key to SIM subscription ID
    val perContactSimPreferences: Map<String, Int> = emptyMap()
)

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("pulse_settings_pref", Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<UserSettings> = _settingsFlow.asStateFlow()

    private fun loadSettings(): UserSettings {
        val themeOrdinal = prefs.getInt("theme_mode", ThemeMode.SYSTEM.ordinal)
        val theme = ThemeMode.entries.getOrElse(themeOrdinal) { ThemeMode.SYSTEM }
        val accent = prefs.getInt("accent_color_index", 0)
        val textScale = prefs.getFloat("text_size_scale", 1.0f)
        val compact = prefs.getBoolean("compact_layout", false)
        val photos = prefs.getBoolean("show_contact_photos", true)
        val haptics = prefs.getBoolean("haptic_feedback", true)
        val tones = prefs.getBoolean("dial_tones", true)
        val animations = prefs.getBoolean("animations_enabled", true)

        val simSlot = prefs.getInt("preferred_sim_slot", -1)
        val askSim = prefs.getBoolean("ask_sim_every_call", false)
        val confirmUnknown = prefs.getBoolean("confirm_unknown_numbers", false)
        val confirmIntl = prefs.getBoolean("confirm_intl_numbers", false)
        val directDial = prefs.getBoolean("direct_dial_details", true)
        val confirmDelHistory = prefs.getBoolean("confirm_delete_history", true)
        val confirmDelRec = prefs.getBoolean("confirm_delete_rec", true)

        val sortFirst = prefs.getBoolean("sort_first_name", true)
        val missedProminent = prefs.getBoolean("missed_prominent", true)
        val launchTabOrdinal = prefs.getInt("launch_tab", LaunchTab.KEYPAD.ordinal)
        val launchTab = LaunchTab.entries.getOrElse(launchTabOrdinal) { LaunchTab.KEYPAD }

        val recEnabled = prefs.getBoolean("call_recording_enabled", true)
        val autoRecOrdinal = prefs.getInt("auto_record_mode", AutoRecordMode.OFF.ordinal)
        val autoRec = AutoRecordMode.entries.getOrElse(autoRecOrdinal) { AutoRecordMode.OFF }
        val recTypeOrdinal = prefs.getInt("record_call_type", RecordCallType.BOTH.ordinal)
        val recType = RecordCallType.entries.getOrElse(recTypeOrdinal) { RecordCallType.BOTH }
        val recFormat = prefs.getString("recording_format", "M4A") ?: "M4A"
        val maxStorage = prefs.getInt("max_storage_mb", 1000)
        val retention = prefs.getInt("auto_cleanup_days", 0)
        val excludeFavs = prefs.getBoolean("exclude_favorites_recording", false)

        val appLock = prefs.getBoolean("app_lock_enabled", false)
        val hideNumbers = prefs.getBoolean("hide_numbers_recents", false)
        val clearSearch = prefs.getBoolean("clear_search_exit", false)
        val perContactSimPrefsJson = prefs.getString("per_contact_sim_prefs", "{}") ?: "{}"
        val perContactSimPrefs = try {
            val obj = JSONObject(perContactSimPrefsJson)
            val map = mutableMapOf<String, Int>()
            obj.keys().forEach { key ->
                map[key] = obj.getInt(key)
            }
            map
        } catch (_: Exception) {
            emptyMap<String, Int>()
        }

        return UserSettings(
            themeMode = theme,
            accentColorIndex = accent,
            textSizeScale = textScale,
            isCompactLayout = compact,
            showContactPhotos = photos,
            hapticFeedbackEnabled = haptics,
            dialTonesEnabled = tones,
            animationsEnabled = animations,

            preferredSimSlot = simSlot,
            askSimEveryCall = askSim,
            confirmUnknownNumbers = confirmUnknown,
            confirmInternationalNumbers = confirmIntl,
            directDialingFromDetails = directDial,
            confirmDeleteCallHistory = confirmDelHistory,
            confirmDeleteRecording = confirmDelRec,

            contactSortByFirstName = sortFirst,
            showMissedCallsProminently = missedProminent,
            defaultLaunchTab = launchTab,

            callRecordingEnabled = recEnabled,
            autoRecordMode = autoRec,
            recordCallType = recType,
            recordingAudioFormat = recFormat,
            maxStorageLimitMb = maxStorage,
            autoCleanupRetentionDays = retention,
            excludeFavoritesFromRecording = excludeFavs,

            appLockEnabled = appLock,
            hideNumbersInRecents = hideNumbers,
            clearSearchHistoryOnExit = clearSearch,
            perContactSimPreferences = perContactSimPrefs
        )
    }

    fun updateSettings(transform: (UserSettings) -> UserSettings) {
        val updated = transform(_settingsFlow.value)
        saveSettings(updated)
    }

    fun setThemeMode(mode: ThemeMode) {
        updateSettings { it.copy(themeMode = mode) }
    }

    fun setHapticFeedback(enabled: Boolean) {
        updateSettings { it.copy(hapticFeedbackEnabled = enabled) }
    }

    fun setDialTones(enabled: Boolean) {
        updateSettings { it.copy(dialTonesEnabled = enabled) }
    }

    fun setPreferredSimSlot(slot: Int) {
        updateSettings { it.copy(preferredSimSlot = slot) }
    }

    fun setAccentColorIndex(index: Int) {
        updateSettings { it.copy(accentColorIndex = index) }
    }

    fun setTextSizeScale(scale: Float) {
        updateSettings { it.copy(textSizeScale = scale) }
    }

    fun setCompactLayout(compact: Boolean) {
        updateSettings { it.copy(isCompactLayout = compact) }
    }

    fun setShowContactPhotos(show: Boolean) {
        updateSettings { it.copy(showContactPhotos = show) }
    }

    fun setCallRecordingEnabled(enabled: Boolean) {
        updateSettings { it.copy(callRecordingEnabled = enabled) }
    }

    fun setAutoRecordMode(mode: AutoRecordMode) {
        updateSettings { it.copy(autoRecordMode = mode) }
    }

    fun setRecordCallType(type: RecordCallType) {
        updateSettings { it.copy(recordCallType = type) }
    }

    fun setRecordingAudioFormat(format: String) {
        updateSettings { it.copy(recordingAudioFormat = format) }
    }

    fun setAutoCleanupRetentionDays(days: Int) {
        updateSettings { it.copy(autoCleanupRetentionDays = days) }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        updateSettings { it.copy(appLockEnabled = enabled) }
    }

    fun setHideNumbersInRecents(hide: Boolean) {
        updateSettings { it.copy(hideNumbersInRecents = hide) }
    }

    fun setDefaultLaunchTab(tab: LaunchTab) {
        updateSettings { it.copy(defaultLaunchTab = tab) }
    }

    fun exportSettingsJson(): String {
        val s = _settingsFlow.value
        val obj = JSONObject().apply {
            put("themeMode", s.themeMode.name)
            put("accentColorIndex", s.accentColorIndex)
            put("textSizeScale", s.textSizeScale.toDouble())
            put("isCompactLayout", s.isCompactLayout)
            put("showContactPhotos", s.showContactPhotos)
            put("hapticFeedbackEnabled", s.hapticFeedbackEnabled)
            put("dialTonesEnabled", s.dialTonesEnabled)
            put("animationsEnabled", s.animationsEnabled)
            put("preferredSimSlot", s.preferredSimSlot)
            put("askSimEveryCall", s.askSimEveryCall)
            put("confirmUnknownNumbers", s.confirmUnknownNumbers)
            put("confirmInternationalNumbers", s.confirmInternationalNumbers)
            put("directDialingFromDetails", s.directDialingFromDetails)
            put("contactSortByFirstName", s.contactSortByFirstName)
            put("showMissedCallsProminently", s.showMissedCallsProminently)
            put("defaultLaunchTab", s.defaultLaunchTab.name)
            put("callRecordingEnabled", s.callRecordingEnabled)
            put("autoRecordMode", s.autoRecordMode.name)
            put("recordCallType", s.recordCallType.name)
            put("recordingAudioFormat", s.recordingAudioFormat)
            put("maxStorageLimitMb", s.maxStorageLimitMb)
            put("autoCleanupRetentionDays", s.autoCleanupRetentionDays)
            put("excludeFavoritesFromRecording", s.excludeFavoritesFromRecording)
            put("appLockEnabled", s.appLockEnabled)
            put("hideNumbersInRecents", s.hideNumbersInRecents)
            put("perContactSimPreferences", JSONObject(s.perContactSimPreferences))
        }
        return obj.toString(2)
    }

    fun importSettingsJson(json: String): Boolean {
        try {
            val obj = JSONObject(json)
            val current = _settingsFlow.value
            val imported = current.copy(
                themeMode = obj.optString("themeMode", current.themeMode.name).let { name ->
                    ThemeMode.entries.firstOrNull { it.name == name } ?: current.themeMode
                },
                accentColorIndex = obj.optInt("accentColorIndex", current.accentColorIndex),
                textSizeScale = obj.optDouble("textSizeScale", current.textSizeScale.toDouble()).toFloat(),
                isCompactLayout = obj.optBoolean("isCompactLayout", current.isCompactLayout),
                showContactPhotos = obj.optBoolean("showContactPhotos", current.showContactPhotos),
                hapticFeedbackEnabled = obj.optBoolean("hapticFeedbackEnabled", current.hapticFeedbackEnabled),
                dialTonesEnabled = obj.optBoolean("dialTonesEnabled", current.dialTonesEnabled),
                animationsEnabled = obj.optBoolean("animationsEnabled", current.animationsEnabled),
                preferredSimSlot = obj.optInt("preferredSimSlot", current.preferredSimSlot),
                askSimEveryCall = obj.optBoolean("askSimEveryCall", current.askSimEveryCall),
                confirmUnknownNumbers = obj.optBoolean("confirmUnknownNumbers", current.confirmUnknownNumbers),
                confirmInternationalNumbers = obj.optBoolean("confirmInternationalNumbers", current.confirmInternationalNumbers),
                directDialingFromDetails = obj.optBoolean("directDialingFromDetails", current.directDialingFromDetails),
                contactSortByFirstName = obj.optBoolean("contactSortByFirstName", current.contactSortByFirstName),
                showMissedCallsProminently = obj.optBoolean("showMissedCallsProminently", current.showMissedCallsProminently),
                defaultLaunchTab = obj.optString("defaultLaunchTab", current.defaultLaunchTab.name).let { name ->
                    LaunchTab.entries.firstOrNull { it.name == name } ?: current.defaultLaunchTab
                },
                callRecordingEnabled = obj.optBoolean("callRecordingEnabled", current.callRecordingEnabled),
                autoRecordMode = obj.optString("autoRecordMode", current.autoRecordMode.name).let { name ->
                    AutoRecordMode.entries.firstOrNull { it.name == name } ?: current.autoRecordMode
                },
                recordCallType = obj.optString("recordCallType", current.recordCallType.name).let { name ->
                    RecordCallType.entries.firstOrNull { it.name == name } ?: current.recordCallType
                },
                recordingAudioFormat = obj.optString("recordingAudioFormat", current.recordingAudioFormat),
                maxStorageLimitMb = obj.optInt("maxStorageLimitMb", current.maxStorageLimitMb),
                autoCleanupRetentionDays = obj.optInt("autoCleanupRetentionDays", current.autoCleanupRetentionDays),
                excludeFavoritesFromRecording = obj.optBoolean("excludeFavoritesFromRecording", current.excludeFavoritesFromRecording),
                appLockEnabled = obj.optBoolean("appLockEnabled", current.appLockEnabled),
                hideNumbersInRecents = obj.optBoolean("hideNumbersInRecents", current.hideNumbersInRecents),
                perContactSimPreferences = obj.optJSONObject("perContactSimPreferences")?.let { simPrefsObj ->
                    val map = mutableMapOf<String, Int>()
                    simPrefsObj.keys().forEach { key ->
                        map[key] = simPrefsObj.getInt(key)
                    }
                    map
                } ?: current.perContactSimPreferences
            )
            saveSettings(imported)
            return true
        } catch (_: Exception) {
            return false
        }
    }

    fun resetSettingsToDefault() {
        prefs.edit().clear().apply()
        _settingsFlow.value = UserSettings()
    }

    private fun saveSettings(s: UserSettings) {
        prefs.edit().apply {
            putInt("theme_mode", s.themeMode.ordinal)
            putInt("accent_color_index", s.accentColorIndex)
            putFloat("text_size_scale", s.textSizeScale)
            putBoolean("compact_layout", s.isCompactLayout)
            putBoolean("show_contact_photos", s.showContactPhotos)
            putBoolean("haptic_feedback", s.hapticFeedbackEnabled)
            putBoolean("dial_tones", s.dialTonesEnabled)
            putBoolean("animations_enabled", s.animationsEnabled)

            putInt("preferred_sim_slot", s.preferredSimSlot)
            putBoolean("ask_sim_every_call", s.askSimEveryCall)
            putBoolean("confirm_unknown_numbers", s.confirmUnknownNumbers)
            putBoolean("confirm_intl_numbers", s.confirmInternationalNumbers)
            putBoolean("direct_dial_details", s.directDialingFromDetails)
            putBoolean("confirm_delete_history", s.confirmDeleteCallHistory)
            putBoolean("confirm_delete_rec", s.confirmDeleteRecording)

            putBoolean("sort_first_name", s.contactSortByFirstName)
            putBoolean("missed_prominent", s.showMissedCallsProminently)
            putInt("launch_tab", s.defaultLaunchTab.ordinal)

            putBoolean("call_recording_enabled", s.callRecordingEnabled)
            putInt("auto_record_mode", s.autoRecordMode.ordinal)
            putInt("record_call_type", s.recordCallType.ordinal)
            putString("recording_format", s.recordingAudioFormat)
            putInt("max_storage_mb", s.maxStorageLimitMb)
            putInt("auto_cleanup_days", s.autoCleanupRetentionDays)
            putBoolean("exclude_favorites_recording", s.excludeFavoritesFromRecording)

            putBoolean("app_lock_enabled", s.appLockEnabled)
            putBoolean("hide_numbers_recents", s.hideNumbersInRecents)
            putBoolean("clear_search_exit", s.clearSearchHistoryOnExit)
            putString("per_contact_sim_prefs", JSONObject(s.perContactSimPreferences).toString())
            apply()
        }
        _settingsFlow.value = s
    }
}
