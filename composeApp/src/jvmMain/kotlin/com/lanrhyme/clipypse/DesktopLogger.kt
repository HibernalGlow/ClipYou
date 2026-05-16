package com.lanrhyme.clipypse

import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*

class DesktopLogger : LoggerImpl {
    private val logFile: File? by lazy {
        val userHome = System.getProperty("user.home")
        val logDir = File(userHome, ".clipypse/logs")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        File(logDir, "clipypse-${SimpleDateFormat("yyyy-MM-dd").format(Date())}.log")
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")

    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        val timestamp = dateFormat.format(Date())
        val levelStr = level.name.padEnd(5)
        val logMessage = "[$timestamp] [$levelStr] [$tag] $message"

        println(logMessage)

        throwable?.let {
            println(it.stackTraceToString())
        }

        try {
            logFile?.let { file ->
                PrintWriter(FileWriter(file, true)).use { writer ->
                    writer.println(logMessage)
                    throwable?.let {
                        writer.println(it.stackTraceToString())
                    }
                }
            }
        } catch (e: Exception) {
            println("Failed to write to log file: ${e.message}")
        }
    }

    override fun getLogFilePath(): String? = logFile?.absolutePath
}
