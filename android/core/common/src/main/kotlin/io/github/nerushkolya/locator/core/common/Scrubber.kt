package io.github.nerushkolya.locator.core.common

import kotlin.math.abs

/**
 * Scrubbing rules of the log-records contract, section 2 (constitution principle V).
 * Layer 1 replaces the value of a sensitive key with "[redacted]"; layer 2 replaces sensitive
 * patterns inside strings with "[redacted:<kind>]". The core service and the delivery service
 * apply the same rules.
 */
object Scrubber {
    const val REDACTED = "[redacted]"
    private const val MAX_DEPTH = 8

    // Lower-cased, without "_" and "-" (see normalizeKey).
    private val sensitiveKeys = setOf(
        "lat", "lon", "lng", "latitude", "longitude", "coordinates", "location", "position", "accuracym",
        "placename", "place", "address",
        "token", "accesstoken", "refreshtoken", "idtoken", "pushtoken", "ticket", "authorization",
        "password", "secret", "apikey", "nonce",
        "email", "mail",
        "code", "invitationcode", "invitecode",
    )

    private val jwt = Regex("""eyJ[\w-]+\.[\w-]+\.[\w-]+""")
    private val bearer = Regex("""\bBearer\s+\S+""", RegexOption.IGNORE_CASE)
    private val email = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}""")

    // Only the keyword ignores case; the invitation alphabet (8 characters without 0, 1, I, L, O, W)
    // is upper case, so ordinary words such as "invitation accepted" stay.
    private val invite = Regex("""\b((?i:code|invite|invitation))([:=\s]\s*)[ABCDEFGHJKMNPQRSTUVXYZ2-9]{8}\b""")
    private val coords = Regex("""(^|[^\d.])(-?\d{1,3}\.\d{3,})\s*,\s*(-?\d{1,3}\.\d{3,})""")

    fun isSensitiveKey(key: String): Boolean {
        val last = key.substringAfterLast('.')
        return normalizeKey(last) in sensitiveKeys
    }

    private fun normalizeKey(key: String) = key.replace("_", "").replace("-", "").lowercase()

    fun scrubString(text: String): String {
        var result = jwt.replace(text, "[redacted:token]")
        result = bearer.replace(result, "[redacted:token]")
        result = email.replace(result, "[redacted:email]")
        result = invite.replace(result) { "${it.groupValues[1]}${it.groupValues[2]}[redacted:invite]" }
        return scrubCoords(result)
    }

    private fun scrubCoords(text: String): String = coords.replace(text) { match ->
        val (prefix, first, second) = match.destructured
        if (withinRange(first) && withinRange(second)) "$prefix[redacted:coords]" else match.value
    }

    private fun withinRange(number: String): Boolean = number.toDoubleOrNull()?.let { abs(it) <= 180 } ?: false

    /** Scrubs a value of a log field: strings, numbers, booleans, null, maps and lists. */
    fun scrubValue(value: Any?, depth: Int = 0): Any? {
        if (depth > MAX_DEPTH) return REDACTED
        return when (value) {
            null, is Boolean, is Number -> value
            is String -> scrubString(value)
            is Map<*, *> -> value.entries.associate { (k, v) ->
                val key = k.toString()
                key to if (isSensitiveKey(key)) REDACTED else scrubValue(v, depth + 1)
            }
            is Iterable<*> -> value.map { scrubValue(it, depth + 1) }
            // Unknown types and exceptions never reach the output with their own text.
            else -> value::class.simpleName.orEmpty()
        }
    }
}
