package io.github.nerushkolya.locator.core.common

enum class LogLevel(val wire: String) {
    DEBUG("debug"),
    INFO("info"),
    WARNING("warning"),
    ERROR("error"),
}

/** Receives one finished, scrubbed JSON line. The Android sink writes it to logcat. */
fun interface LogSink {
    fun write(level: LogLevel, line: String)
}
