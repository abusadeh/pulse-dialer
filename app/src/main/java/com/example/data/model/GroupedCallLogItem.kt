package com.example.data.model

data class GroupedCallLogItem(
    val primaryLog: CallLogItem,
    val subLogs: List<CallLogItem>, // consecutive calls from the same normalized number
    val isExpanded: Boolean = false
)
