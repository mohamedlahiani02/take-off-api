package tn.takeoff.common

/**
 * Normalize a Tunisian phone to canonical +216XXXXXXXX form.
 * Accepts inputs with spaces, a leading 00216/216, or a bare 8 digits.
 * Non-conforming input is returned trimmed (it simply won't match any stored phone).
 */
object PhoneUtil {
    fun normalize(input: String): String {
        var digits = input.trim().replace(Regex("[\\s-]"), "")
        digits = digits.removePrefix("+")
        if (digits.startsWith("00216")) digits = digits.removePrefix("00216")
        else if (digits.startsWith("216")) digits = digits.removePrefix("216")
        return if (digits.length == 8 && digits.all { it.isDigit() }) "+216$digits" else input.trim()
    }
}
