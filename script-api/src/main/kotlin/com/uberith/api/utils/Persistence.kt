package com.uberith.api.utils

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.uberith.api.BWUInit
import net.botwithus.ui.WorkspaceManager
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.reflect.Type

/**
 * Properties-backed persistence adapter that stores JSON blobs inside
 * the current Workspace's Properties under ~/.botwithus/workspaces.
 *
 * This preserves the existing Persistence<T> API while removing direct
 * JSON file I/O. Keys are derived from [fileName] without extension.
 */
class Persistence<T>(
    private val fileName: String,
    private val type: Type,
    @Suppress("unused") private val baseDir: File = File(".botwithus/configs")
) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val log = LoggerFactory.getLogger(Persistence::class.java)

    // Force initialize Workspace/Module loading once in this JVM
    @Suppress("unused")
    private val __init = BWUInit

    private val key: String = run {
        val name = fileName.substringBeforeLast('.', fileName)
        "configs.$name"
    }

    /** Returns the underlying properties file path for the current workspace. */
    val location: File
        get() {
            val home = System.getProperty("user.home")
            val ws = WorkspaceManager.getManager().getCurrent()
            return File(File(File(home, ".botwithus"), "workspaces"), ws.uuid + ".properties")
        }

    fun saveData(data: T) {
        try {
            val ws = WorkspaceManager.getManager().getCurrent()
            val props = ws.properties
            props.setProperty(key, gson.toJson(data, type))
            // Persist workspace to disk
            ws.save()
            WorkspaceManager.save(ws)
            log.info("saveData: saved key '{}' into workspace '{}'", key, ws.uuid)
        } catch (e: Exception) {
            log.error("saveData: failed for key '{}'", key, e)
        }
    }

    fun loadData(): T? {
        return try {
            val ws = WorkspaceManager.getManager().getCurrent()
            val json = ws.properties.getProperty(key) ?: return null
            gson.fromJson(json, type)
        } catch (e: Exception) {
            log.error("loadData: failed for key '{}'", key, e)
            null
        }
    }

    fun ensureBaseDir(): Boolean = true
    fun ensureParentDir(): Boolean = true

    fun ensureFile(createEmptyJson: Boolean = false, defaultProvider: (() -> T)? = null): Boolean {
        val existing = loadData()
        if (existing != null) return true
        return try {
            when {
                defaultProvider != null -> { saveData(defaultProvider()); true }
                createEmptyJson -> { saveData(gson.fromJson("{}", type)); true }
                else -> true
            }
        } catch (_: Exception) { false }
    }

    fun saveDataAtomic(data: T): Boolean {
        return try { saveData(data); true } catch (_: Exception) { false }
    }

    fun loadOrCreate(defaultProvider: () -> T): T {
        return loadData() ?: defaultProvider().also { saveData(it) }
    }

    fun loadOrDefault(defaultValue: T): T {
        return loadData() ?: defaultValue
    }

    fun touch(): Boolean = true
    fun exists(): Boolean = loadData() != null
    fun delete(): Boolean {
        return try {
            val ws = WorkspaceManager.getManager().getCurrent()
            val removed = ws.properties.remove(key) != null
            if (removed) {
                ws.save(); WorkspaceManager.save(ws)
            }
            removed
        } catch (_: Exception) { false }
    }

    fun appendLine(text: String, addNewline: Boolean = true): Boolean {
        // For logs, append to ~/.botwithus/logs/<fileName>
        return try {
            val logsDir = File(System.getProperty("user.home"), ".botwithus/logs")
            if (!logsDir.exists()) logsDir.mkdirs()
            val logFile = File(logsDir, fileName)
            logFile.parentFile?.mkdirs()
            logFile.appendText(if (addNewline) "$text\n" else text)
            true
        } catch (_: Exception) { false }
    }

    companion object {
        fun forLog(scriptName: String): Persistence<String> {
            return Persistence("$scriptName.log", String::class.java)
        }
    }
}
