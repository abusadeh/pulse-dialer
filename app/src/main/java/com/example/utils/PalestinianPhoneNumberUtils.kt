package com.example.utils

object PalestinianPhoneNumberUtils {

    private val HARAKAT_REGEX = Regex("[\\u064B-\\u065F\\u0670]")
    private val ALIF_REGEX = Regex("[\\u0622\\u0623\\u0625\\u0671]")

    /**
     * Converts Eastern Arabic (٠-٩) and Persian (۰-۹) numerals to standard ASCII (0-9).
     */
    fun convertArabicDigitsToAscii(raw: String?): String {
        if (raw == null) return ""
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            when (ch) {
                in '\u0660'..'\u0669' -> sb.append((ch - '\u0660' + '0'.code).toChar())
                in '\u06F0'..'\u06F9' -> sb.append((ch - '\u06F0' + '0'.code).toChar())
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * Normalizes Arabic text for flexible search (unifying Alif variants, Taa Marbuta, Yaa, and removing Tashkeel).
     */
    fun normalizeArabicText(text: String?): String {
        if (text == null) return ""
        return text
            .replace(HARAKAT_REGEX, "") // Remove harakat/tashkeel
            .replace("ـ", "") // Remove tatweel
            .replace(ALIF_REGEX, "ا") // Normalize Alif variants to bare Alif
            .replace('ة', 'ه') // Normalize Taa Marbuta
            .replace('ى', 'ي') // Normalize Alif Maqsoora
            .lowercase()
    }

    /**
     * Cleans harmless formatting characters (spaces, hyphens, parentheses, dots, slashes).
     * Converts Eastern Arabic numerals to standard digits and retains leading '+' if present.
     */
    fun cleanPhoneNumber(raw: String?): String {
        if (raw == null) return ""
        val ascii = convertArabicDigitsToAscii(raw)
        val trimmed = ascii.trim()
        if (trimmed.isEmpty()) return ""
        val hasLeadingPlus = trimmed.startsWith("+")
        val sb = StringBuilder(trimmed.length)
        if (hasLeadingPlus) sb.append('+')
        for (i in 0 until trimmed.length) {
            val ch = trimmed[i]
            if (ch in '0'..'9') sb.append(ch)
        }
        return sb.toString()
    }

    /**
     * Normalizes a Palestinian mobile phone number to its canonical 10-digit local format:
     * e.g., "0594091090" or "056XXXXXXX".
     *
     * Supports:
     * - 10-digit local: 059XXXXXXX / 056XXXXXXX
     * - 9-digit local: 59XXXXXXX / 56XXXXXXX
     * - International: +970 / +972 / 00970 / 00972 / 970 / 972 + (59/56)XXXXXXX
     * - Eastern Arabic (٠-٩) and Persian (۰-۹) numerals
     *
     * Returns null if the given number is invalid, blank, or not a recognized Palestinian mobile number.
     */
    fun normalizePalestinianPhoneNumber(phoneNumber: String?): String? {
        if (phoneNumber.isNullOrBlank()) return null

        val cleaned = cleanPhoneNumber(phoneNumber)
        if (cleaned.isBlank()) return null

        val digits = cleaned.filter { it.isDigit() }

        // Recognized international country code prefixes for Palestine/region: 970 or 972.
        // Recognized Palestinian mobile prefixes: 59 (Jawwal) and 56 (Ooredoo).
        // Total digits after country code: 9 digits starting with 59 or 56.
        // Canonical local representation: 0 + 59XXXXXXX / 0 + 56XXXXXXX (10 digits starting with 059 or 056).

        when {
            // Case A: Starts with +970 or +972 (+ followed by 12 digits = 13 chars total)
            (cleaned.startsWith("+970") || cleaned.startsWith("+972")) -> {
                if (cleaned.length == 13) {
                    val mobileDigits = cleaned.substring(4) // 9 digits
                    if (mobileDigits.length == 9 && (mobileDigits.startsWith("59") || mobileDigits.startsWith("56"))) {
                        return "0$mobileDigits"
                    }
                }
            }

            // Case B: Starts with 00970 or 00972 (14 digits total)
            (digits.startsWith("00970") || digits.startsWith("00972")) -> {
                if (digits.length == 14) {
                    val mobileDigits = digits.substring(5) // 9 digits
                    if (mobileDigits.length == 9 && (mobileDigits.startsWith("59") || mobileDigits.startsWith("56"))) {
                        return "0$mobileDigits"
                    }
                }
            }

            // Case C: Starts with 970 or 972 without + or 00 (12 digits total)
            (digits.startsWith("970") || digits.startsWith("972")) -> {
                if (digits.length == 12) {
                    val mobileDigits = digits.substring(3) // 9 digits
                    if (mobileDigits.length == 9 && (mobileDigits.startsWith("59") || mobileDigits.startsWith("56"))) {
                        return "0$mobileDigits"
                    }
                }
            }

            // Case D: Local format starting with 059 or 056 (10 digits total)
            (digits.startsWith("059") || digits.startsWith("056")) -> {
                if (digits.length == 10) {
                    return digits
                }
            }

            // Case E: 9-digit format starting with 59 or 56 without leading zero (9 digits total)
            (digits.startsWith("59") || digits.startsWith("56")) -> {
                if (digits.length == 9) {
                    return "0$digits"
                }
            }
        }

        return null
    }

    /**
     * Checks if a phone number is a recognized Palestinian mobile number.
     */
    fun isPalestinianMobile(phoneNumber: String?): Boolean {
        return normalizePalestinianPhoneNumber(phoneNumber) != null
    }

    /**
     * Normalizes any phone number.
     * Returns canonical Palestinian local format (059XXXXXXX / 056XXXXXXX) for Palestinian mobile numbers,
     * or standard international format (+<digits>) / cleaned digits for other valid numbers.
     * Returns null if invalid or blank.
     */
    fun normalizePhoneNumber(phoneNumber: String?): String? {
        if (phoneNumber.isNullOrBlank()) return null

        val palNorm = normalizePalestinianPhoneNumber(phoneNumber)
        if (palNorm != null) {
            return palNorm
        }

        val cleaned = cleanPhoneNumber(phoneNumber)
        if (cleaned.isBlank()) return null

        val digits = cleaned.filter { it.isDigit() }
        if (digits.length < 3) return null

        return when {
            cleaned.startsWith("+") -> "+$digits"
            cleaned.startsWith("00") -> "+${digits.substring(2)}"
            else -> digits
        }
    }

    /**
     * Returns true if two phone numbers are equivalent.
     * Handles Palestinian mobile formats (+970, 00970, 970, +972, 00972, 972, 059, 056, 59, 56),
     * formatting differences (spaces, hyphens, parentheses), international prefixes (+ vs 00),
     * and strict non-Palestinian equivalence without false positives.
     */
    fun areEquivalentPhoneNumbers(first: String?, second: String?): Boolean {
        if (first.isNullOrBlank() || second.isNullOrBlank()) return false

        val pal1 = normalizePalestinianPhoneNumber(first)
        val pal2 = normalizePalestinianPhoneNumber(second)

        if (pal1 != null && pal2 != null) {
            return pal1 == pal2
        }

        if (pal1 != null || pal2 != null) {
            // One is a valid Palestinian mobile number, the other is not
            return false
        }

        // Neither is a Palestinian mobile number
        val norm1 = normalizePhoneNumber(first)
        val norm2 = normalizePhoneNumber(second)

        if (norm1 == null || norm2 == null) return false

        return norm1 == norm2
    }

    /**
     * Converts partial search query prefixes (+970, 00970, 970, +972, 00972, 972)
     * to local format (05...) for intelligent partial matching.
     *
     * Incomplete country prefixes without subscriber digits return empty string to prevent
     * broad matching on digit "0".
     */
    fun normalizeSearchQuery(query: String?): String {
        if (query.isNullOrBlank()) return ""
        val clean = cleanPhoneNumber(query)
        val digits = clean.filter { it.isDigit() }
        when {
            clean.startsWith("+970") || clean.startsWith("+972") -> {
                val rest = clean.substring(4)
                if (rest.startsWith("5")) {
                    return "0$rest"
                }
                // When rest is empty or does not start with mobile digit 5, do NOT return "0"
                return if (rest.isEmpty()) "" else rest
            }
            digits.startsWith("00970") || digits.startsWith("00972") -> {
                val rest = digits.substring(5)
                if (rest.startsWith("5")) {
                    return "0$rest"
                }
                return if (rest.isEmpty()) "" else rest
            }
            digits.startsWith("970") || digits.startsWith("972") -> {
                val rest = digits.substring(3)
                if (rest.startsWith("5")) {
                    return "0$rest"
                }
                return if (rest.isEmpty()) "" else rest
            }
            (digits.startsWith("59") || digits.startsWith("56")) && digits.length in 2..9 -> {
                return "0$digits"
            }
        }
        return digits
    }

    /**
     * Checks if a search query matches a phone number intelligently.
     * Handles local/international variants, partial prefix typing, and formatting differences.
     * Avoids matching unrelated phone numbers when incomplete prefixes are typed.
     */
    fun matchesQuery(phoneNumber: String?, query: String?): Boolean {
        if (query.isNullOrBlank()) return true
        if (phoneNumber.isNullOrBlank()) return false

        val asciiPhone = convertArabicDigitsToAscii(phoneNumber)
        val asciiQuery = convertArabicDigitsToAscii(query)

        val cleanPhone = cleanPhoneNumber(asciiPhone)
        val cleanQuery = cleanPhoneNumber(asciiQuery)

        val digitsPhone = cleanPhone.filter { it in '0'..'9' }
        val digitsQuery = cleanQuery.filter { it in '0'..'9' }

        // 1. Direct contains check (raw or clean formatted)
        if (asciiPhone.contains(asciiQuery, ignoreCase = true) ||
            (cleanQuery.isNotEmpty() && cleanPhone.contains(cleanQuery))) {
            return true
        }

        // Digits containment: if query starts with '+', it is an international query
        // and must match at the beginning of the number to avoid matching unrelated numbers containing the digits.
        if (digitsQuery.isNotEmpty()) {
            if (cleanQuery.startsWith("+")) {
                if (cleanPhone.startsWith(cleanQuery) || digitsPhone.startsWith(digitsQuery)) {
                    return true
                }
            } else {
                if (digitsPhone.contains(digitsQuery)) {
                    return true
                }
            }
        }

        // 2. Full equivalence check
        if (areEquivalentPhoneNumbers(asciiPhone, asciiQuery)) {
            return true
        }

        // 3. Palestinian normalized matching
        val normPhone = normalizePalestinianPhoneNumber(asciiPhone)

        // Check if query is an incomplete Palestinian international prefix:
        val isIncompletePalPrefix = when {
            cleanQuery == "+970" || cleanQuery == "+972" || cleanQuery == "+97" -> true
            digitsQuery == "00970" || digitsQuery == "00972" || digitsQuery == "0097" -> true
            digitsQuery == "970" || digitsQuery == "972" -> true
            else -> false
        }

        if (isIncompletePalPrefix) {
            // Incomplete Palestinian prefix matches Palestinian mobile numbers, not unrelated numbers
            if (normPhone != null) {
                return true
            }
        }

        val normQuery = normalizePalestinianPhoneNumber(asciiQuery)
        if (normPhone != null && normQuery != null) {
            if (normPhone == normQuery || normPhone.contains(normQuery)) {
                return true
            }
        }

        // 4. Normalized partial query matching
        val queryLocal = normalizeSearchQuery(asciiQuery)
        if (queryLocal.isNotEmpty()) {
            val phoneLocal = normPhone ?: digitsPhone
            if (phoneLocal.contains(queryLocal)) {
                return true
            }
        }

        return false
    }

    /**
     * Formats a phone number for display.
     * For Palestinian numbers, formats as "05X-XXX-XXXX".
     * For others, returns cleaned digits or normalized format.
     */
    fun formatPalestinianNumber(phoneNumber: String?): String {
        val norm = normalizePalestinianPhoneNumber(phoneNumber) ?: return phoneNumber ?: ""
        // Format 0594091090 as 059-409-1090
        return if (norm.length == 10) {
            "${norm.substring(0, 3)}-${norm.substring(3, 6)}-${norm.substring(6)}"
        } else {
            norm
        }
    }

    fun formatNumber(phoneNumber: String?): String {
        if (isPalestinianMobile(phoneNumber)) {
            return formatPalestinianNumber(phoneNumber)
        }
        return normalizePhoneNumber(phoneNumber) ?: phoneNumber ?: ""
    }
}
