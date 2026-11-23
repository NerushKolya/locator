package io.github.nerushkolya.locator.core.common

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Writes one JSON object per record to a [LogSink] after scrubbing (log-records contract, sections 1 and 2).
 * Message and field values pass through [Scrubber]; field values are scalars, maps or lists.
 */
class Logger(
    private val sink: LogSink,
    private val clock: () -> Instant = Instant::now,
    private val service: String = "app",
) {
    fun debug(msg: String, event: String? = null, fields: Map<String, Any?> = emptyMap()) =
        log(LogLevel.DEBUG, msg, event, fields)

    fun info(msg: String, event: String? = null, fields: Map<String, Any?> = emptyMap()) =
        log(LogLevel.INFO, msg, event, fields)

    fun warning(msg: String, event: String? = null, fields: Map<String, Any?> = emptyMap()) =
        log(LogLevel.WARNING, msg, event, fields)

    fun error(msg: String, event: String? = null, fields: Map<String, Any?> = emptyMap()) =
        log(LogLevel.ERROR, msg, event, fields)

    fun log(level: LogLevel, msg: String, event: String? = null, fields: Map<String, Any?> = emptyMap()) {
        val record = LinkedHashMap<String, Any?>()
        record["ts"] = TIMESTAMP.format(clock())
        record["level"] = level.wire
        record["service"] = service
        record["msg"] = Scrubber.scrubString(msg)
        if (event != null) record["event"] = Scrubber.scrubString(event)
        for ((key, value) in fields) {
            if (key in RESERVED) continue
            record[key] = if (Scrubber.isSensitiveKey(key)) Scrubber.REDACTED else Scrubber.scrubValue(value)
        }
        sink.write(level, Json.write(record))
    }

    private companion object {
        val TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC)
        val RESERVED = setOf("ts", "level", "service", "msg", "event")
    }
}
