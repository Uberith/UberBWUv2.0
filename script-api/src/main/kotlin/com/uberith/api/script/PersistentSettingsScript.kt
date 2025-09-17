package com.uberith.api.script

import com.uberith.api.script.SuspendableScript
import com.uberith.api.utils.ConfigStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Base script that wires a [ConfigStore] into a reactive settings holder.
 *
 * Scripts derive from this to obtain:
 * - Lazy loading/persisting of strongly typed settings.
 * - A hot [StateFlow] that the UI layer can observe.
 * - Convenience updaters for immutable copy semantics.
 */
abstract class PersistentSettingsScript<T : Any>(
    private val moduleName: String,
    private val settingsClass: Class<T>,
    private val defaultFactory: () -> T
) : SuspendableScript() {

    private val store: ConfigStore<T> by lazy { ConfigStore(moduleName, settingsClass) }
    private val _settings = MutableStateFlow(defaultFactory())
    private var loaded = false

    /** Latest settings snapshot. */
    val settingsState: StateFlow<T> = _settings.asStateFlow()

    /** Current settings value (immutable snapshot). */
    protected val settings: T
        get() = _settings.value

    /** Ensures settings are loaded once without logging load failures. */
    protected fun ensureSettingsLoaded(): T {
        if (!loaded) {
            loadSettings(logOnFailure = false)
        }
        return settings
    }

    /**
     * Loads settings from disk. Falls back to [defaultFactory] on failure.
     */
    protected fun loadSettings(logOnFailure: Boolean = true): T {
        val loadedSettings = store.load(logOnFailure) ?: defaultFactory()
        _settings.value = loadedSettings
        loaded = true
        onSettingsLoaded(loadedSettings)
        return loadedSettings
    }

    /**
     * Replaces the current settings snapshot. Optionally persists immediately.
     */
    protected fun replaceSettings(new: T, persist: Boolean = true) {
        _settings.value = new
        loaded = true
        if (persist) {
            store.save(new, logOnFailure = true)
        }
        onSettingsLoaded(new)
    }

    /**
     * Updates the current settings snapshot via immutable copy.
     */
    protected fun updateSettings(transform: (T) -> T, persist: Boolean = true): T {
        val updated = transform(settings)
        replaceSettings(updated, persist)
        return updated
    }

    /**
     * Persists the latest settings snapshot to disk.
     */
    protected fun persistSettings(logOnFailure: Boolean = true): Boolean {
        return store.save(settings, logOnFailure = logOnFailure)
    }

    /** Hook for subclasses to react whenever a new snapshot becomes current. */
    protected open fun onSettingsLoaded(snapshot: T) {}
}
