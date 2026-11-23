package io.github.nerushkolya.locator.core.common

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The nine vectors of the log-records contract, section 4, plus the record format of section 1. */
class LoggerTest {
    private val lines = mutableListOf<Pair<LogLevel, String>>()
    private val logger = Logger(
        sink = LogSink { level, line -> lines += level to line },
        clock = { Instant.parse("2026-11-23T10:15:30.123Z") },
    )

    private fun output() = lines.joinToString("\n") { it.second }

    private fun assertAbsent(vararg forbidden: String) {
        val out = output()
        forbidden.forEach { assertFalse(out.contains(it), "found '$it' in: $out") }
    }

    @Test
    fun `vector 1 - coordinate fields are redacted by key`() {
        logger.info("moved", fields = mapOf("lat" to 50.4501, "lon" to 30.5234))
        assertAbsent("50.4501", "30.5234")
        assertTrue(output().contains("\"lat\":\"[redacted]\""))
    }

    @Test
    fun `vector 2 - coordinates in the message are redacted by pattern`() {
        logger.info("position 50.4501, 30.5234 accepted")
        assertAbsent("50.4501", "30.5234")
        assertTrue(output().contains("[redacted:coords]"))
    }

    @Test
    fun `vector 3 - place name is redacted`() {
        logger.info("saved", fields = mapOf("placeName" to "Home"))
        assertAbsent("Home")
    }

    @Test
    fun `vector 4 - token field is redacted`() {
        val token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.c2lnbmF0dXJl"
        logger.info("signed in", fields = mapOf("accessToken" to token))
        assertAbsent(token)
    }

    @Test
    fun `vector 5 - bearer value in the message is redacted`() {
        logger.error("Authorization: Bearer abc.def.ghi failed")
        assertAbsent("abc.def.ghi")
    }

    @Test
    fun `vector 6 - e-mail in field and message is redacted`() {
        logger.info("sent to person@example.org", fields = mapOf("email" to "person@example.org"))
        assertAbsent("person@example.org")
    }

    @Test
    fun `vector 7 - invitation code in field and message is redacted`() {
        logger.warning("invite code K7P2M9QX expired", fields = mapOf("invitationCode" to "K7P2M9QX"))
        assertAbsent("K7P2M9QX")
    }

    @Test
    fun `vector 8 - nested location fields are redacted`() {
        logger.info(
            "device",
            fields = mapOf("device" to mapOf("location" to mapOf("lat" to 50.45, "lon" to 30.52))),
        )
        assertAbsent("50.45", "30.52")
    }

    @Test
    fun `vector 9 - ids and status stay`() {
        logger.info("done", fields = mapOf("userId" to "0192f3e4-aaaa", "status" to 200))
        assertTrue(output().contains("0192f3e4-aaaa"))
        assertTrue(output().contains("\"status\":200"))
    }

    @Test
    fun `ordinary words after invitation are kept`() {
        logger.info("invitation accepted")
        assertTrue(output().contains("invitation accepted"))
    }

    @Test
    fun `coordinates outside the valid range are kept`() {
        logger.info("build 999.1234, 888.5678")
        assertTrue(output().contains("999.1234, 888.5678"))
    }

    @Test
    fun `record has the contract fields`() {
        logger.info("started", event = "app.start", fields = mapOf("count" to 3))
        assertEquals(
            """{"ts":"2026-11-23T10:15:30.123Z","level":"info","service":"app","msg":"started","event":"app.start","count":3}""",
            lines.single().second,
        )
    }

    @Test
    fun `strings are escaped and keys with a dot use the last segment`() {
        logger.info("a \"quoted\"\nline", fields = mapOf("device.token" to "secret-value"))
        assertAbsent("secret-value")
        assertTrue(output().contains("a \\\"quoted\\\"\\nline"))
    }

    @Test
    fun `levels are passed to the sink`() {
        logger.debug("d")
        logger.error("e")
        assertEquals(listOf(LogLevel.DEBUG, LogLevel.ERROR), lines.map { it.first })
    }
}
