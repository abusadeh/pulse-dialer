package com.example.data.model

data class FavoriteItem(
    val id: String,
    val contactId: Long? = null,
    val displayName: String,
    val phoneNumber: String,
    val photoUri: String? = null,
    val isDeviceStarred: Boolean = false
)
