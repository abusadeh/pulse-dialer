package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.BlockedNumberItem
import com.example.utils.PalestinianPhoneNumberUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BlockedNumbersRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("blocked_numbers_prefs", Context.MODE_PRIVATE)

    private val _blockedNumbersFlow = MutableStateFlow<List<BlockedNumberItem>>(emptyList())
    val blockedNumbersFlow: StateFlow<List<BlockedNumberItem>> = _blockedNumbersFlow.asStateFlow()

    private val lock = Any()

    init {
        loadBlockedNumbers()
    }

    private fun loadBlockedNumbers() = synchronized(lock) {
        val set = prefs.getStringSet("blocked_numbers_set", emptySet()) ?: emptySet()
        val list = set.map { raw ->
            val parts = raw.split("|", limit = 3)
            val num = parts.getOrNull(0) ?: ""
            val name = parts.getOrNull(1)?.ifEmpty { null }
            val millis = parts.getOrNull(2)?.toLongOrNull() ?: System.currentTimeMillis()
            BlockedNumberItem(number = num, name = name, dateBlockedMillis = millis)
        }
        _blockedNumbersFlow.value = list
    }

    fun blockNumber(number: String, name: String? = null) = synchronized(lock) {
        if (number.isBlank()) return
        val currentSet = prefs.getStringSet("blocked_numbers_set", emptySet())?.toMutableSet() ?: mutableSetOf()

        // Remove existing entries that are equivalent to avoid duplicates
        currentSet.removeAll { raw ->
            val parts = raw.split("|", limit = 3)
            val existingNumber = parts.getOrNull(0) ?: ""
            PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers(existingNumber, number)
        }

        // Preserve original raw number for display
        val serialized = "$number|${name ?: ""}|${System.currentTimeMillis()}"
        currentSet.add(serialized)
        prefs.edit().putStringSet("blocked_numbers_set", currentSet).apply()
        loadBlockedNumbers()
    }

    fun unblockNumber(number: String) = synchronized(lock) {
        if (number.isBlank()) return
        val currentSet = prefs.getStringSet("blocked_numbers_set", emptySet())?.toMutableSet() ?: mutableSetOf()

        // Remove all equivalent entries
        currentSet.removeAll { raw ->
            val parts = raw.split("|", limit = 3)
            val existingNumber = parts.getOrNull(0) ?: ""
            PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers(existingNumber, number)
        }

        prefs.edit().putStringSet("blocked_numbers_set", currentSet).apply()
        loadBlockedNumbers()
    }

    fun isNumberBlocked(number: String): Boolean = synchronized(lock) {
        if (number.isBlank()) return false
        return _blockedNumbersFlow.value.any { item ->
            PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers(item.number, number)
        }
    }

    companion object {
        @Volatile
        private var instance: BlockedNumbersRepository? = null

        /**
         * Shared instance used by the app and the call services, so the block list is read from
         * disk once and a number blocked in the UI is seen by the next incoming call.
         */
        fun getInstance(context: Context): BlockedNumbersRepository =
            instance ?: synchronized(this) {
                instance ?: BlockedNumbersRepository(context.applicationContext).also { instance = it }
            }

        fun normalizeNumber(number: String): String {
            return PalestinianPhoneNumberUtils.cleanPhoneNumber(number)
        }
    }
}
