package com.example.data.model

data class BlockedNumberItem(
    val number: String,
    val name: String? = null,
    val dateBlockedMillis: Long = System.currentTimeMillis()
)
