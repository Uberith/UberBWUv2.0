# Script API Refactor Plan

## Goals

- Centralise persistence, phase management, runtime tracking, and woodcutting control inside `script-api`.
- Provide modular behaviour handlers (breaks, AFK jitter, logout guards, world hopping) that own their settings slices.
- Expose ImGui helper widgets to keep script UIs lightweight and consistent.
- Leave gameplay scripts focused on orchestration logic.

## New Building Blocks

| Module | Description | Primary API |
| --- | --- | --- |
| `PersistentSettingsScript<T>` | Settings-backed script base with `StateFlow` snapshots | `settingsState`, `updateSettings`, `persistSettings` |
| `RuntimeTracker` | Runtime + counter bookkeeping | `start()`, `mark()`, `snapshot()` |
| `PhaseLoop` | Enum-based phase state machine | `phase`, `transition()`, `on()` |
| `WoodcuttingSession` | High-level bank/chop orchestration | `prepare()`, `chop()`, `bankInventory()` |
| `WoodcuttingScript<T, P>` | High-level woodcutting script base with DSL loop | `ScriptLoop.phaseLoop { ... }`, `runtime` |
| `handlers/BreakScheduler` | Periodic AFK break executor | `update()`, `tick()` |
| `handlers/AfkJitter` | Short jitter idles | `update()`, `tick()` |
| `handlers/LogoutGuard` | Signals when runtime/goal thresholds met | `shouldLogout()` |
| `handlers/WorldHopPolicy` | Simple world-hop heuristics | `shouldHop()` |
| `ui/imgui/ImGuiWidgets` | Shared ImGui widgets | `toggle()`, `boundedInt()`, `tabs()` |
| `ui/imgui/TextureCache` | Lightweight keyed cache | `getOrPut()` |

## Migration Notes

1. Scripts extend `WoodcuttingScript<T, Phase>` and implement two DSL hooks:
   ```kotlin
   override suspend fun ScriptLoop.onStart() = woodcutting.prepare()
   override suspend fun ScriptLoop.onTick() = phaseLoop { ... }
   ```
2. Settings models reference handler settings (`BreakSettings`, `AfkSettings`, etc.) so they are persisted automatically.
3. GUIs observe `settingsSnapshot()` and mutate via `script.mutateSettings { ... }`, delegating widget layout to `ImGuiWidgets` helpers.
4. Handlers own their internal scheduling state; scripts simply call `handler.update(settings)` on changes and `handler.tick(this)` during loops.

## Outstanding Work

- Integrate world-hop logic with actual navigation APIs when available.
- Expand ImGui helpers with more complex layout primitives (tables, graphs).
- Port additional scripts to the new base to validate reuse and identify gaps.
