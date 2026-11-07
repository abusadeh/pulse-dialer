package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.CallLogItem
import com.example.data.model.CallType
import com.example.data.repository.AnalyticsCalculator
import com.example.data.repository.BlockedNumbersRepository
import com.example.data.repository.CallLogRepository
import com.example.data.repository.ContactsRepository
import com.example.data.repository.FavoritesRepository
import com.example.data.repository.TimeRangeFilter
import com.example.utils.CallLogGroupingUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase4AdvancedFeaturesTest {

    @Test
    fun testConsecutiveCallGroupingAndEquivalence() {
        val now = System.currentTimeMillis()
        val logs = listOf(
            CallLogItem(1L, "0594091090", "Ahmad", now, 10L, CallType.INCOMING),
            CallLogItem(2L, "+970594091090", "Ahmad", now - 1000L, 20L, CallType.INCOMING),
            CallLogItem(3L, "0568987654", "Noor", now - 2000L, 5L, CallType.OUTGOING),
            CallLogItem(4L, "0594091090", "Ahmad", now - 3000L, 0L, CallType.MISSED)
        )

        val grouped = CallLogGroupingUtils.groupConsecutiveCalls(logs)
        assertEquals(3, grouped.size)
        assertEquals(2, grouped[0].subLogs.size)
        assertEquals(1, grouped[1].subLogs.size)
        assertEquals(1, grouped[2].subLogs.size)
    }

    @Test
    fun testPrivateAndUnknownNumbersNotGrouped() {
        val now = System.currentTimeMillis()
        val logs = listOf(
            CallLogItem(1L, "Unknown", null, now, 0L, CallType.MISSED),
            CallLogItem(2L, "Unknown", null, now - 1000L, 0L, CallType.MISSED),
            CallLogItem(3L, "Private", null, now - 2000L, 0L, CallType.MISSED)
        )

        val grouped = CallLogGroupingUtils.groupConsecutiveCalls(logs)
        assertEquals(3, grouped.size)
    }

    @Test
    fun testPermissionDeniedReturnsEmptyListNoFabricatedData() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = CallLogRepository(context)

        // Without READ_CALL_LOG permission granted in test context
        val logs = repo.getCallLogs(null)
        assertTrue(logs.isEmpty())
    }

    @Test
    fun testAnalyticsTimeRangeBoundariesAndFrequentContacts() {
        val now = System.currentTimeMillis()
        val oneDayMillis = 24 * 60 * 60 * 1000L

        val logs = listOf(
            CallLogItem(1L, "0594091090", "Ahmad", now - 1000L, 60L, CallType.INCOMING),
            CallLogItem(2L, "0594091090", "Ahmad", now - 2000L, 120L, CallType.OUTGOING),
            CallLogItem(3L, "0568987654", "Noor", now - (2 * oneDayMillis), 30L, CallType.INCOMING),
            CallLogItem(4L, "0591112233", "Tariq", now - (10 * oneDayMillis), 45L, CallType.OUTGOING)
        )

        val last7Days = AnalyticsCalculator.filterLogsByTimeRange(logs, TimeRangeFilter.LAST_7_DAYS)
        assertEquals(3, last7Days.size)

        val todayLogs = AnalyticsCalculator.filterLogsByTimeRange(logs, TimeRangeFilter.TODAY)
        assertEquals(2, todayLogs.size)

        val last30Days = AnalyticsCalculator.filterLogsByTimeRange(logs, TimeRangeFilter.LAST_30_DAYS)
        assertEquals(4, last30Days.size)

        val allHistory = AnalyticsCalculator.filterLogsByTimeRange(logs, TimeRangeFilter.ALL)
        assertEquals(4, allHistory.size)
        assertEquals("All History", TimeRangeFilter.ALL.displayName)
        assertEquals("Today", TimeRangeFilter.TODAY.displayName)
        assertEquals("Last 7 Days", TimeRangeFilter.LAST_7_DAYS.displayName)
        assertEquals("Last 30 Days", TimeRangeFilter.LAST_30_DAYS.displayName)

        val stats = AnalyticsCalculator.calculateEnhancedStats(logs)
        assertEquals(4, stats.totalCalls)
        assertEquals(255L, stats.totalDurationSeconds)
        assertEquals(2, stats.incomingCallsCount)
        assertEquals(2, stats.outgoingCallsCount)
        assertTrue(stats.frequentContacts.isNotEmpty())
        assertEquals("Ahmad", stats.frequentContacts.first().displayName)
        assertEquals(2, stats.frequentContacts.first().callCount)
        assertEquals(1, stats.frequentContacts.first().incomingCount)
        assertEquals(1, stats.frequentContacts.first().outgoingCount)
    }

    @Test
    fun testQuickActionsFavoriteAndBlock() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val contactsRepo = ContactsRepository(context)
        val favRepo = FavoritesRepository(context, contactsRepo)
        val blockRepo = BlockedNumbersRepository(context)

        favRepo.addCustomFavorite("Test Friend", "0594091090")
        favRepo.loadFavorites()
        assertTrue(favRepo.isFavorite("+970594091090"))

        blockRepo.blockNumber("0594091090", "Test Friend")
        assertTrue(blockRepo.isNumberBlocked("+972594091090"))

        blockRepo.unblockNumber("+970594091090")
        assertFalse(blockRepo.isNumberBlocked("0594091090"))
    }

    @Test
    fun testQuickActionsSaveContactAndSmsIntents() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val contactsRepo = ContactsRepository(context)

        // Save Contact quick action intent
        val addIntent = contactsRepo.createAddContactIntent(phoneNumber = "0594091090", name = "Test Friend")
        assertEquals(android.content.Intent.ACTION_INSERT, addIntent.action)
        assertEquals("0594091090", addIntent.getStringExtra(android.provider.ContactsContract.Intents.Insert.PHONE))
        assertEquals("Test Friend", addIntent.getStringExtra(android.provider.ContactsContract.Intents.Insert.NAME))

        // SMS Intent quick action format
        val smsUri = android.net.Uri.parse("smsto:0594091090")
        val smsIntent = android.content.Intent(android.content.Intent.ACTION_SENDTO, smsUri)
        assertEquals(android.content.Intent.ACTION_SENDTO, smsIntent.action)
        assertEquals("smsto:0594091090", smsIntent.data?.toString())
    }
}

