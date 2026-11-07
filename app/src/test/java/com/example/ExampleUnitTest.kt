package com.example

import com.example.ui.components.T9SearchHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testT9TextToDigits() {
        val converted = T9SearchHelper.textToT9("John")
        assertEquals("5646", converted)

        val bob = T9SearchHelper.textToT9("Bob")
        assertEquals("262", bob)
    }

    @Test
    fun testT9MatchesContactName() {
        // "John Doe" -> "5646 363"
        val displayName = "John Doe"
        val numbers = listOf("+1 555-0199")

        // Matches prefix of first name "564" -> "JOH"
        assertTrue(T9SearchHelper.matches("564", displayName, numbers))

        // Matches prefix of last name "363" -> "DOE"
        assertTrue(T9SearchHelper.matches("363", displayName, numbers))

        // Matches digits in phone number "0199"
        assertTrue(T9SearchHelper.matches("0199", displayName, numbers))

        // Does not match random digits
        assertFalse(T9SearchHelper.matches("99999", displayName, numbers))
    }

    @Test
    fun testT9BlankQueryMatchesAll() {
        assertTrue(T9SearchHelper.matches("", "Alice", listOf("123456")))
    }
}
