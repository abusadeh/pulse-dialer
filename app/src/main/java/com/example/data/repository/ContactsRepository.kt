package com.example.data.repository

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.example.data.model.ContactItem
import com.example.utils.PalestinianPhoneNumberUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class ContactsRepository(private val context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver

    fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun getAllContacts(): List<ContactItem> = withContext(Dispatchers.IO) {
        if (!hasContactsPermission()) {
            return@withContext emptyList()
        }

        val contactsMap = mutableMapOf<Long, ContactBuilder>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
            ContactsContract.CommonDataKinds.Phone.STARRED
        )

        try {
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val lookupIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                val starredIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val lookup = if (lookupIdx != -1) cursor.getString(lookupIdx) ?: "" else ""
                    val name = if (nameIdx != -1) cursor.getString(nameIdx) ?: "Unknown" else "Unknown"
                    val number = if (numIdx != -1) cursor.getString(numIdx) ?: "" else ""
                    val photo = if (photoIdx != -1) cursor.getString(photoIdx) else null
                    val starred = if (starredIdx != -1) cursor.getInt(starredIdx) == 1 else false

                    val existing = contactsMap.getOrPut(id) {
                        ContactBuilder(id, lookup, name, photo, starred)
                    }
                    if (number.isNotBlank() && !existing.numbers.contains(number)) {
                        existing.numbers.add(number)
                    }
                }
            }
        } catch (_: SecurityException) {
            return@withContext emptyList()
        } catch (_: Exception) {
            return@withContext emptyList()
        }

        val result = contactsMap.values.map { builder ->
            ContactItem(
                id = builder.id,
                lookupKey = builder.lookupKey,
                displayName = builder.displayName,
                phoneNumbers = builder.numbers,
                photoUri = builder.photoUri,
                isStarred = builder.isStarred
            )
        }

        result
    }

    suspend fun getStarredContacts(): List<ContactItem> = withContext(Dispatchers.IO) {
        getAllContacts().filter { it.isStarred }
    }

    suspend fun findContactByNumber(number: String): ContactItem? = withContext(Dispatchers.IO) {
        if (number.isBlank() || !hasContactsPermission()) return@withContext null
        // Fast path: the system's indexed phone lookup (used for caller ID on incoming calls).
        lookupByPhoneIndex(number)?.let { return@withContext it }
        // Slow path: full scan with our own number-equivalence rules (e.g. 059… vs +97059…).
        getAllContacts().firstOrNull { contact ->
            contact.phoneNumbers.any { num ->
                PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers(num, number)
            }
        }
    }

    private fun lookupByPhoneIndex(number: String): ContactItem? {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(number)
        )
        val projection = arrayOf(
            ContactsContract.PhoneLookup.CONTACT_ID,
            ContactsContract.PhoneLookup.LOOKUP_KEY,
            ContactsContract.PhoneLookup.DISPLAY_NAME,
            ContactsContract.PhoneLookup.NUMBER,
            ContactsContract.PhoneLookup.PHOTO_URI,
            ContactsContract.PhoneLookup.STARRED
        )
        return try {
            contentResolver.query(uri, projection, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return null
                val name = c.getString(2)
                if (name.isNullOrBlank()) return null
                ContactItem(
                    id = c.getLong(0),
                    lookupKey = c.getString(1).orEmpty(),
                    displayName = name,
                    phoneNumbers = listOfNotNull(c.getString(3)),
                    photoUri = c.getString(4),
                    isStarred = c.getInt(5) == 1
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun findAllContactsByNumber(number: String): List<ContactItem> = withContext(Dispatchers.IO) {
        if (number.isBlank()) return@withContext emptyList()
        getAllContacts().filter { contact ->
            contact.phoneNumbers.any { num ->
                PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers(num, number)
            }
        }
    }

    fun createAddContactIntent(phoneNumber: String? = null, name: String? = null): Intent {
        val intent = Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI).apply {
            if (!phoneNumber.isNullOrBlank()) {
                putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber)
            }
            if (!name.isNullOrBlank()) {
                putExtra(ContactsContract.Intents.Insert.NAME, name)
            }
        }
        return intent
    }

    fun createEditContactIntent(contactId: Long, lookupKey: String): Intent {
        val uri = ContactsContract.Contacts.getLookupUri(contactId, lookupKey)
        return Intent(Intent.ACTION_EDIT).apply {
            setDataAndType(uri, ContactsContract.Contacts.CONTENT_ITEM_TYPE)
        }
    }

    fun createViewContactIntent(contactId: Long, lookupKey: String): Intent {
        val uri = ContactsContract.Contacts.getLookupUri(contactId, lookupKey)
        return Intent(Intent.ACTION_VIEW, uri)
    }

    suspend fun getContactGroups(): List<ContactGroup> = withContext(Dispatchers.IO) {
        val groups = mutableListOf<ContactGroup>()
        if (!hasContactsPermission()) return@withContext groups

        val projection = arrayOf(
            ContactsContract.Groups._ID,
            ContactsContract.Groups.TITLE,
            ContactsContract.Groups.ACCOUNT_NAME,
            ContactsContract.Groups.ACCOUNT_TYPE
        )

        try {
            contentResolver.query(
                ContactsContract.Groups.CONTENT_URI,
                projection,
                null,
                null,
                ContactsContract.Groups.TITLE + " ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.Groups._ID)
                val titleIdx = cursor.getColumnIndex(ContactsContract.Groups.TITLE)
                val accountNameIdx = cursor.getColumnIndex(ContactsContract.Groups.ACCOUNT_NAME)
                val accountTypeIdx = cursor.getColumnIndex(ContactsContract.Groups.ACCOUNT_TYPE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val title = cursor.getString(titleIdx) ?: continue
                    val accountName = cursor.getString(accountNameIdx) ?: ""
                    val accountType = cursor.getString(accountTypeIdx) ?: ""
                    
                    // Filter out system groups that might be empty or hidden
                    if (!title.startsWith("System Group")) {
                        groups.add(ContactGroup(id, title, accountName, accountType))
                    }
                }
            }
        } catch (_: Exception) {}
        groups
    }

    suspend fun getContactsInGroup(groupId: Long): List<Long> = withContext(Dispatchers.IO) {
        val contactIds = mutableListOf<Long>()
        if (!hasContactsPermission()) return@withContext contactIds

        val projection = arrayOf(ContactsContract.CommonDataKinds.GroupMembership.CONTACT_ID)
        val selection = "${ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID} = ? AND " +
                "${ContactsContract.Data.MIMETYPE} = ?"
        val selectionArgs = arrayOf(groupId.toString(), ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE)

        try {
            contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.GroupMembership.CONTACT_ID)
                while (cursor.moveToNext()) {
                    contactIds.add(cursor.getLong(idIdx))
                }
            }
        } catch (_: Exception) {}
        contactIds
    }

    data class ContactGroup(
        val id: Long,
        val title: String,
        val accountName: String,
        val accountType: String
    )

    sealed class DuplicateGroup {
        abstract val contacts: List<ContactItem>
        abstract val reason: String

        data class ExactMatch(override val contacts: List<ContactItem>, override val reason: String) : DuplicateGroup()
        data class PossibleMatch(override val contacts: List<ContactItem>, override val reason: String) : DuplicateGroup()
    }

    fun findDuplicateSuggestions(contacts: List<ContactItem>): List<DuplicateGroup> {
        return findDuplicateSuggestionsAdvanced(contacts)
    }

    fun findDuplicateSuggestionsAdvanced(contacts: List<ContactItem>): List<DuplicateGroup> {
        val results = mutableListOf<DuplicateGroup>()
        if (contacts.isEmpty()) return results

        // 1. Exact Name & Phone Match (Highest confidence)
        val exactMap = contacts.groupBy { contact ->
            val normName = PalestinianPhoneNumberUtils.normalizeArabicText(contact.displayName).trim()
            val normPhones = contact.phoneNumbers.mapNotNull { PalestinianPhoneNumberUtils.normalizePhoneNumber(it) }.sorted()
            "$normName|$normPhones"
        }.filter { it.value.size > 1 && it.key.isNotBlank() }

        exactMap.forEach { (_, group) ->
            results.add(DuplicateGroup.ExactMatch(group, "Exact name and phone number match"))
        }

        // 2. Shared Normalized Phone Numbers (Excluding those already in ExactMatch)
        val sharedPhoneMap = mutableMapOf<String, MutableList<ContactItem>>()
        contacts.forEach { contact ->
            contact.phoneNumbers.forEach { num ->
                val norm = PalestinianPhoneNumberUtils.normalizePhoneNumber(num)
                if (!norm.isNullOrBlank()) {
                    sharedPhoneMap.getOrPut(norm) { mutableListOf() }.add(contact)
                }
            }
        }

        sharedPhoneMap.filter { it.value.distinctBy { c -> c.id }.size > 1 }
            .forEach { (num, group) ->
                val distinctGroup = group.distinctBy { it.id }
                // Avoid re-suggesting if they are already in an ExactMatch (subset check)
                if (!results.any { it is DuplicateGroup.ExactMatch && it.contacts.containsAll(distinctGroup) }) {
                    results.add(DuplicateGroup.PossibleMatch(distinctGroup, "Shared phone number: ${PalestinianPhoneNumberUtils.formatNumber(num)}"))
                }
            }

        // 3. Arabic Normalized Name Match (Possible)
        val nameMap = mutableMapOf<String, MutableList<ContactItem>>()
        contacts.forEach { contact ->
            val normName = PalestinianPhoneNumberUtils.normalizeArabicText(contact.displayName).trim()
            if (normName.length > 2) {
                nameMap.getOrPut(normName) { mutableListOf() }.add(contact)
            }
        }

        nameMap.filter { it.value.size > 1 }.forEach { (norm, group) ->
            if (!results.any { it.contacts.containsAll(group) }) {
                results.add(DuplicateGroup.PossibleMatch(group, "Similar names: ${group.first().displayName}"))
            }
        }

        return results
    }

    private class ContactBuilder(
        val id: Long,
        val lookupKey: String,
        val displayName: String,
        val photoUri: String?,
        val isStarred: Boolean
    ) {
        val numbers = mutableListOf<String>()
    }
}
