package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.CallLogItem
import com.example.data.model.CallType
import com.example.data.model.ContactItem
import com.example.data.repository.AutoRecordMode
import com.example.data.repository.CallLogRepository
import com.example.data.repository.ContactsRepository
import com.example.data.repository.RecordingsRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Pulse Dialer", appName)
    }

    @Test
    fun `settings repository persists preferences and roundtrips json`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = SettingsRepository(context)

        repo.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, repo.settingsFlow.value.themeMode)

        repo.setHapticFeedback(false)
        assertEquals(false, repo.settingsFlow.value.hapticFeedbackEnabled)

        repo.setPreferredSimSlot(1)
        assertEquals(1, repo.settingsFlow.value.preferredSimSlot)

        repo.setAutoRecordMode(AutoRecordMode.AUTO_RECORD)
        assertEquals(AutoRecordMode.AUTO_RECORD, repo.settingsFlow.value.autoRecordMode)

        val exportedJson = repo.exportSettingsJson()
        assertTrue(exportedJson.contains("DARK"))
        assertTrue(exportedJson.contains("AUTO_RECORD"))

        // Test import
        val imported = repo.importSettingsJson(exportedJson)
        assertTrue(imported)
        assertEquals(ThemeMode.DARK, repo.settingsFlow.value.themeMode)
    }

    @Test
    fun `call log stats calculation works`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = CallLogRepository(context)

        val sampleLogs = listOf(
            CallLogItem(1, "123", "Alice", 1000L, 60L, CallType.INCOMING),
            CallLogItem(2, "456", "Bob", 2000L, 120L, CallType.OUTGOING),
            CallLogItem(3, "789", null, 3000L, 0L, CallType.MISSED)
        )

        val stats = repo.calculateCallStats(sampleLogs)
        assertEquals(3, stats.totalCalls)
        assertEquals(60L, stats.incomingSeconds)
        assertEquals(120L, stats.outgoingSeconds)
        assertEquals(1, stats.missedCallsCount)
    }

    @Test
    fun `duplicate contact detection works`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = ContactsRepository(context)

        val contacts = listOf(
            ContactItem(1, "key1", "John Doe", listOf("555-1234")),
            ContactItem(2, "key2", "John Doe", listOf("555-9999")),
            ContactItem(3, "key3", "Alice Smith", listOf("555-0000")),
            ContactItem(4, "key4", "Charlie", listOf("555-1234"))
        )

        val duplicates = repo.findDuplicateSuggestions(contacts)
        assertTrue(duplicates.isNotEmpty())
    }

    @Test
    fun `recordings repository directory and storage stats operate cleanly`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = RecordingsRepository(context)

        val dir = repo.getRecordingsDirectory()
        assertTrue(dir.exists())

        val stats = repo.getStorageStats()
        assertTrue(stats.availableBytes >= 0)
        assertTrue(stats.usedBytes >= 0)
    }

    @Test
    fun `launch MainActivity cleanly`() {
        org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
    }
}
