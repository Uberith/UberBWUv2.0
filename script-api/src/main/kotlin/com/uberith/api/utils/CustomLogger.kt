package com.uberith.api.utils
import java.io.File
import java.io.FileWriter
import java.io.IOException

object CustomLogger {
    private var console: Any? = null
    private var logFile: File? = null
    private var scriptName: String = "Script"

    fun initialize(console: Any?, scriptName: String) {
        this.console = console
        this.scriptName = scriptName
        val dir = File(System.getProperty("user.home"), ".BotWithUs/logs")
        if (!dir.exists()) dir.mkdirs()
        logFile = File(dir, "$scriptName.log")
    }

    private fun writeLine(level: String, message: String) {
        try {
            val line = "[$level][$scriptName] $message\n"
            // Try to invoke ScriptConsole.log if available
            try { console?.javaClass?.getMethod("log", String::class.java)?.invoke(console, line.trim()) } catch (_: Throwable) {}
            val f = logFile ?: return
            FileWriter(f, true).use { it.write(line) }
        } catch (_: IOException) { }
    }

    fun info(message: String, src: String = scriptName) = writeLine("INFO", message)
    fun warning(message: String, src: String = scriptName) = writeLine("WARN", message)
    fun error(message: String, src: String = scriptName) = writeLine("ERROR", message)
    fun debug(message: String, src: String = scriptName) = writeLine("DEBUG", message)
}
