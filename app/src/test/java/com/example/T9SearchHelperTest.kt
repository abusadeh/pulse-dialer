package com.example

import com.example.ui.components.T9SearchHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class T9SearchHelperTest {

    @Test
    fun testSpacesDoNotProduceDigitsAndPreserveWordBoundaries() {
        val t9Name = T9SearchHelper.textToT9("أحمد خليل")
        // 'أحمد' -> '2384', space -> ' ', 'خليل' -> '3797'
        assertEquals("2384 3797", t9Name)

        // Space character must NOT produce '2'
        val spaceOnly = T9SearchHelper.textToT9("   ")
        assertEquals("   ", spaceOnly)
        assertFalse(spaceOnly.contains("2"))
    }

    @Test
    fun testSecondAndThirdNameSearchInArabic() {
        val displayName = "أحمد خليل"
        val numbers = listOf("0599123456")

        // 'خليل' -> 3797 (خ=3, ل=7, ي=9, ل=7)
        // Searching for second name using its T9 sequence
        assertTrue("Prefix 3 should match خليل", T9SearchHelper.matches("3", displayName, numbers))
        assertTrue("Prefix 37 should match خليل", T9SearchHelper.matches("37", displayName, numbers))
        assertTrue("Prefix 379 should match خليل", T9SearchHelper.matches("379", displayName, numbers))
        assertTrue("Full 3797 should match خليل", T9SearchHelper.matches("3797", displayName, numbers))

        // Searching for first name 'أحمد' -> 2384
        assertTrue("Prefix 2 should match أحمد", T9SearchHelper.matches("2", displayName, numbers))
        assertTrue("Prefix 23 should match أحمد", T9SearchHelper.matches("23", displayName, numbers))
        assertTrue("Full 2384 should match أحمد", T9SearchHelper.matches("2384", displayName, numbers))

        // Full continuous T9 string
        assertTrue(T9SearchHelper.matches("23843797", displayName, numbers))

        // Three-word name
        val threeWords = "طارق عمر نمر"
        // طارق -> 6247 (ط=6, ا=2, ر=4, ق=7), عمر -> 684 (ع=6, م=8, ر=4), نمر -> 884 (ن=8, م=8, ر=4)
        assertTrue("Should match 3rd name نمر", T9SearchHelper.matches("884", threeWords, numbers))
        assertTrue("Should match 2nd name عمر", T9SearchHelper.matches("684", threeWords, numbers))
        assertTrue("Should match 1st name طارق", T9SearchHelper.matches("6247", threeWords, numbers))
    }

    @Test
    fun testNoFalsePositivesOnKey2FromSpaces() {
        // Phone number without digit '2'
        val numbersWithoutTwo = listOf("0599787878")

        // 'سمير عمر' -> س=5, م=8, ي=9, ر=4 / ع=6, م=8, ر=4 (no letter maps to key 2)
        // With space bug, space between names was mapped to '2', falsely matching key 2.
        val contactNoKey2 = "سمير عمر"
        assertFalse("Key 2 should NOT match contact where no word starts with 2 and space is not 2",
            T9SearchHelper.matches("2", contactNoKey2, numbersWithoutTwo))
    }

    @Test
    fun testEnglishNamesT9Matching() {
        val displayName = "John Doe"
        val numbers = listOf("0599123456")

        // John -> 5646, Doe -> 363
        assertTrue("Should match second name Doe with 363", T9SearchHelper.matches("363", displayName, numbers))
        assertTrue("Should match second name Doe prefix with 36", T9SearchHelper.matches("36", displayName, numbers))
        assertTrue("Should match first name John with 5646", T9SearchHelper.matches("5646", displayName, numbers))
        assertTrue("Should match continuous JohnDoe", T9SearchHelper.matches("5646363", displayName, numbers))

        // Unrelated sequence
        assertFalse(T9SearchHelper.matches("999", displayName, numbers))
    }

    @Test
    fun testArabicKeypadLetterMappings() {
        // Key 2: أ إ آ ا ء ب ت ث
        assertEquals("22222222", T9SearchHelper.textToT9("أإآاءبتث"))

        // Key 3: ج ح خ
        assertEquals("333", T9SearchHelper.textToT9("جحخ"))

        // Key 4: د ذ ر ز
        assertEquals("4444", T9SearchHelper.textToT9("دذرز"))

        // Key 5: س ش ص ض
        assertEquals("5555", T9SearchHelper.textToT9("سشصض"))

        // Key 6: ط ظ ع غ
        assertEquals("6666", T9SearchHelper.textToT9("طظعغ"))

        // Key 7: ف ق ك ل
        assertEquals("7777", T9SearchHelper.textToT9("فقكل"))

        // Key 8: م ن ه ة
        assertEquals("8888", T9SearchHelper.textToT9("منهة"))

        // Key 9: و ي ى ئ ؤ
        assertEquals("99999", T9SearchHelper.textToT9("ويىئؤ"))
    }

    @Test
    fun testPhoneNumberMatchingThroughT9() {
        val displayName = "Dr. Tariq"
        val numbers = listOf("0594091090")

        // Exact number match
        assertTrue(T9SearchHelper.matches("0594091090", displayName, numbers))

        // Partial Palestinian local match
        assertTrue(T9SearchHelper.matches("05940", displayName, numbers))

        // 9-digit format without leading zero
        assertTrue(T9SearchHelper.matches("59409", displayName, numbers))

        // International format query matching local number
        assertTrue(T9SearchHelper.matches("+970594091090", displayName, numbers))

        // Eastern Arabic numerals query
        assertTrue(T9SearchHelper.matches("٠٥٩٤٠٩١٠٩٠", displayName, numbers))

        // Unrelated query
        assertFalse(T9SearchHelper.matches("111222", displayName, numbers))
    }
}
