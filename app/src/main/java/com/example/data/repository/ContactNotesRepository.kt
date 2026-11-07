package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ContactNotesRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("contact_notes_prefs", Context.MODE_PRIVATE)

    private val _notesFlow = MutableStateFlow<Map<String, String>>(emptyMap())
    val notesFlow: StateFlow<Map<String, String>> = _notesFlow.asStateFlow()

    init {
        loadNotes()
    }

    private fun loadNotes() {
        val map = mutableMapOf<String, String>()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("note_") && value is String) {
                val rawKey = key.removePrefix("note_")
                map[rawKey] = value
            }
        }
        _notesFlow.value = map
    }

    fun getNote(contactIdOrNumber: String): String {
        val key = "note_${normalizeKey(contactIdOrNumber)}"
        return prefs.getString(key, "") ?: ""
    }

    fun saveNote(contactIdOrNumber: String, note: String) {
        val key = "note_${normalizeKey(contactIdOrNumber)}"
        if (note.isBlank()) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putString(key, note).apply()
        }
        loadNotes()
    }

    private fun normalizeKey(raw: String): String {
        return raw.replace(Regex("[^a-zA-Z0-9_]"), "_")
    }
}
