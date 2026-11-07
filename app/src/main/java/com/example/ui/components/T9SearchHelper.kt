package com.example.ui.components

import com.example.utils.PalestinianPhoneNumberUtils

object T9SearchHelper {

    private val CHAR_TO_DIGIT: Map<Char, Char> = buildMap {
        for (c in 'A'..'C') put(c, '2')
        for (c in 'a'..'c') put(c, '2')

        for (c in 'D'..'F') put(c, '3')
        for (c in 'd'..'f') put(c, '3')

        for (c in 'G'..'I') put(c, '4')
        for (c in 'g'..'i') put(c, '4')

        for (c in 'J'..'L') put(c, '5')
        for (c in 'j'..'l') put(c, '5')

        for (c in 'M'..'O') put(c, '6')
        for (c in 'm'..'o') put(c, '6')

        for (c in 'P'..'S') put(c, '7')
        for (c in 'p'..'s') put(c, '7')

        for (c in 'T'..'V') put(c, '8')
        for (c in 't'..'v') put(c, '8')

        for (c in 'W'..'Z') put(c, '9')
        for (c in 'w'..'z') put(c, '9')

        // Universal Arabic Phone Keypad Mapping
        // 2: أ إ آ ا ء ب ت ث (without space character)
        "أإآاءبتث".forEach { put(it, '2') }
        // 3: ج ح خ
        "جحخ".forEach { put(it, '3') }
        // 4: د ذ ر ز
        "دذرز".forEach { put(it, '4') }
        // 5: س ش ص ض
        "سشصض".forEach { put(it, '5') }
        // 6: ط ظ ع غ
        "طظعغ".forEach { put(it, '6') }
        // 7: ف ق ك ل
        "فقكل".forEach { put(it, '7') }
        // 8: م ن ه ة
        "منهة".forEach { put(it, '8') }
        // 9: و ي ى ئ ؤ
        "ويىئؤ".forEach { put(it, '9') }
    }

    private const val MAX_CACHE_SIZE = 10000
    private val t9Cache = object : java.util.LinkedHashMap<String, String>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > MAX_CACHE_SIZE
        }
    }

    fun textToT9(text: String): String {
        synchronized(t9Cache) {
            val cached = t9Cache[text]
            if (cached != null) return cached
        }

        val norm = PalestinianPhoneNumberUtils.normalizeArabicText(text)
        val sb = java.lang.StringBuilder(norm.length)
        for (ch in norm) {
            val digit = CHAR_TO_DIGIT[ch]
            if (digit != null) {
                sb.append(digit)
            } else if (ch in '0'..'9') {
                sb.append(ch)
            } else {
                sb.append(' ')
            }
        }
        val result = sb.toString()
        synchronized(t9Cache) {
            t9Cache[text] = result
        }
        return result
    }

    /**
     * Checks if typed numeric sequence matches contact name (via T9) or contact phone numbers
     */
    fun matches(queryDigits: String, displayName: String, phoneNumbers: List<String>): Boolean {
        if (queryDigits.isBlank()) return true

        val asciiQuery = PalestinianPhoneNumberUtils.convertArabicDigitsToAscii(queryDigits)
        val cleanQuery = asciiQuery.filter { it in '0'..'9' }

        if (cleanQuery.isEmpty()) {
            return phoneNumbers.any { it.contains(queryDigits) }
        }

        // 1. Check if phone number contains query digits or Palestinian equivalent
        for (number in phoneNumbers) {
            if (PalestinianPhoneNumberUtils.matchesQuery(number, asciiQuery)) {
                return true
            }
        }

        // 2. Check T9 match on name (full or any word starting with the sequence)
        val t9String = textToT9(displayName)
        val words = t9String.split(' ').filter { it.isNotBlank() }
        for (word in words) {
            if (word.startsWith(cleanQuery)) {
                return true
            }
        }

        return t9String.replace(" ", "").contains(cleanQuery)
    }
}
