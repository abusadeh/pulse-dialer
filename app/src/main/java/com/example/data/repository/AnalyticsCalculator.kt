package com.example.data.repository

import com.example.data.model.CallLogItem
import com.example.data.model.CallType
import com.example.utils.PalestinianPhoneNumberUtils
import java.util.Calendar

enum class TimeRangeFilter(val displayName: String) {
    TODAY("Today"),
    LAST_7_DAYS("Last 7 Days"),
    LAST_30_DAYS("Last 30 Days"),
    ALL("All History")
}

data class FrequentContactStats(
    val number: String,
    val displayName: String,
    val callCount: Int,
    val totalDurationSeconds: Long,
    val formattedDuration: String,
    val incomingCount: Int = 0,
    val outgoingCount: Int = 0,
    val missedCount: Int = 0
)

data class EnhancedCallStats(
    val totalCalls: Int,
    val incomingSeconds: Long,
    val outgoingSeconds: Long,
    val incomingCallsCount: Int = 0,
    val outgoingCallsCount: Int = 0,
    val missedCallsCount: Int,
    val rejectedCallsCount: Int,
    val totalDurationSeconds: Long,
    val formattedIncomingDuration: String,
    val formattedOutgoingDuration: String,
    val totalDurationFormatted: String,
    val frequentContacts: List<FrequentContactStats> = emptyList()
)

object AnalyticsCalculator {

    fun filterLogsByTimeRange(logs: List<CallLogItem>, range: TimeRangeFilter): List<CallLogItem> {
        if (range == TimeRangeFilter.ALL) return logs

        val now = Calendar.getInstance()
        val cutoff = Calendar.getInstance()

        when (range) {
            TimeRangeFilter.TODAY -> {
                cutoff.set(Calendar.HOUR_OF_DAY, 0)
                cutoff.set(Calendar.MINUTE, 0)
                cutoff.set(Calendar.SECOND, 0)
                cutoff.set(Calendar.MILLISECOND, 0)
            }
            TimeRangeFilter.LAST_7_DAYS -> {
                cutoff.add(Calendar.DAY_OF_YEAR, -7)
            }
            TimeRangeFilter.LAST_30_DAYS -> {
                cutoff.add(Calendar.DAY_OF_YEAR, -30)
            }
            TimeRangeFilter.ALL -> {}
        }

        val cutoffMillis = cutoff.timeInMillis
        return logs.filter { it.dateMillis >= cutoffMillis }
    }

    fun calculateEnhancedStats(logs: List<CallLogItem>): EnhancedCallStats {
        var inSecs = 0L
        var outSecs = 0L
        var inCount = 0
        var outCount = 0
        var missed = 0
        var rejected = 0
        var totalSecs = 0L

        val contactGroupMap = mutableMapOf<String, MutableList<CallLogItem>>()

        for (log in logs) {
            totalSecs += log.durationSeconds
            when (log.type) {
                CallType.INCOMING -> {
                    inSecs += log.durationSeconds
                    inCount++
                }
                CallType.OUTGOING -> {
                    outSecs += log.durationSeconds
                    outCount++
                }
                CallType.MISSED -> missed++
                CallType.REJECTED -> rejected++
                else -> {}
            }

            if (log.number.isNotBlank() && !log.number.equals("unknown", ignoreCase = true) && !log.number.equals("private", ignoreCase = true)) {
                val key = PalestinianPhoneNumberUtils.normalizePalestinianPhoneNumber(log.number)
                    ?: PalestinianPhoneNumberUtils.normalizePhoneNumber(log.number)
                    ?: log.number
                contactGroupMap.getOrPut(key) { mutableListOf() }.add(log)
            }
        }

        val frequentList = contactGroupMap.map { (key, groupLogs) ->
            val name = groupLogs.firstNotNullOfOrNull { it.cachedName?.takeIf { n -> n.isNotBlank() && n != it.number } }
                ?: groupLogs.first().number
            val count = groupLogs.size
            val dur = groupLogs.sumOf { it.durationSeconds }
            val inc = groupLogs.count { it.type == CallType.INCOMING }
            val out = groupLogs.count { it.type == CallType.OUTGOING }
            val mis = groupLogs.count { it.type == CallType.MISSED }
            FrequentContactStats(
                number = groupLogs.first().number,
                displayName = name,
                callCount = count,
                totalDurationSeconds = dur,
                formattedDuration = formatDuration(dur),
                incomingCount = inc,
                outgoingCount = out,
                missedCount = mis
            )
        }.sortedByDescending { it.callCount }.take(10)

        return EnhancedCallStats(
            totalCalls = logs.size,
            incomingSeconds = inSecs,
            outgoingSeconds = outSecs,
            incomingCallsCount = inCount,
            outgoingCallsCount = outCount,
            missedCallsCount = missed,
            rejectedCallsCount = rejected,
            totalDurationSeconds = totalSecs,
            formattedIncomingDuration = formatDuration(inSecs),
            formattedOutgoingDuration = formatDuration(outSecs),
            totalDurationFormatted = formatDuration(totalSecs),
            frequentContacts = frequentList
        )
    }

    private fun formatDuration(seconds: Long): String {
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
}
