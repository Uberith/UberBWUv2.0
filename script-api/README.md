# UberChop :script-api — Persistent Settings Library

Reusable, thread-safe, and testable settings library for Kotlin apps. Uses Jetpack DataStore (Preferences) for persistence on JVM/Android and exposes a small, ergonomic API for getting/setting values, observing changes, JSON import/export, and versioned schema migrations.

## Features

- Strongly-typed `UberChopSettings` model with defaults
- Flow-based observation (`Flow<UberChopSettings>` and projections)
- Atomic updates via DataStore `edit`
- JSON import/export with kotlinx-serialization (pretty by default)
- Pluggable `ValueEncryptor` for sensitive fields (apiKey)
- Versioned migrations (example v1 → v2 adds `logLevel`)
- Works on Android and Desktop/JVM; in-memory fallback for tests

## Installation

settings.gradle.kts

```
include(":script-api")
```

app/build.gradle.kts

```
dependencies {
    implementation(project(":script-api"))
}
```

Module plugin/deps are declared inside `:script-api/build.gradle.kts` using Kotlin Multiplatform (`android` + `desktop` JVM target).

## Quick Start

Android

```kotlin
import com.uberchop.scriptapi.settings.*

val store: SettingsStore = provideSettingsStore(context)

// read
val snapshot = store.get()

// observe
store.settings.collect { println(it) }

// update single key
store.set(Selectors.ServerUrl, "https://api.example.com")

// JSON export/import
val json = store.exportJson()
store.importJson(json, ImportStrategy.Merge)

// reset
store.resetToDefaults()
```

Desktop/JVM

```kotlin
import com.uberchop.scriptapi.settings.*
import java.nio.file.Paths

val store = provideSettingsStore(Paths.get("./uberchop_settings.preferences_pb"))
```

In-memory (tests)

```kotlin
val store = SettingsModule.provideInMemorySettingsStore()
```

## Public API

- `interface SettingsStore`
  - `val settings: Flow<UberChopSettings>`
  - `suspend fun get(): UberChopSettings`
  - `suspend fun update(transform: (UberChopSettings) -> UberChopSettings)`
  - `suspend fun <T> set(key: SettingSelector<T>, value: T)`
  - `suspend fun resetToDefaults()`
  - `suspend fun exportJson(pretty: Boolean = true): String`
  - `suspend fun importJson(json: String, strategy: ImportStrategy = ImportStrategy.Merge)`
  - `fun <T> observe(selector: (UberChopSettings) -> T): Flow<T>`

- `object Selectors` — type-safe keys:
  - `ServerUrl: SettingSelector<String>`
  - `ApiKey: SettingSelector<String>`
  - `Theme: SettingSelector<ThemeMode>`
  - `AutoStart: SettingSelector<Boolean>`
  - `PollIntervalMs: SettingSelector<Long>`
  - `TelemetryEnabled: SettingSelector<Boolean>`
  - `LogLevel: SettingSelector<String>`

- DI helpers
  - Android: `fun provideSettingsStore(context: Context, encryptor: ValueEncryptor? = null): SettingsStore`
  - Desktop: `fun provideSettingsStore(filePath: java.nio.file.Path, encryptor: ValueEncryptor? = null): SettingsStore`
  - Tests/Common: `fun SettingsModule.provideInMemorySettingsStore(...)`

## JSON Schema

`UberChopSettings` is exported with these fields (defaults shown):

```json
{
  "serverUrl": "",
  "apiKey": "",
  "theme": "SYSTEM", // one of SYSTEM|LIGHT|DARK
  "autoStart": false,
  "pollIntervalMs": 60000,
  "telemetryEnabled": true,
  "logLevel": "INFO", // added in v2
  "schemaVersion": 2
}
```

Import strategies:

- `Merge` — applies only fields present in the JSON to current settings.
- `Overwrite` — resets to defaults, then applies JSON.

Unknown JSON fields are ignored. Values are validated (e.g. negative `pollIntervalMs` coerced to 0).

## Migrations

The library writes and reads `schemaVersion` in preferences and performs migrations before writes.

Example: v1 → v2

- Adds `logLevel` with default `INFO`.

Implementation lives in `SettingsMigrations.migrateIfNeeded(current, prefs)`. Add new steps when bumping versions.

## Encryption Hook

Sensitive fields such as `apiKey` can be encrypted at rest.

- Provide a `ValueEncryptor` with `encrypt(raw: String)` / `decrypt(enc: String)`.
- Encryption is applied only to `apiKey` in preferences.
- Default is `NoopEncryptor` (pass-through).

Example:

```kotlin
class MyEncryptor(...) : ValueEncryptor { /* ... */ }
val store = provideSettingsStore(context, MyEncryptor())
```

## Testing

Use `InMemorySettingsStore` for fast, deterministic tests (no IO). Turbine is included to test `Flow` emissions.

## Notes

- All IO is executed on `Dispatchers.IO`.
- All DataStore mutations are atomic via `edit`.
- Flows use `distinctUntilChanged` to avoid redundant emissions.
