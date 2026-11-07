package com.example.data.repository

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.example.data.model.CallLogItem
import com.example.data.model.CallType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CallLogRepository(private val context: Context) {

    companion object {
        /** Upper bound on call-log rows read at once (statistics cover this window too). */
        const val MAX_CALL_LOG_ENTRIES = 2000
    }

    private val contentResolver: ContentResolver = context.contentResolver

    fun hasCallLogPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasWriteCallLogPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun getCallLogs(filter: CallType? = null): List<CallLogItem> = withContext(Dispatchers.IO) {
        if (!hasCallLogPermission()) {
            return@withContext emptyList()
        }

        val list = mutableListOf<CallLogItem>()
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.TYPE
        )

        val selection = when (filter) {
            CallType.MISSED -> "${CallLog.Calls.TYPE} = ${CallLog.Calls.MISSED_TYPE}"
            CallType.INCOMING -> "${CallLog.Calls.TYPE} = ${CallLog.Calls.INCOMING_TYPE}"
            CallType.OUTGOING -> "${CallLog.Calls.TYPE} = ${CallLog.Calls.OUTGOING_TYPE}"
            CallType.REJECTED -> "${CallLog.Calls.TYPE} = ${CallLog.Calls.REJECTED_TYPE}"
            else -> null
        }

        try {
            contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CallLog.Calls._ID)
                val numIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
                val durIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)
                val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)

                // Newest first; older entries beyond the cap are not loaded into memory.
                while (list.size < MAX_CALL_LOG_ENTRIES && cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val number = if (numIdx != -1) cursor.getString(numIdx) ?: "Unknown" else "Unknown"
                    val cachedName = if (nameIdx != -1) cursor.getString(nameIdx) else null
                    val dateMillis = if (dateIdx != -1) cursor.getLong(dateIdx) else 0L
                    val durationSec = if (durIdx != -1) cursor.getLong(durIdx) else 0L
                    val rawType = if (typeIdx != -1) cursor.getInt(typeIdx) else 0

                    val callType = when (rawType) {
                        CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
                        CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
                        CallLog.Calls.MISSED_TYPE -> CallType.MISSED
                        CallLog.Calls.REJECTED_TYPE -> CallType.REJECTED
                        CallLog.Calls.BLOCKED_TYPE -> CallType.BLOCKED
                        CallLog.Calls.VOICEMAIL_TYPE -> CallType.VOICEMAIL
                        else -> CallType.UNKNOWN
                    }

                    list.add(
                        CallLogItem(
                            id = id,
                            number = number,
                            cachedName = cachedName,
                            dateMillis = dateMillis,
                            durationSeconds = durationSec,
                            type = callType,
                            formattedDate = formatCallDate(dateMillis),
                            formattedDuration = formatCallDuration(durationSec, callType)
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
            return@withContext emptyList()
        } catch (_: Exception) {
            return@withContext emptyList()
        }

        list
    }


    suspend fun deleteCallLog(callId: Long): Boolean = withContext(Dispatchers.IO) {
        if (!hasWriteCallLogPermission()) return@withContext false
        try {
            val count = contentResolver.delete(
                CallLog.Calls.CONTENT_URI,
                "${CallLog.Calls._ID} = ?",
                arrayOf(callId.toString())
            )
            count > 0
        } catch (_: Exception) {
            false
        }
    }

    suspend fun clearAllCallLogs(): Boolean = withContext(Dispatchers.IO) {
        if (!hasWriteCallLogPermission()) return@withContext false
        try {
            contentResolver.delete(CallLog.Calls.CONTENT_URI, null, null)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun formatCallDate(timeMillis: Long): String {
        if (timeMillis <= 0) return ""
        val now = Calendar.getInstance()
        val callTime = Calendar.getInstance().apply { timeInMillis = timeMillis }

        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val formattedTime = timeFormat.format(Date(timeMillis))

        val isToday = now.get(Calendar.YEAR) == callTime.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == callTime.get(Calendar.DAY_OF_YEAR)

        if (isToday) {
            return "Today, $formattedTime"
        }

        // Compare against "now minus one day" so Dec 31 -> Jan 1 also counts as yesterday.
        val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        val isYesterday = yesterday.get(Calendar.YEAR) == callTime.get(Calendar.YEAR) &&
                yesterday.get(Calendar.DAY_OF_YEAR) == callTime.get(Calendar.DAY_OF_YEAR)

        if (isYesterday) {
            return "Yesterday, $formattedTime"
        }

        val isSameYear = now.get(Calendar.YEAR) == callTime.get(Calendar.YEAR)
        val dateFormat = if (isSameYear) {
            SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        } else {
            SimpleDateFormat("MMM d, yyyy, h:mm a", Locale.getDefault())
        }
        return dateFormat.format(Date(timeMillis))
    }

    private fun formatCallDuration(seconds: Long, callType: CallType): String {
        if (callType == CallType.MISSED) return "Missed"
        if (callType == CallType.REJECTED) return "Declined"
        if (callType == CallType.BLOCKED) return "Blocked"
        if (seconds <= 0) return "0s"

        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val remainingSecs = seconds % 60

        return when {
            hours > 0 -> "${hours}h ${minutes}m ${remainingSecs}s"
            minutes > 0 -> "${minutes}m ${remainingSecs}s"
            else -> "${remainingSecs}s"
        }
    }

    fun calculateCallStats(logs: List<CallLogItem>): CallStats {
        var inSecs = 0L
        var outSecs = 0L
        var missed = 0
        var rejected = 0
        var totalSecs = 0L

        for (log in logs) {
            totalSecs += log.durationSeconds
            when (log.type) {
                CallType.INCOMING -> inSecs += log.durationSeconds
                CallType.OUTGOING -> outSecs += log.durationSeconds
                CallType.MISSED -> missed++
                CallType.REJECTED -> rejected++
                else -> {}
            }
        }

        return CallStats(
            totalCalls = logs.size,
            incomingSeconds = inSecs,
            outgoingSeconds = outSecs,
            missedCallsCount = missed,
            rejectedCallsCount = rejected,
            totalDurationSeconds = totalSecs,
            formattedIncomingDuration = formatCallDuration(inSecs, CallType.INCOMING),
            formattedOutgoingDuration = formatCallDuration(outSecs, CallType.OUTGOING),
            totalDurationFormatted = formatCallDuration(totalSecs, CallType.INCOMING)
        )
    }
}

data class CallStats(
    val totalCalls: Int,
    val incomingSeconds: Long,
    val outgoingSeconds: Long,
    val missedCallsCount: Int,
    val rejectedCallsCount: Int = 0,
    val totalDurationSeconds: Long = 0L,
    val formattedIncomingDuration: String,
    val formattedOutgoingDuration: String,
    val totalDurationFormatted: String = "0s"
)
