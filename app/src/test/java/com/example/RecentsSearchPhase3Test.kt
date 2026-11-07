package com.example

import com.example.data.model.CallLogItem
import com.example.data.model.CallType
import com.example.utils.PalestinianPhoneNumberUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentsSearchPhase3Test {

    @Test
    fun testArabicNameNormalizationAndMatching() {
        // Required test cases:
        // احمد matches أحمد
        // ابراهيم matches إبراهيم
        // علي matches عَلِيّ

        val query1 = "احمد"
        val name1 = "أحمد محمد"
        val normQuery1 = PalestinianPhoneNumberUtils.normalizeArabicText(query1)
        val normName1 = PalestinianPhoneNumberUtils.normalizeArabicText(name1)
        assertTrue(normName1.contains(normQuery1))

        val query2 = "ابراهيم"
        val name2 = "إبراهيم خليل"
        val normQuery2 = PalestinianPhoneNumberUtils.normalizeArabicText(query2)
        val normName2 = PalestinianPhoneNumberUtils.normalizeArabicText(name2)
        assertTrue(normName2.contains(normQuery2))

        val query3 = "علي"
        val name3 = "عَلِيّ حسن"
        val normQuery3 = PalestinianPhoneNumberUtils.normalizeArabicText(query3)
        val normName3 = PalestinianPhoneNumberUtils.normalizeArabicText(name3)
        assertTrue(normName3.contains(normQuery3))
    }

    @Test
    fun testMultiWordAndPartialNameSearch() {
        val contactName = "محمد أحمد الخليل"
        val normName = PalestinianPhoneNumberUtils.normalizeArabicText(contactName)

        val queryTokens1 = PalestinianPhoneNumberUtils.normalizeArabicText("أحمد الخليل").split(Regex("\\s+")).filter { it.isNotBlank() }
        assertTrue(queryTokens1.all { normName.contains(it) })

        val queryTokens2 = PalestinianPhoneNumberUtils.normalizeArabicText("محمد خليل").split(Regex("\\s+")).filter { it.isNotBlank() }
        assertTrue(queryTokens2.all { normName.contains(it) })

        val queryTokens3 = PalestinianPhoneNumberUtils.normalizeArabicText("زياد احمد").split(Regex("\\s+")).filter { it.isNotBlank() }
        assertFalse(queryTokens3.all { normName.contains(it) })
    }

    @Test
    fun testPalestinianPhoneNumberEquivalenceInCallLogSearch() {
        val logNumber = "+970594091090"
        val equivalentFormats = listOf(
            "0594091090",
            "594091090",
            "+970594091090",
            "00970594091090",
            "+972594091090",
            "00972594091090"
        )

        for (fmt in equivalentFormats) {
            assertTrue(PalestinianPhoneNumberUtils.matchesQuery(logNumber, fmt))
        }

        // 056 Ooredoo equivalence
        val logNumber056 = "00970568987654"
        val equivalentFormats056 = listOf(
            "0568987654",
            "568987654",
            "+970568987654",
            "00970568987654",
            "+972568987654",
            "00972568987654"
        )

        for (fmt in equivalentFormats056) {
            assertTrue(PalestinianPhoneNumberUtils.matchesQuery(logNumber056, fmt))
        }
    }

    @Test
    fun testIncompletePrefixAndUnrelatedNumbersSearch() {
        val logNumber = "0594091090"

        // Incomplete prefixes normalize to empty string in normalizeSearchQuery and do not match unrelated numbers
        assertEquals("", PalestinianPhoneNumberUtils.normalizeSearchQuery("+970"))
        assertEquals("", PalestinianPhoneNumberUtils.normalizeSearchQuery("00970"))

        // Unrelated numbers should not match
        assertFalse(PalestinianPhoneNumberUtils.matchesQuery(logNumber, "+14155552671"))
    }

    @Test
    fun testEmptySearchQueryMatchesAll() {
        val sampleLogs = listOf(
            CallLogItem(1, "0594091090", "Ahmad", 1000L, 30L, CallType.INCOMING),
            CallLogItem(2, "0568987654", "Noor", 2000L, 45L, CallType.OUTGOING)
        )

        val searchQuery = ""
        val filtered = sampleLogs.filter { log ->
            if (searchQuery.isBlank()) {
                true
            } else {
                val nameMatch = log.cachedName?.contains(searchQuery, ignoreCase = true) == true
                val numMatch = PalestinianPhoneNumberUtils.matchesQuery(log.number, searchQuery)
                nameMatch || numMatch
            }
        }

        assertEquals(2, filtered.size)
    }
}
