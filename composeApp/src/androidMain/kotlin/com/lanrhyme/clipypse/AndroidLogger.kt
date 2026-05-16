package com.lanrhyme.clipypse

import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class AndroidLogger : LoggerImpl {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")

    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, message, throwable)
            LogLevel.INFO -> Log.i(tag, message, throwable)
            LogLevel.WARN -> Log.w(tag, message, throwable)
            LogLevel.ERROR -> Log.e(tag, message, throwable)
        }
    }

    override fun getLogFilePath(): String? {
        val context = AndroidContext.context ?: return null
        val logDir = File(context.filesDir, "logs")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        return File(logDir, "clipypse-${SimpleDateFormat("yyyy-MM-dd").format(Date())}.log").absolutePath
    }
}
