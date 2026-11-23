package io.github.nerushkolya.locator

import android.util.Log
import io.github.nerushkolya.locator.core.common.LogLevel
import io.github.nerushkolya.locator.core.common.LogSink

/** Writes the finished JSON record to logcat under the tag `Locator`; release builds drop `debug` records. */
class AndroidLogSink(private val isDebugBuild: Boolean = BuildConfig.DEBUG) : LogSink {
    override fun write(level: LogLevel, line: String) {
        when (level) {
            LogLevel.DEBUG -> if (isDebugBuild) Log.d(TAG, line)
            LogLevel.INFO -> Log.i(TAG, line)
            LogLevel.WARNING -> Log.w(TAG, line)
            LogLevel.ERROR -> Log.e(TAG, line)
        }
    }

    private companion object {
        const val TAG = "Locator"
    }
}
