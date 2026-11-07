package com.example.data.model

data class RecordingItem(
    val id: String,
    val filePath: String,
    val fileName: String,
    val callerNumber: String?,
    val callerName: String?,
    val dateMillis: Long,
    val durationSeconds: Long,
    val fileSizeBytes: Long = 0L,
    val formattedDate: String = "",
    val formattedDuration: String = "",
    val formattedSize: String = ""
)
