Config System Overview

- Keys and defaults live in `src/main/java/com/example/config/ConfigKeys.java`.
- The immutable model is `src/main/java/com/example/config/Config.java`.
- I/O and profiles are handled by `src/main/java/com/example/config/ConfigService.java`.
- Minimal UI is `src/main/java/com/example/ui/ConfigPanel.java`.
- Sample integration is `src/main/java/com/example/MyScript.java`.

How to add a new key

1) Define the key and default in `ConfigKeys`:
   - Add a new `public static final String` key name.
   - Add its default value to `DEFAULTS`.
2) Surface it in `Config`:
   - Add a final field with an appropriate type.
   - Update the constructor, `fromProperties(...)` to parse with defaults and validation, and `toProperties()` to write it back.
3) Update the UI (`ConfigPanel`) to expose the new field if needed.
4) Save once: defaults will persist automatically on next save via `ConfigService.save(...)`.

Notes

- Files are stored under the script workspace: `${workspace}/config/profiles/<profile>.properties`.
- Writes are atomic when supported: `*.tmp` then `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`.
- All errors are logged; the system falls back to safe defaults when files are missing or corrupt.
- Profiles are sanitized to `[a-zA-Z0-9-_]+`, with a default of `default`.

