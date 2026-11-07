package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.FavoriteItem
import com.example.utils.PalestinianPhoneNumberUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class FavoritesRepository(
    private val context: Context,
    private val contactsRepository: ContactsRepository
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("pulse_favorites_pref", Context.MODE_PRIVATE)

    private val _favoritesFlow = MutableStateFlow<List<FavoriteItem>>(emptyList())
    val favoritesFlow: StateFlow<List<FavoriteItem>> = _favoritesFlow.asStateFlow()

    private val lock = Any()

    suspend fun loadFavorites() = withContext(Dispatchers.IO) {
        val list = mutableListOf<FavoriteItem>()

        // 1. Device starred contacts
        val starredContacts = contactsRepository.getStarredContacts()
        for (c in starredContacts) {
            val phone = c.phoneNumbers.firstOrNull() ?: ""
            if (phone.isNotBlank()) {
                list.add(
                    FavoriteItem(
                        id = "device_${c.id}",
                        contactId = c.id,
                        displayName = c.displayName,
                        phoneNumber = phone,
                        photoUri = c.photoUri,
                        isDeviceStarred = true
                    )
                )
            }
        }

        // 2. Local custom favorites
        val localFavorites = getLocalFavoritesList()
        for (item in localFavorites) {
            // Avoid duplicate if already in device starred by equivalence or contact ID
            val isDuplicate = list.any { existing ->
                (item.contactId != null && existing.contactId == item.contactId) ||
                PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers(existing.phoneNumber, item.phoneNumber)
            }
            if (!isDuplicate) {
                list.add(item)
            }
        }

        synchronized(lock) {
            _favoritesFlow.value = list
        }
    }

    suspend fun addCustomFavorite(
        name: String,
        number: String,
        contactId: Long? = null,
        photoUri: String? = null
    ) = withContext(Dispatchers.IO) {
        if (number.isBlank() && name.isBlank()) return@withContext

        synchronized(lock) {
            val current = getLocalFavoritesList().toMutableList()
            current.removeAll { existing ->
                (contactId != null && existing.contactId == contactId) ||
                PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers(existing.phoneNumber, number)
            }
            val newItem = FavoriteItem(
                id = "custom_${System.currentTimeMillis()}",
                contactId = contactId,
                displayName = name,
                phoneNumber = number,
                photoUri = photoUri,
                isDeviceStarred = false
            )
            current.add(newItem)
            saveLocalFavorites(current)
        }
        loadFavorites()
    }

    suspend fun removeFavorite(favorite: FavoriteItem) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val current = getLocalFavoritesList().filterNot { existing ->
                existing.id == favorite.id ||
                (favorite.contactId != null && existing.contactId == favorite.contactId) ||
                PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers(existing.phoneNumber, favorite.phoneNumber)
            }
            saveLocalFavorites(current)
        }
        loadFavorites()
    }

    fun isFavorite(number: String, contactId: Long? = null): Boolean {
        if (number.isBlank() && contactId == null) return false
        return _favoritesFlow.value.any { item ->
            (contactId != null && item.contactId == contactId) ||
            PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers(item.phoneNumber, number)
        }
    }

    private fun getLocalFavoritesList(): List<FavoriteItem> {
        val list = mutableListOf<FavoriteItem>()
        val localJson = prefs.getString("custom_favorites_json", "[]") ?: "[]"
        try {
            val jsonArray = JSONArray(localJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id", "")
                val name = obj.optString("name", "")
                val number = obj.optString("number", "")
                val photoUri = if (obj.has("photoUri")) obj.getString("photoUri") else null
                val contactId = if (obj.has("contactId")) obj.getLong("contactId") else null

                // Filter duplicates within local custom favorites list itself
                val isDuplicate = list.any { existing ->
                    (contactId != null && existing.contactId == contactId) ||
                    PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers(existing.phoneNumber, number)
                }
                if (!isDuplicate) {
                    list.add(
                        FavoriteItem(
                            id = id,
                            contactId = contactId,
                            displayName = name,
                            phoneNumber = number,
                            photoUri = photoUri,
                            isDeviceStarred = false
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Ignore
        }
        return list
    }

    private fun saveLocalFavorites(items: List<FavoriteItem>) {
        val array = JSONArray()
        for (item in items) {
            val obj = JSONObject().apply {
                put("id", item.id)
                item.contactId?.let { put("contactId", it) }
                put("name", item.displayName)
                put("number", item.phoneNumber)
                item.photoUri?.let { put("photoUri", it) }
            }
            array.put(obj)
        }
        prefs.edit().putString("custom_favorites_json", array.toString()).apply()
    }
}
