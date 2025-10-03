# Repository Guidelines

## Project Structure & Module Organization
- `script-api/` hosts the shared Kotlin multiplatform bindings for BotWithUs; keep reusable abstractions here.
- Gameplay scripts live under `SkillingScripts/` (e.g. `SkillingScripts/UberChop/`), each with its own `src/main/java|kotlin` tree and Gradle subproject.
- Utility tooling sits in `InternalUtils/` (`UberTestingUtil`, `SuspendableDemo`) and reuses the API via Gradle project dependencies.
- Use `src/main/java/com/example` as a starter template for UI-heavy scripts rather than shipping it.

## Build, Test, and Development Commands
- `./gradlew build` — compiles every module, enforces the JDK 24 toolchain, and runs all registered unit tests.
- `./gradlew :script-api:build` — rebuilds only the shared API when making low-level protocol or model changes.
- `./gradlew :SkillingScripts:UberChop:build` — produces the UberChop jar and triggers `copyJar`, copying the artifact into `~/.BotWithUs/scripts` for local runtime pickup.
- `./gradlew clean` — wipe build outputs before switching branches when dependencies or generated sources change.
- Always invoke the Gradle wrapper from the project root; sync IDEs via the Gradle tool window to keep patched module paths aligned.

## Coding Style & Naming Conventions
- Kotlin sources use four-space indentation, `UpperCamelCase` types, and `lowerCamelCase` members; prefer expression bodies for small helpers.
- Annotate runnable scripts with `@Info` metadata and keep package names under `com.uberith.<module>` to match Gradle coordinates.
- Java samples follow the same indentation, avoid wildcard imports, and keep constants `UPPER_SNAKE_CASE`.
- Use IntelliJ’s default Kotlin/Java formatter and run `./gradlew build` before committing to catch toolchain regressions early.

## Testing Guidelines
- Unit and coroutine tests belong in `src/test/kotlin`, mirroring production packages and named `<Feature>Test.kt`.
- Prefer `kotlin.test` assertions and `app.cash.turbine` for flow verification (already on the classpath via `script-api`).
- Execute `./gradlew test` locally; add focused module invocations (e.g. `./gradlew :InternalUtils:UberTestingUtil:test`) when iterating.
- For integration scenarios that hit live game state, gate them behind explicit flags or `@Ignore` and document manual repro steps in the test file header.

## Commit & Pull Request Guidelines
- Replace the historical “Uberith Uberman Commit” placeholder with imperative, scoped summaries such as `script-api: add ground-item snapshot DTO`.
- Reference issue IDs or support tickets in the body (`BWU-123`) and list the commands you executed (`./gradlew :module:test`).
- PRs should outline the user-facing change, affected modules, test evidence, and include screenshots/GIFs for UI or in-game behaviour tweaks.
- Confirm BotWithUs dependency revisions and verify the staged jar under `~/.BotWithUs/scripts` before requesting review.
