package com.uberith.api.utils

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.slf4j.LoggerFactory
import java.lang.reflect.Type

/**
 * Generic JSON-backed store for script settings.
 */
class ConfigStore<T>(
    private val moduleName: String,
    private val type: Type,
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
) {

    private val logger = LoggerFactory.getLogger(ConfigStore::class.java)

    fun load(logOnFailure: Boolean = true): T? {
        return try {
            val json = ConfigFiles.readModuleSettings(moduleName)
            if (json.isNullOrBlank()) {
                logger.debug("[ConfigStore] No persisted data for module '{}'", moduleName)
                null
            } else {
                gson.fromJson<T>(json, type).also {
                    logger.info("[ConfigStore] Loaded settings for module '{}'", moduleName)
                }
            }
        } catch (t: Throwable) {
            if (logOnFailure) {
                logger.warn("[ConfigStore] Failed to load module '{}' settings: {}", moduleName, t.message)
            }
            null
        }
    }

    fun save(value: T, logOnFailure: Boolean = true): Boolean {
        return try {
            val json = gson.toJson(value, type)
            ConfigFiles.writeModuleSettings(moduleName, json)
            logger.info("[ConfigStore] Saved settings for module '{}'", moduleName)
            true
        } catch (t: Throwable) {
            if (logOnFailure) {
                logger.error("[ConfigStore] Failed to save module '{}' settings", moduleName, t)
            }
            false
        }
    }
}
