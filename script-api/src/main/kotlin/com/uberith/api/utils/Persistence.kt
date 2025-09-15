package com.uberith.api.utils

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.lang.reflect.Type

/**
 * Provides utility methods for persisting and loading data objects as JSON files.
 *
 * @param <T> The type of data to be persisted and loaded.
 */
class Persistence<T>(private val fileName: String, private val type: Type) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    // New default config dir under ~/.BotWithUs/scripts/configs
    private val newConfigDir = File(System.getProperty("user.home"), ".BotWithUs/scripts/configs")
    // Legacy path (without leading dot) for backward compatibility when loading
    private val legacyConfigDir = File(System.getProperty("user.home"), "BotWithUs/scripts/configs")
    private val file: File

    /**
     * Constructs a new Persistence for the given file name and data type.
     *
     * @param fileName The name of the JSON file to save/load data.
     * @param type     The type of the data object, including generic parameters.
     */
    init {
        if (!newConfigDir.exists()) {
            newConfigDir.mkdirs()
        }

        this.file = File(newConfigDir, fileName)
    }

    /**
     * Saves the given data object to a JSON file.
     *
     * @param data The data object to save.
     */
    fun saveData(data: T) {
        try {
            FileWriter(file).use { writer ->
                gson.toJson(data, writer)
            }
        } catch (e: IOException) {
            // Handle save failure gracefully or rethrow as needed.
            e.printStackTrace()
        }
    }

    /**
     * Loads the data object from a JSON file.
     *
     * @return The loaded data object, or null if loading failed.
     */
    fun loadData(): T? {
        // Preferred location
        if (file.exists()) {
            try {
                FileReader(file).use { reader ->
                    return gson.fromJson(reader, type)
                }
            } catch (e: Exception) {
                // If deserialization fails (schema change, corrupt file, etc.), back up and return null.
                e.printStackTrace()
                try {
                    val backup = File(file.parentFile, file.nameWithoutExtension + ".corrupt-" + System.currentTimeMillis() + ".json")
                    file.copyTo(backup, overwrite = true)
                } catch (_: IOException) { /* ignore backup errors */ }
                return null
            }
        }

        // Fallback: attempt to load from legacy location and then migrate on next save
        val legacyFile = File(legacyConfigDir, file.name)
        if (legacyFile.exists()) {
            try {
                FileReader(legacyFile).use { reader ->
                    return gson.fromJson(reader, type)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Do not back up legacy file here; leave as-is
            }
        }

        try {
            FileReader(file).use { reader ->
                return gson.fromJson(reader, type)
            }
        } catch (e: Exception) {
            // If deserialization fails (schema change, corrupt file, etc.), back up and return null.
            e.printStackTrace()
            try {
                val backup = File(file.parentFile, file.nameWithoutExtension + ".corrupt-" + System.currentTimeMillis() + ".json")
                file.copyTo(backup, overwrite = true)
            } catch (_: IOException) { /* ignore backup errors */ }
        }
        return null
    }
}
