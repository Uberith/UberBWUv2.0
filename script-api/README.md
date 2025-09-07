Suspendable Script API

Overview
- Module: `script-api`
- Base class: `com.uberith.api.SuspendableScript`
- Purpose: Provide a coroutine-like, tick-accurate loop for BotWithUs RS3 scripts without adding external coroutine libraries.

Key API
- `abstract suspend fun onLoop()`: Put your script logic here. It is called repeatedly.
- `suspend fun awaitTicks(ticks: Int)`: Suspend until the specified number of server ticks elapse.
- `suspend fun awaitUntil(timeout: Int = 5, condition: () -> Boolean)`: Poll `condition` each tick until it returns true or the timeout (in ticks) is reached.

How it works
- Internally creates a continuation from a suspend lambda and advances it on the client tick.
- Uses `net.botwithus.rs3.client.Client.getServerTick()` to schedule resumption.
- You never call `run()` directly; the host calls `run()` each client tick. The base handles resuming your coroutine at the right time.

Usage
1) Extend `SuspendableScript` in your script class and implement `onLoop()`.

```kotlin
package com.uberith

import com.uberith.api.SuspendableScript
import net.botwithus.scripts.Info
import net.botwithus.ui.workspace.Workspace

@Info(name = "MyScript", description = "Demo", version = "1.0.0", author = "You")
class MyScript : SuspendableScript() {
    override fun onDraw(workspace: Workspace) {
        // optional drawing
    }

    override suspend fun onLoop() {
        // Do an action, then wait 1 tick
        // ... your logic ...
        awaitTicks(1)

        // Wait for a condition (up to 10 ticks)
        awaitUntil(timeout = 10) { /* check something */ false }
    }
}
```

2) Register the script via ServiceLoader metadata in your module:
- `src/main/resources/META-INF/services/net.botwithus.scripts.Script`
- Contents: `com.uberith.MyScript`

Tips
- Keep `onLoop()` fast; offload long waits via `awaitTicks/awaitUntil`.
- Avoid `Thread.sleep` inside scripts; use await methods to keep the host responsive.
- If you need to reset your loop (e.g., on deactivate), create a new instance of your script or manage any internal state you added.

Included template
- `com.uberith.SuspendableTemplate` demonstrates a minimal script using the base.

