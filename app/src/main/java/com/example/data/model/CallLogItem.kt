package com.example.data.model

enum class CallType {
    INCOMING,
    OUTGOING,
    MISSED,
    REJECTED,
    BLOCKED,
    VOICEMAIL,
    UNKNOWN
}

data class CallLogItem(
    val id: Long,
    val number: String,
    val cachedName: String?,
    val dateMillis: Long,
    val durationSeconds: Long,
    val type: CallType,
    val simDisplayName: String? = null,
    val formattedDate: String = "",
    val formattedDuration: String = ""
)
