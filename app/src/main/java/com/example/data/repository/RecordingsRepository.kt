package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import androidx.core.content.FileProvider
import com.example.data.model.RecordingItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingsRepository(private val context: Context) {

    private val recordingsDir: File = File(context.filesDir, "recordings").apply {
        if (!exists()) mkdirs()
    }

    private val metadataFile: File = File(context.filesDir, "recordings_metadata.json")

    private val _recordingsFlow = MutableStateFlow<List<RecordingItem>>(emptyList())
    val recordingsFlow: StateFlow<List<RecordingItem>> = _recordingsFlow.asStateFlow()

    fun getRecordingsDirectory(): File = recordingsDir

    suspend fun loadRecordings(): List<RecordingItem> = withContext(Dispatchers.IO) {
        val metadataMap = loadMetadata()
        val files = recordingsDir.listFiles()?.filter {
            it.isFile && (it.name.endsWith(".m4a") || it.name.endsWith(".3gp") || it.name.endsWith(".wav") || it.name.endsWith(".mp3"))
        } ?: emptyList()

        val list = mutableListOf<RecordingItem>()

        for (file in files) {
            val fileId = file.nameWithoutExtension
            val meta = metadataMap[fileId]

            val callerNumber = meta?.takeIf { it.has("callerNumber") && !it.isNull("callerNumber") }?.getString("callerNumber")
            val callerName = meta?.takeIf { it.has("callerName") && !it.isNull("callerName") }?.getString("callerName")
            val dateMillis = meta?.optLong("dateMillis", file.lastModified()) ?: file.lastModified()
            val durationSec = meta?.optLong("durationSeconds", estimateDuration(file.length())) ?: 0L

            list.add(
                RecordingItem(
                    id = fileId,
                    filePath = file.absolutePath,
                    fileName = file.name,
                    callerNumber = callerNumber,
                    callerName = callerName,
                    dateMillis = dateMillis,
                    durationSeconds = durationSec,
                    fileSizeBytes = file.length(),
                    formattedDate = formatDate(dateMillis),
                    formattedDuration = formatDuration(durationSec),
                    formattedSize = formatFileSize(file.length())
                )
            )
        }

        // Sort newest first
        val sorted = list.sortedByDescending { it.dateMillis }
        _recordingsFlow.value = sorted
        sorted
    }

    suspend fun saveRecordingMetadata(
        fileId: String,
        callerNumber: String?,
        callerName: String?,
        dateMillis: Long,
        durationSeconds: Long
    ) = withContext(Dispatchers.IO) {
        val metadataMap = loadMetadata().toMutableMap()
        val obj = JSONObject().apply {
            put("callerNumber", callerNumber ?: "")
            put("callerName", callerName ?: "")
            put("dateMillis", dateMillis)
            put("durationSeconds", durationSeconds)
        }
        metadataMap[fileId] = obj
        persistMetadata(metadataMap)
        loadRecordings()
    }

    suspend fun renameRecording(recordingId: String, newDisplayName: String): Boolean = withContext(Dispatchers.IO) {
        val item = _recordingsFlow.value.firstOrNull { it.id == recordingId } ?: return@withContext false
        val currentFile = File(item.filePath)
        if (!currentFile.exists()) return@withContext false

        val ext = currentFile.extension
        val cleanName = newDisplayName.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val targetName = if (cleanName.endsWith(".$ext")) cleanName else "$cleanName.$ext"
        val newFile = File(recordingsDir, targetName)

        if (currentFile.renameTo(newFile)) {
            val metadataMap = loadMetadata().toMutableMap()
            val existingObj = metadataMap.remove(recordingId) ?: JSONObject()
            val newId = newFile.nameWithoutExtension
            metadataMap[newId] = existingObj
            persistMetadata(metadataMap)
            loadRecordings()
            true
        } else {
            false
        }
    }

    suspend fun deleteRecording(recordingId: String): Boolean = withContext(Dispatchers.IO) {
        val item = _recordingsFlow.value.firstOrNull { it.id == recordingId } ?: return@withContext false
        val file = File(item.filePath)
        val deleted = if (file.exists()) file.delete() else true

        if (deleted) {
            val metadataMap = loadMetadata().toMutableMap()
            metadataMap.remove(recordingId)
            persistMetadata(metadataMap)
            loadRecordings()
        }
        deleted
    }

    fun getShareIntent(recording: RecordingItem): Intent? {
        val file = File(recording.filePath)
        if (!file.exists()) return null

        val authority = "${context.packageName}.fileprovider"
        val uri: Uri = FileProvider.getUriForFile(context, authority, file)

        return Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Call Recording: ${recording.callerName ?: recording.callerNumber ?: recording.fileName}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun getStorageStats(): StorageStats {
        var usedBytes = 0L
        recordingsDir.listFiles()?.forEach { if (it.isFile) usedBytes += it.length() }

        val (availableBytes, totalBytes) = try {
            val stat = StatFs(recordingsDir.path)
            val avail = stat.availableBlocksLong * stat.blockSizeLong
            val total = stat.blockCountLong * stat.blockSizeLong
            avail to total
        } catch (_: Exception) {
            0L to 0L
        }

        return StorageStats(
            usedBytes = usedBytes,
            availableBytes = availableBytes,
            totalBytes = totalBytes,
            formattedUsed = formatFileSize(usedBytes),
            formattedAvailable = formatFileSize(availableBytes)
        )
    }

    suspend fun cleanupOldRecordings(retentionDays: Int): Int = withContext(Dispatchers.IO) {
        if (retentionDays <= 0) return@withContext 0
        val cutoffMillis = System.currentTimeMillis() - (retentionDays * 24L * 60L * 60L * 1000L)
        val toDelete = _recordingsFlow.value.filter { it.dateMillis < cutoffMillis }

        var deletedCount = 0
        for (item in toDelete) {
            if (deleteRecording(item.id)) {
                deletedCount++
            }
        }
        deletedCount
    }

    private fun loadMetadata(): Map<String, JSONObject> {
        if (!metadataFile.exists()) return emptyMap()
        val map = mutableMapOf<String, JSONObject>()
        try {
            val jsonStr = metadataFile.readText()
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id", "")
                if (id.isNotBlank()) {
                    map[id] = obj
                }
            }
        } catch (_: Exception) {
            // Ignore corruption
        }
        return map
    }

    private fun persistMetadata(map: Map<String, JSONObject>) {
        try {
            val array = JSONArray()
            for ((id, obj) in map) {
                obj.put("id", id)
                array.put(obj)
            }
            metadataFile.writeText(array.toString())
        } catch (_: Exception) {
            // Safe fallback
        }
    }

    private fun formatDate(timeMillis: Long): String {
        val sdf = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
        return sdf.format(Date(timeMillis))
    }

    private fun formatDuration(seconds: Long): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 KB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) {
            String.format(Locale.US, "%.1f MB", mb)
        } else {
            String.format(Locale.US, "%.0f KB", kb)
        }
    }

    private fun estimateDuration(fileSizeBytes: Long): Long {
        // Approximate 16 KB/sec for standard compressed voice audio (AAC 128kbps)
        return (fileSizeBytes / 16000L).coerceAtLeast(1L)
    }

    data class StorageStats(
        val usedBytes: Long,
        val availableBytes: Long,
        val totalBytes: Long,
        val formattedUsed: String,
        val formattedAvailable: String
    )
}
