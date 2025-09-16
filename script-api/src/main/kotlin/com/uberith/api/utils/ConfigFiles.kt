package com.uberith.api.utils

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileWriter
import java.io.IOException

/**
 * Utilities for writing settings files under ~/.botwithus/configs
 */
object ConfigFiles {
    private val log = LoggerFactory.getLogger(ConfigFiles::class.java)
    private fun ensureWritable(dir: File): Boolean {
        return try {
            if (!dir.exists()) {
                if (!dir.mkdirs() && !dir.exists()) return false
            }
            val probe = File(dir, ".write_probe")
            FileWriter(probe, false).use { it.write("") }
            probe.delete()
            true
        } catch (_: Exception) { false }
    }

    /**
     * Returns the configs directory (~/.botwithus/configs), creating it if missing.
     * If the home-based path is not writable, falls back to ./configs.
     */
    @JvmStatic
    fun configsDir(): File {
        val homeDir = File(System.getProperty("user.home"), ".botwithus/configs").absoluteFile
        if (ensureWritable(homeDir)) {
            log.debug("Using configs directory {} (user.home)", homeDir.absolutePath)
            return homeDir
        }
        val cwdDir = File("./configs").absoluteFile
        if (ensureWritable(cwdDir)) {
            log.warn("Falling back to configs directory {} (cwd)", cwdDir.absolutePath)
            return cwdDir
        }
        log.warn("No writable configs directory found; returning {} (writes may fail)", cwdDir.absolutePath)
        return cwdDir
    }

    /**
     * Writes the given [content] (e.g., JSON) to ~/.botwithus/configs/[fileName].
     * Creates the configs directory if needed. Returns the written file.
     */
    @JvmStatic
    fun writeSettings(fileName: String, content: String): File {
        val dir = configsDir()
        val file = File(dir, fileName)
        file.parentFile?.let { if (!it.exists()) it.mkdirs() }
        try {
            log.debug("Writing settings file '{}' (chars={})", file.name, content.length)
            FileWriter(file, false).use { it.write(content) }
            log.info("Wrote settings to {}", file.absolutePath)
        } catch (e: IOException) {
            log.error("Failed writing settings to {}", file.absolutePath, e)
            throw e
        }
        return file
    }

    /** Convenience wrapper that writes to `<moduleName>.json` under configs. */
    @JvmStatic
    fun writeModuleSettings(moduleName: String, content: String): File {
        val file = "$moduleName.json"
        log.debug("writeModuleSettings(module='{}') -> '{}'", moduleName, file)
        return writeSettings(file, content)
    }

    /** Reads settings file content from ~/.botwithus/configs/[fileName], or returns null if missing/error. */
    @JvmStatic
    fun readSettings(fileName: String): String? {
        val dir = configsDir()
        val file = File(dir, fileName)
        return try {
            if (!file.exists()) {
                log.debug("readSettings: no file at {}", file.absolutePath)
                null
            } else {
                val text = file.readText()
                log.debug("readSettings: read {} bytes from {}", text.length, file.absolutePath)
                text
            }
        } catch (e: IOException) {
            log.error("Failed reading settings from {}", file.absolutePath, e)
            null
        }
    }

    /** Convenience wrapper that reads `<moduleName>.json` under configs. */
    @JvmStatic
    fun readModuleSettings(moduleName: String): String? {
        val file = "$moduleName.json"
        log.debug("readModuleSettings(module='{}') -> '{}'", moduleName, file)
        return readSettings(file)
    }
}
