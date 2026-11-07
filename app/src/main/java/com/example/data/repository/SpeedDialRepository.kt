package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SpeedDialEntry(
    val keyDigit: Int, // 1 to 9
    val number: String,
    val name: String? = null
)

class SpeedDialRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("speed_dial_prefs", Context.MODE_PRIVATE)

    private val _speedDialMap = MutableStateFlow<Map<Int, SpeedDialEntry>>(emptyMap())
    val speedDialMapFlow: StateFlow<Map<Int, SpeedDialEntry>> = _speedDialMap.asStateFlow()

    init {
        loadSpeedDialMap()
    }

    private fun loadSpeedDialMap() {
        val map = mutableMapOf<Int, SpeedDialEntry>()
        for (i in 1..9) {
            val raw = prefs.getString("speed_dial_$i", null)
            if (!raw.isNullOrEmpty()) {
                val parts = raw.split("|", limit = 2)
                val number = parts.getOrNull(0) ?: ""
                val name = parts.getOrNull(1)?.ifEmpty { null }
                if (number.isNotEmpty()) {
                    map[i] = SpeedDialEntry(keyDigit = i, number = number, name = name)
                }
            }
        }
        _speedDialMap.value = map
    }

    fun setSpeedDial(keyDigit: Int, number: String, name: String? = null) {
        if (keyDigit !in 1..9) return
        if (number.isBlank()) {
            prefs.edit().remove("speed_dial_$keyDigit").apply()
        } else {
            prefs.edit().putString("speed_dial_$keyDigit", "$number|${name ?: ""}").apply()
        }
        loadSpeedDialMap()
    }

    fun removeSpeedDial(keyDigit: Int) {
        if (keyDigit !in 1..9) return
        prefs.edit().remove("speed_dial_$keyDigit").apply()
        loadSpeedDialMap()
    }
}
