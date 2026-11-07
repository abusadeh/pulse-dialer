package com.example.utils

import com.example.data.model.CallLogItem
import com.example.data.model.GroupedCallLogItem

object CallLogGroupingUtils {

    /**
     * Groups consecutive call logs that refer to the same normalized phone number.
     * Does NOT merge nonconsecutive calls separated by calls from another number.
     * Does NOT group private, unknown, or withheld numbers together.
     */
    fun groupConsecutiveCalls(logs: List<CallLogItem>): List<GroupedCallLogItem> {
        if (logs.isEmpty()) return emptyList()

        val groupedList = mutableListOf<GroupedCallLogItem>()
        var currentPrimary: CallLogItem? = null
        var currentSubLogs = mutableListOf<CallLogItem>()

        for (log in logs) {
            val isPrivateOrUnknown = log.number.isBlank() ||
                    log.number.equals("unknown", ignoreCase = true) ||
                    log.number.equals("private", ignoreCase = true) ||
                    log.number.equals("-1", ignoreCase = true)

            if (currentPrimary == null) {
                currentPrimary = log
                currentSubLogs = mutableListOf(log)
            } else {
                val primaryIsPrivate = currentPrimary.number.isBlank() ||
                        currentPrimary.number.equals("unknown", ignoreCase = true) ||
                        currentPrimary.number.equals("private", ignoreCase = true) ||
                        currentPrimary.number.equals("-1", ignoreCase = true)

                val areEquivalent = if (isPrivateOrUnknown || primaryIsPrivate) {
                    false
                } else {
                    PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers(currentPrimary.number, log.number)
                }

                if (areEquivalent) {
                    currentSubLogs.add(log)
                } else {
                    groupedList.add(GroupedCallLogItem(primaryLog = currentPrimary, subLogs = currentSubLogs.toList()))
                    currentPrimary = log
                    currentSubLogs = mutableListOf(log)
                }
            }
        }

        if (currentPrimary != null) {
            groupedList.add(GroupedCallLogItem(primaryLog = currentPrimary, subLogs = currentSubLogs.toList()))
        }

        return groupedList
    }
}
