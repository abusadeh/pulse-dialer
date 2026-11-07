package com.example.data.model

data class ContactItem(
    val id: Long,
    val lookupKey: String,
    val displayName: String,
    val phoneNumbers: List<String>,
    val photoUri: String? = null,
    val isStarred: Boolean = false,
    val initial: Char = displayName.firstOrNull()?.uppercaseChar() ?: '#'
)
