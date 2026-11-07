package com.example

import com.example.utils.PalestinianPhoneNumberUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PalestinianPhoneNumberUtilsTest {

    @Test
    fun testPalestinianMobileNormalization() {
        val canonical = "0594091090"

        assertEquals(canonical, PalestinianPhoneNumberUtils.normalizePalestinianPhoneNumber("0594091090"))
        assertEquals(canonical, PalestinianPhoneNumberUtils.normalizePalestinianPhoneNumber("594091090"))
        assertEquals(canonical, PalestinianPhoneNumberUtils.normalizePalestinianPhoneNumber("+970594091090"))
        assertEquals(canonical, PalestinianPhoneNumberUtils.normalizePalestinianPhoneNumber("00970594091090"))
        assertEquals(canonical, PalestinianPhoneNumberUtils.normalizePalestinianPhoneNumber("970594091090"))
        assertEquals(canonical, PalestinianPhoneNumberUtils.normalizePalestinianPhoneNumber("+972594091090"))
        assertEquals(canonical, PalestinianPhoneNumberUtils.normalizePalestinianPhoneNumber("00972594091090"))
        assertEquals(canonical, PalestinianPhoneNumberUtils.normalizePalestinianPhoneNumber("972594091090"))
    }

    @Test
    fun testOoredoo056MobileNormalization() {
        val canonical = "0568987654"

        assertEquals(canonical, PalestinianPhoneNumberUtils.normalizePalestinianPhoneNumber("0568987654"))
        assertEquals(canonical, PalestinianPhoneNumberUtils.normalizePalestinianPhoneNumber("568987654"))
        assertEquals(canonical, PalestinianPhoneNumberUtils.normalizePalestinianPhoneNumber("+970568987654"))
        assertEquals(canonical, PalestinianPhoneNumberUtils.normalizePalestinianPhoneNumber("00970568987654"))
        assertEquals(canonical, PalestinianPhoneNumberUtils.normalizePalestinianPhoneNumber("970568987654"))
        assertEquals(canonical, PalestinianPhoneNumberUtils.normalizePalestinianPhoneNumber("+972568987654"))
        assertEquals(canonical, PalestinianPhoneNumberUtils.normalizePalestinianPhoneNumber("00972568987654"))
        assertEquals(canonical, PalestinianPhoneNumberUtils.normalizePalestinianPhoneNumber("972568987654"))
    }

    @Test
    fun testEquivalenceRequiredCases() {
        val local = "0594091090"

        assertTrue(PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers(local, "594091090"))
        assertTrue(PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers(local, "00970594091090"))
        assertTrue(PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers(local, "+970594091090"))
        assertTrue(PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers(local, "00972594091090"))
        assertTrue(PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers(local, "+972594091090"))
        assertTrue(PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers(local, "970594091090"))
        assertTrue(PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers(local, "972594091090"))

        // Ooredoo 056 equivalence
        val local056 = "0568987654"
        assertTrue(PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers(local056, "568987654"))
        assertTrue(PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers(local056, "+970568987654"))
        assertTrue(PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers(local056, "00970568987654"))
        assertTrue(PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers(local056, "+972568987654"))
        assertTrue(PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers(local056, "00972568987654"))
        assertTrue(PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers("568987654", "+970568987654"))
    }

    @Test
    fun testArabicAndPersianNumeralsNormalization() {
        // Eastern Arabic numerals (٠-٩)
        assertEquals("0594091090", PalestinianPhoneNumberUtils.normalizePalestinianPhoneNumber("٠٥٩٤٠٩١٠٩٠"))
        assertEquals("0594091090", PalestinianPhoneNumberUtils.normalizePalestinianPhoneNumber("٥٩٤٠٩١٠٩٠"))
        assertEquals("0594091090", PalestinianPhoneNumberUtils.normalizePalestinianPhoneNumber("+٩٧٠٥٩٤٠٩١٠٩٠"))
        assertEquals("0568987654", PalestinianPhoneNumberUtils.normalizePalestinianPhoneNumber("٠٥٦٨٩٨٧٦٥٤"))

        // Persian numerals (۰-۹)
        assertEquals("0594091090", PalestinianPhoneNumberUtils.normalizePalestinianPhoneNumber("۰۵۹۴۰۹۱۰۹۰"))
        assertEquals("0594091090", PalestinianPhoneNumberUtils.normalizePalestinianPhoneNumber("۵۹۴۰۹۱۰۹۰"))
        assertEquals("0594091090", PalestinianPhoneNumberUtils.normalizePalestinianPhoneNumber("+۹۷۰۵۹۴۰۹۱۰۹۰"))
        assertEquals("0568987654", PalestinianPhoneNumberUtils.normalizePalestinianPhoneNumber("۰۵۶۸۹۸۷۶۵۴"))

        // Equivalence across numeral systems
        assertTrue(PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers("0594091090", "٠٥٩٤٠٩١٠٩٠"))
        assertTrue(PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers("0594091090", "۰۵۹۴۰۹۱۰۹۰"))
        assertTrue(PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers("٠٥٩٤٠٩١٠٩٠", "+970594091090"))
        assertTrue(PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers("۰۵۶۸۹۸۷۶۵۴", "+972568987654"))
    }

    @Test
    fun testSpacesAndHyphens() {
        assertTrue(PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers("059-409-1090", "+970 59 409 1090"))
        assertTrue(PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers("(059) 409 1090", "00972-59-409-1090"))
        assertTrue(PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers("59 409 1090", "+970594091090"))
        assertTrue(PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers("(056) 898-7654", "+970 56 898 7654"))
    }

    @Test
    fun testInvalidLengthsReturnNullAndDoNotMatch() {
        // Too short
        assertNull(PalestinianPhoneNumberUtils.normalizePalestinianPhoneNumber("059409109"))
        assertFalse(PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers("059409109", "0594091090"))

        assertNull(PalestinianPhoneNumberUtils.normalizePalestinianPhoneNumber("59409109"))
        assertFalse(PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers("59409109", "0594091090"))

        // Too long
        assertNull(PalestinianPhoneNumberUtils.normalizePalestinianPhoneNumber("05940910900"))
        assertFalse(PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers("05940910900", "0594091090"))

        assertNull(PalestinianPhoneNumberUtils.normalizePalestinianPhoneNumber("5940910900"))
        assertFalse(PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers("5940910900", "0594091090"))

        // Too short international
        assertNull(PalestinianPhoneNumberUtils.normalizePalestinianPhoneNumber("+97059409109"))
        assertFalse(PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers("+97059409109", "0594091090"))
    }

    @Test
    fun testEmptyAndNullInputs() {
        assertNull(PalestinianPhoneNumberUtils.normalizePalestinianPhoneNumber(""))
        assertNull(PalestinianPhoneNumberUtils.normalizePalestinianPhoneNumber("   "))
        assertNull(PalestinianPhoneNumberUtils.normalizePalestinianPhoneNumber(null))

        assertFalse(PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers("", "0594091090"))
        assertFalse(PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers(null, "0594091090"))
        assertFalse(PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers("0594091090", null))
        assertFalse(PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers(null, null))
    }

    @Test
    fun testUnrelatedInternationalNumbers() {
        // Equal US numbers (+ vs 00)
        assertTrue(PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers("+14155552671", "0014155552671"))

        // Different US numbers
        assertFalse(PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers("+14155552671", "+14155552672"))

        // US vs Palestinian
        assertFalse(PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers("+14155552671", "0594091090"))
        assertFalse(PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers("+14155552671", "0568987654"))

        // Palestinian normalization must return null for non-Palestinian numbers
        assertNull(PalestinianPhoneNumberUtils.normalizePalestinianPhoneNumber("+14155552671"))
        assertNull(PalestinianPhoneNumberUtils.normalizePalestinianPhoneNumber("+447911123456"))
    }

    @Test
    fun testDifferentPalestinianNumbersDoNotMatch() {
        assertFalse(PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers("0594091090", "0594091091"))
        assertFalse(PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers("+970594091090", "+970594091091"))
        assertFalse(PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers("0594091090", "0564091090"))
        assertFalse(PalestinianPhoneNumberUtils.areEquivalentPhoneNumbers("594091090", "564091090"))
    }

    @Test
    fun testIncompletePrefixFalsePositivePrevention() {
        // Incomplete prefixes must NOT match unrelated international numbers that contain '0' or '970'
        val unrelatedWithZero = "+14155552670"
        val unrelatedWith970 = "+14159702671"
        val unrelatedUK = "+447911123456"

        assertFalse(PalestinianPhoneNumberUtils.matchesQuery(unrelatedWithZero, "+970"))
        assertFalse(PalestinianPhoneNumberUtils.matchesQuery(unrelatedWithZero, "+972"))
        assertFalse(PalestinianPhoneNumberUtils.matchesQuery(unrelatedWithZero, "00970"))
        assertFalse(PalestinianPhoneNumberUtils.matchesQuery(unrelatedWithZero, "00972"))
        assertFalse(PalestinianPhoneNumberUtils.matchesQuery(unrelatedWith970, "+970"))
        assertFalse(PalestinianPhoneNumberUtils.matchesQuery(unrelatedUK, "+970"))

        // normalizeSearchQuery must NOT return "0" for incomplete prefixes
        assertEquals("", PalestinianPhoneNumberUtils.normalizeSearchQuery("+970"))
        assertEquals("", PalestinianPhoneNumberUtils.normalizeSearchQuery("+972"))
        assertEquals("", PalestinianPhoneNumberUtils.normalizeSearchQuery("00970"))
        assertEquals("", PalestinianPhoneNumberUtils.normalizeSearchQuery("00972"))
        assertEquals("", PalestinianPhoneNumberUtils.normalizeSearchQuery("970"))
        assertEquals("", PalestinianPhoneNumberUtils.normalizeSearchQuery("972"))

        // Incomplete prefix DOES match valid Palestinian numbers
        assertTrue(PalestinianPhoneNumberUtils.matchesQuery("0594091090", "+970"))
        assertTrue(PalestinianPhoneNumberUtils.matchesQuery("+970594091090", "+970"))
        assertTrue(PalestinianPhoneNumberUtils.matchesQuery("0568987654", "+970"))
        assertTrue(PalestinianPhoneNumberUtils.matchesQuery("0594091090", "+972"))
        assertTrue(PalestinianPhoneNumberUtils.matchesQuery("0594091090", "00970"))
    }

    @Test
    fun testIncrementalPrefixTyping() {
        val palNumber = "0594091090"

        // Incremental search while typing Palestinian number with country code
        assertEquals("05", PalestinianPhoneNumberUtils.normalizeSearchQuery("+9705"))
        assertEquals("059", PalestinianPhoneNumberUtils.normalizeSearchQuery("+97059"))
        assertEquals("0594", PalestinianPhoneNumberUtils.normalizeSearchQuery("+970594"))
        assertEquals("059409", PalestinianPhoneNumberUtils.normalizeSearchQuery("+97059409"))

        assertTrue(PalestinianPhoneNumberUtils.matchesQuery(palNumber, "+9705"))
        assertTrue(PalestinianPhoneNumberUtils.matchesQuery(palNumber, "+97059"))
        assertTrue(PalestinianPhoneNumberUtils.matchesQuery(palNumber, "+970594"))
        assertTrue(PalestinianPhoneNumberUtils.matchesQuery(palNumber, "+970 59"))

        // Typing 9-digit format without leading 0 incrementally
        assertEquals("059", PalestinianPhoneNumberUtils.normalizeSearchQuery("59"))
        assertEquals("0594", PalestinianPhoneNumberUtils.normalizeSearchQuery("594"))
        assertEquals("059409", PalestinianPhoneNumberUtils.normalizeSearchQuery("59409"))
        assertTrue(PalestinianPhoneNumberUtils.matchesQuery(palNumber, "59409"))
        assertTrue(PalestinianPhoneNumberUtils.matchesQuery(palNumber, "594091090"))
    }

    @Test
    fun testContactSearchAndCallLogMatchingBehavior() {
        val phoneInContact = "+970594091090"
        val userTypedQuery = "0594091090"

        assertTrue(PalestinianPhoneNumberUtils.matchesQuery(phoneInContact, userTypedQuery))
        assertTrue(PalestinianPhoneNumberUtils.matchesQuery("00972594091090", "05940"))
        assertTrue(PalestinianPhoneNumberUtils.matchesQuery("0594091090", "+970 59"))

        // Unrelated number search query
        assertFalse(PalestinianPhoneNumberUtils.matchesQuery("+14155552671", "0594091090"))

        // Eastern Arabic numerals in search query
        assertTrue(PalestinianPhoneNumberUtils.matchesQuery("+970594091090", "٠٥٩٤٠٩١٠٩٠"))
        assertTrue(PalestinianPhoneNumberUtils.matchesQuery("0594091090", "٠٥٩"))

        // Persian numerals in search query
        assertTrue(PalestinianPhoneNumberUtils.matchesQuery("+970594091090", "۰۵۹۴۰۹۱۰۹۰"))
        assertTrue(PalestinianPhoneNumberUtils.matchesQuery("0594091090", "۰۵۹"))

        // Partial suffix search
        assertTrue(PalestinianPhoneNumberUtils.matchesQuery("0594091090", "1090"))
    }

    @Test
    fun testArabicTextNormalization() {
        val normalized1 = PalestinianPhoneNumberUtils.normalizeArabicText("أَحْمَد")
        val normalized2 = PalestinianPhoneNumberUtils.normalizeArabicText("احمد")
        assertEquals(normalized1, normalized2)

        val alifMaqsoora = PalestinianPhoneNumberUtils.normalizeArabicText("مستشفى")
        assertTrue(alifMaqsoora.endsWith("ي"))

        val taaMarbuta = PalestinianPhoneNumberUtils.normalizeArabicText("فاطمة")
        assertTrue(taaMarbuta.endsWith("ه"))
    }
}
